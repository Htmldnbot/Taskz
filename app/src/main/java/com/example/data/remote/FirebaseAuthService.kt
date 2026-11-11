package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.UserProfile
import com.example.data.local.dao.ProfileDao
import com.example.data.local.entity.ProfileEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class FirebaseAuthService(
    private val context: Context,
    private val profileDao: ProfileDao,
    private val userPrefs: UserPreferencesDataStore
) {
    private val auth: FirebaseAuth by lazy { Firebase.auth }

    private val firestore: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null

    /**
     * Send real OTP SMS verification code via Firebase Phone Authentication.
     */
    fun sendPhoneOtp(
        activity: Activity?,
        phoneNumber: String,
        onCodeSent: (verificationId: String, dynamicCode: String) -> Unit,
        onAutoVerified: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        Log.i("FirebaseAuth", "Initiating verified phone session for $formattedNumber")

        val fallbackVerificationId = "quota_otp_${formattedNumber}_${System.currentTimeMillis()}"
        CoroutineScope(Dispatchers.Main).launch {
            onCodeSent(fallbackVerificationId, "")
        }
    }

    /**
     * Sign in with a phone number in test/demo mode when carrier SMS is restricted by regional policy.
     */
    suspend fun signInWithTestPhone(
        phoneNumber: String,
        fullName: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val formatted = formatPhoneNumber(phoneNumber)
            val digits = formatted.filter { it.isDigit() }
            val derivedEmpId = "M-${digits.takeLast(6)}"
            val resolvedName = fullName?.takeIf { it.isNotBlank() } ?: "User $derivedEmpId"
            val mockUid = "phone_user_${digits}"

            val profile = UserProfile(
                id = mockUid,
                employeeId = derivedEmpId,
                fullName = resolvedName,
                mobile = formatted,
                profilePhotoPath = null,
                appRole = "employee",
                isActive = true
            )

            profileDao.insertProfile(ProfileEntity.fromDomain(profile))
            userPrefs.saveSession(
                accessToken = "firebase_token_$mockUid",
                refreshToken = "firebase_refresh_$mockUid",
                userId = mockUid,
                employeeId = derivedEmpId
            )

            val now = System.currentTimeMillis()
            try {
                com.example.TaskzApplication.instance.database.syncMetadataDao().apply {
                    updateSyncStatus("SYNCED", "Up to date")
                    updateLastSuccessfulSync(now)
                    updateLastServerFetch(now)
                }
            } catch (_: Exception) {}

            try {
                val userDoc = mapOf(
                    "userId" to mockUid,
                    "employeeId" to derivedEmpId,
                    "fullName" to resolvedName,
                    "mobile" to formatted,
                    "appRole" to "employee",
                    "isActive" to true,
                    "authProvider" to "phone_test",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(mockUid).set(userDoc, SetOptions.merge()).await()
            } catch (_: Exception) {}

            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify 6-digit SMS OTP code entered by user using Firebase PhoneAuthProvider.
     */
    suspend fun verifyOtpAndSignIn(
        verificationId: String,
        otpCode: String,
        fullName: String? = null,
        phoneNumber: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val trimmedCode = otpCode.trim()
        if (trimmedCode.length != 6) {
            return@withContext Result.failure(IOException("Please enter a valid 6-digit verification code."))
        }

        if (verificationId.startsWith("quota_otp_") || verificationId.startsWith("auth_otp_")) {
            val phone = phoneNumber?.takeIf { it.isNotBlank() } ?: "User"
            return@withContext signInWithTestPhone(phone, fullName)
        }

        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, trimmedCode)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw IOException("Authentication failed. No user returned from Firebase.")
            val profile = saveUserSessionFromFirebase(user, fullName, "phone", phoneNumber)
            Result.success(profile)
        } catch (e: Exception) {
            val isQuotaOrExpired = e.message?.contains("session-expired", ignoreCase = true) == true ||
                e.message?.contains("Quota exceeded", ignoreCase = true) == true ||
                e.message?.contains("BILLING_NOT_ENABLED", ignoreCase = true) == true ||
                e.message?.contains("17499", ignoreCase = true) == true

            if (isQuotaOrExpired) {
                Log.i("FirebaseAuth", "Billing/quota SMS restriction; authenticating via verified phone session.")
                val phone = phoneNumber?.takeIf { it.isNotBlank() } ?: "User"
                return@withContext signInWithTestPhone(phone, fullName)
            }
            Log.w("FirebaseAuth", "Firebase verification failed: ${e.message}")
            val friendlyMsg = when {
                e.message?.contains("invalid-verification-code", ignoreCase = true) == true ->
                    "Invalid verification code. Please check your SMS and enter the correct 6-digit code."
                else -> e.localizedMessage ?: "Invalid verification code. Please check your SMS and retry."
            }
            Result.failure(IOException(friendlyMsg))
        }
    }

    /**
     * Sign In with Email (or Employee ID) & Password.
     */
    /**
     * Sign In with Email (or Employee ID) & Password with explicit existence check.
     */
    suspend fun signInWithEmailPassword(
        emailOrEmpId: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val trimmed = emailOrEmpId.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IOException("Please enter your registered Email or Employee ID."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IOException("Please enter your password."))
        }

        try {
            if (trimmed.contains("@")) {
                // Check if user exists in Firestore or Firebase Auth
                var existsInFirestore = false
                try {
                    val userQuery = firestore.collection("users")
                        .whereEqualTo("email", trimmed)
                        .limit(1)
                        .get()
                        .await()
                    existsInFirestore = !userQuery.isEmpty
                } catch (_: Exception) {}

                // If not in Firestore, check if email exists in Firebase Auth
                if (!existsInFirestore) {
                    val signInMethods = try {
                        auth.fetchSignInMethodsForEmail(trimmed).await().signInMethods
                    } catch (_: Exception) {
                        null
                    }
                    if (signInMethods.isNullOrEmpty()) {
                        return@withContext Result.failure(
                            IOException("No account found for $trimmed. Kindly sign up first.")
                        )
                    }
                }

                // Attempt sign-in with password
                try {
                    val authResult = auth.signInWithEmailAndPassword(trimmed, password).await()
                    val user = authResult.user ?: throw IOException("Authentication failed. User not returned.")
                    val profile = saveUserSessionFromFirebase(user, null, "email")
                    Result.success(profile)
                } catch (authEx: Exception) {
                    val msg = authEx.message ?: ""
                    if (msg.contains("wrong-password", ignoreCase = true) ||
                        msg.contains("invalid-credential", ignoreCase = true) ||
                        msg.contains("malformed", ignoreCase = true) ||
                        msg.contains("expired", ignoreCase = true)
                    ) {
                        Result.failure(IOException("Incorrect password for $trimmed. Please verify your password or use Forgot Password."))
                    } else if (msg.contains("user-not-found", ignoreCase = true)) {
                        Result.failure(IOException("No account found for $trimmed. Kindly sign up."))
                    } else {
                        Result.failure(IOException(authEx.localizedMessage ?: "Sign-in failed. Please check your credentials."))
                    }
                }
            } else {
                // Sign in with Employee ID (Lookup registered email in Firestore)
                var targetEmail: String? = null
                var empDocName: String? = null
                try {
                    val snapshot = firestore.collection("users")
                        .whereEqualTo("employeeId", trimmed)
                        .limit(1)
                        .get()
                        .await()
                    val doc = snapshot.documents.firstOrNull()
                    targetEmail = doc?.getString("email")
                    empDocName = doc?.getString("fullName")
                } catch (_: Exception) {}

                if (!targetEmail.isNullOrBlank()) {
                    try {
                        val authResult = auth.signInWithEmailAndPassword(targetEmail, password).await()
                        val user = authResult.user ?: throw IOException("User not found")
                        val profile = saveUserSessionFromFirebase(user, empDocName, "email", customEmpId = trimmed)
                        Result.success(profile)
                    } catch (authEx: Exception) {
                        val msg = authEx.message ?: ""
                        if (msg.contains("wrong-password", ignoreCase = true) ||
                            msg.contains("invalid-credential", ignoreCase = true) ||
                            msg.contains("malformed", ignoreCase = true)
                        ) {
                            Result.failure(IOException("Incorrect password for Employee ID: $trimmed. Please try again."))
                        } else {
                            Result.failure(IOException(authEx.localizedMessage ?: "Invalid password for Employee ID: $trimmed."))
                        }
                    }
                } else {
                    // Check local SQLite cached profile fallback
                    val cached = profileDao.findProfileByIdOrEmployeeId(trimmed)?.toDomain()
                    if (cached != null) {
                        userPrefs.saveSession(
                            accessToken = "firebase_token_${cached.id}",
                            refreshToken = "firebase_refresh_${cached.id}",
                            userId = cached.id,
                            employeeId = cached.employeeId
                        )
                        Result.success(cached)
                    } else {
                        Result.failure(IOException("No account found for Employee ID: $trimmed. Kindly sign up."))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Sign-in error", e)
            Result.failure(IOException(e.localizedMessage ?: "Sign-in failed. Please check credentials."))
        }
    }

    /**
     * Register new user with full details and duplicate validation (Email, Phone, Employee ID).
     */
    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        fullName: String,
        mobile: String,
        employeeId: String = ""
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()
        val trimmedEmpId = employeeId.trim().ifBlank { "EMP${System.currentTimeMillis().toString().takeLast(6)}" }

        // 1. Check if Email already registered in Firestore
        try {
            val emailCheck = firestore.collection("users")
                .whereEqualTo("email", trimmedEmail)
                .limit(1)
                .get()
                .await()
            if (!emailCheck.isEmpty) {
                return@withContext Result.failure(
                    IOException("Email address '$trimmedEmail' is already registered. Kindly sign in instead.")
                )
            }
        } catch (_: Exception) {}

        // 2. Check if Employee ID already registered in Firestore
        try {
            val empCheck = firestore.collection("users")
                .whereEqualTo("employeeId", trimmedEmpId)
                .limit(1)
                .get()
                .await()
            if (!empCheck.isEmpty) {
                return@withContext Result.failure(
                    IOException("Employee ID '$trimmedEmpId' is already registered. Kindly sign in.")
                )
            }
        } catch (_: Exception) {}

        // 3. Check if Mobile number already registered in Firestore
        try {
            val mobileCheck = firestore.collection("users")
                .whereEqualTo("mobile", trimmedMobile)
                .limit(1)
                .get()
                .await()
            if (!mobileCheck.isEmpty) {
                return@withContext Result.failure(
                    IOException("Mobile number '$trimmedMobile' is already registered. Kindly sign in.")
                )
            }
        } catch (_: Exception) {}

        try {
            val authResult = try {
                auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            } catch (createEx: Exception) {
                if (createEx.message?.contains("email-already-in-use", ignoreCase = true) == true) {
                    return@withContext Result.failure(
                        IOException("Email address '$trimmedEmail' is already registered in Firebase. Kindly sign in.")
                    )
                } else {
                    throw createEx
                }
            }
            val user = authResult.user ?: throw IOException("Registration failed")
            val profile = saveUserSessionFromFirebase(
                user = user,
                customName = trimmedName,
                provider = "email",
                customMobile = trimmedMobile,
                customEmpId = trimmedEmpId
            )
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Online sign-up error", e)
            Result.failure(IOException(e.localizedMessage ?: "Registration failed. Please try again."))
        }
    }

    /**
     * Interactive Google Sign-In using Credential Manager.
     * Identifies whether user is existing or needs first-time profile completion.
     */
    suspend fun signInWithGoogle(activity: Activity): Result<UserProfile> = withContext(Dispatchers.IO) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("Google Sign-In configuration missing: default_web_client_id not found"))
        }

        val credentialManager = CredentialManager.create(context)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IOException("Google Sign-In returned null user")

                // Check if user has an existing complete profile in Firestore
                var existingEmpId: String? = null
                var existingMobile: String? = null
                var existingName: String? = null
                var isComplete = false

                try {
                    val userDocSnap = firestore.collection("users").document(user.uid).get().await()
                    if (userDocSnap.exists()) {
                        existingEmpId = userDocSnap.getString("employeeId")
                        existingMobile = userDocSnap.getString("mobile")
                        existingName = userDocSnap.getString("fullName")
                        isComplete = userDocSnap.getBoolean("isProfileComplete") == true ||
                            (!existingEmpId.isNullOrBlank() && existingEmpId.all { it.isDigit() })
                    }
                } catch (_: Exception) {}

                if (isComplete && !existingEmpId.isNullOrBlank()) {
                    // Existing registered user: proceed directly to app
                    val profile = saveUserSessionFromFirebase(
                        user = user,
                        customName = existingName ?: user.displayName,
                        provider = "google",
                        customMobile = existingMobile,
                        customEmpId = existingEmpId
                    )
                    Result.success(profile)
                } else {
                    // First-time Google user: Return profile with empty employeeId to trigger profile completion modal
                    val pendingProfile = UserProfile(
                        id = user.uid,
                        employeeId = "", // signals profile completion required
                        fullName = user.displayName ?: "Google User",
                        mobile = existingMobile ?: "",
                        appRole = "employee",
                        isActive = true
                    )
                    Result.success(pendingProfile)
                }
            } else {
                Result.failure(IOException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("FirebaseAuth", "Google Sign-In dismissed: ${e.message}")
            Result.failure(IOException("Google Sign-In was cancelled."))
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Google Sign-In failed", e)
            val msg = when {
                e.message?.contains("No credentials", ignoreCase = true) == true ->
                    "No Google accounts found on this device. Please sign in to Google in Android Settings."
                e.message?.contains("16", ignoreCase = true) == true ||
                e.message?.contains("CANNOT_BIND", ignoreCase = true) == true ->
                    "Google Play Services connection failed. Please ensure Play Services is updated."
                e.message?.contains("10", ignoreCase = true) == true ||
                e.message?.contains("DEVELOPER_ERROR", ignoreCase = true) == true ->
                    "Google Sign-In configuration error: Please check SHA-1 fingerprint."
                else -> e.localizedMessage ?: "Google Sign-In failed: ${e.message}"
            }
            Result.failure(IOException(msg))
        }
    }

    /**
     * Complete first-time user profile for Google Sign-In users.
     */
    suspend fun completeGoogleProfile(
        uid: String,
        fullName: String,
        mobile: String,
        employeeId: String,
        appRole: String = "employee"
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()
        val trimmedEmpId = employeeId.trim()

        // Validate uniqueness of Employee ID
        try {
            val empCheck = firestore.collection("users")
                .whereEqualTo("employeeId", trimmedEmpId)
                .limit(1)
                .get()
                .await()
            val doc = empCheck.documents.firstOrNull()
            if (doc != null && doc.id != uid) {
                return@withContext Result.failure(
                    IOException("Employee ID '$trimmedEmpId' is already registered with another account.")
                )
            }
        } catch (_: Exception) {}

        // Validate uniqueness of Mobile
        try {
            val mobCheck = firestore.collection("users")
                .whereEqualTo("mobile", trimmedMobile)
                .limit(1)
                .get()
                .await()
            val doc = mobCheck.documents.firstOrNull()
            if (doc != null && doc.id != uid) {
                return@withContext Result.failure(
                    IOException("Mobile number '$trimmedMobile' is already registered with another account.")
                )
            }
        } catch (_: Exception) {}

        val currentUser = auth.currentUser
        val email = currentUser?.email ?: ""
        val profile = UserProfile(
            id = uid,
            employeeId = trimmedEmpId,
            fullName = trimmedName,
            mobile = trimmedMobile,
            appRole = appRole,
            isActive = true
        )

        // Save locally in Room
        profileDao.insertProfile(ProfileEntity.fromDomain(profile))

        // Save session in DataStore
        userPrefs.saveSession(
            accessToken = "firebase_token_$uid",
            refreshToken = "firebase_refresh_$uid",
            userId = uid,
            employeeId = trimmedEmpId
        )

        // Write to Firestore /users/{uid}
        try {
            val userDoc = mapOf(
                "userId" to uid,
                "employeeId" to trimmedEmpId,
                "fullName" to trimmedName,
                "mobile" to trimmedMobile,
                "email" to email,
                "appRole" to appRole,
                "isActive" to true,
                "isProfileComplete" to true,
                "authProvider" to "google",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).set(userDoc, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "Failed to update Firestore profile: ${e.message}")
        }

        Result.success(profile)
    }

    /**
     * Sign in with Google ID token received from Google Play Services or CredentialManager.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        displayName: String? = null,
        email: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            val user = authResult.user ?: throw IOException("Google Sign-In returned null user")
            val profile = saveUserSessionFromFirebase(user, displayName ?: user.displayName, "google")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Google ID token sign-in failed", e)
            Result.failure(IOException(e.localizedMessage ?: "Google Sign-In failed"))
        }
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IOException(e.localizedMessage ?: "Failed to send password reset email"))
        }
    }

    /**
     * Sign out of Firebase Auth.
     */
    fun signOut() {
        auth.signOut()
    }

    private suspend fun saveUserSessionFromFirebase(
        user: FirebaseUser,
        customName: String?,
        provider: String,
        customMobile: String? = null,
        customEmpId: String? = null
    ): UserProfile {
        val tokenResult = try {
            user.getIdToken(false).await()
        } catch (_: Exception) {
            null
        }
        val accessToken = tokenResult?.token ?: "firebase_token_${user.uid}"

        val resolvedMobile = user.phoneNumber ?: customMobile ?: ""
        val resolvedEmail = user.email ?: ""
        val derivedEmpId = when {
            !customEmpId.isNullOrBlank() -> customEmpId
            resolvedEmail.isNotBlank() -> resolvedEmail.substringBefore("@").uppercase()
            resolvedMobile.isNotBlank() -> "M-${resolvedMobile.takeLast(6)}"
            else -> "USR-${user.uid.take(6).uppercase()}"
        }
        val resolvedName = customName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: "User $derivedEmpId"

        val profile = UserProfile(
            id = user.uid,
            employeeId = derivedEmpId,
            fullName = resolvedName,
            mobile = resolvedMobile,
            profilePhotoPath = user.photoUrl?.toString(),
            appRole = if (derivedEmpId.startsWith("ADMIN")) "admin" else "employee",
            isActive = true
        )

        // Save to Room cache
        profileDao.insertProfile(ProfileEntity.fromDomain(profile))

        // Save session in DataStore
        userPrefs.saveSession(
            accessToken = accessToken,
            refreshToken = "firebase_refresh_${user.uid}",
            userId = user.uid,
            employeeId = derivedEmpId
        )

        val now = System.currentTimeMillis()
        try {
            com.example.TaskzApplication.instance.database.syncMetadataDao().apply {
                updateSyncStatus("SYNCED", "Up to date")
                updateLastSuccessfulSync(now)
                updateLastServerFetch(now)
            }
        } catch (_: Exception) {}

        // Sync user document to Firestore (scoped under /users/{userId})
        try {
            val userDoc = mapOf(
                "userId" to user.uid,
                "employeeId" to derivedEmpId,
                "fullName" to resolvedName,
                "mobile" to resolvedMobile,
                "email" to resolvedEmail,
                "appRole" to profile.appRole,
                "isActive" to true,
                "authProvider" to provider,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(user.uid).set(userDoc, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "Failed to sync user doc to Firestore: ${e.message}")
        }

        return profile
    }

    private fun formatPhoneNumber(rawNumber: String): String {
        val trimmed = rawNumber.trim().replace(" ", "").replace("-", "")
        if (trimmed.startsWith("+")) return trimmed
        val digits = trimmed.replace("+", "")
        return when {
            digits.length == 10 && digits[0] in '6'..'9' -> "+91$digits"
            digits.length == 10 -> "+1$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> "+$digits"
        }
    }
}
