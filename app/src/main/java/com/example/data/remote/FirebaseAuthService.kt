package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import com.example.data.local.dao.ProfileDao
import com.example.data.local.entity.ProfileEntity
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

class FirebaseAuthService(
    private val context: Context,
    private val profileDao: ProfileDao,
    private val userPrefs: UserPreferencesDataStore
) {
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val supabaseClient get() = TaskzApplication.instance.supabaseClient
    private val authRepository get() = TaskzApplication.instance.authRepository

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Sign In with Email + Password (Firebase Auth verification + Supabase session retrieval).
     */
    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val trimmed = email.trim()
        val normalizedEmail = trimmed.lowercase()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            return@withContext Result.failure(IOException("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IOException("Please enter your password."))
        }

        val linkedEmployeeId = userPrefs.getEmployeeIdForEmail(normalizedEmail)
        val methodHint = userPrefs.getEmailAuthMethodHint(normalizedEmail)

        try {
            var firebaseException: Exception? = null
            val authResult = try {
                auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            } catch (ex: Exception) {
                firebaseException = ex
                null
            }

            if (authResult?.user != null) {
                val fbUser = authResult.user!!
                userPrefs.saveEmailAuthMethodHint(normalizedEmail, "email")

                val empIdFromProfile = extractEmployeeIdFromUri(fbUser.photoUrl)
                val resolvedEmpId = linkedEmployeeId ?: empIdFromProfile

                if (!resolvedEmpId.isNullOrBlank()) {
                    userPrefs.saveEmailEmployeeHint(normalizedEmail, resolvedEmpId)
                    val supaRes = authRepository.login(resolvedEmpId, password)
                    if (supaRes.isSuccess) {
                        return@withContext supaRes
                    }
                    val supaErr = supaRes.exceptionOrNull()
                    if (supaErr != null && authRepository.isNetworkFailure(supaErr)) {
                        return@withContext supaRes
                    }
                }

                // Fallback: try direct Supabase email login if email was registered directly in Supabase Auth
                val directSupa = supabaseClient.loginWithEmail(normalizedEmail, password)
                if (directSupa.isSuccess) {
                    val session = directSupa.getOrThrow()
                    return@withContext finalizeSupabaseProfileSession(
                        session = session,
                        fallbackEmpId = resolvedEmpId ?: normalizedEmail.substringBefore("@").uppercase(),
                        fallbackName = fbUser.displayName,
                        emailForHint = normalizedEmail,
                        authMethodHint = "email"
                    )
                }

                auth.signOut()
                return@withContext Result.failure(
                    IOException(
                        if (!resolvedEmpId.isNullOrBlank()) {
                            "Email verified, but password did not match Employee ID ($resolvedEmpId) on server. " +
                                "Please sign in via the 'Employee ID' tab with your original password."
                        } else {
                            "Email verified, but this device is not yet linked to your Employee ID. " +
                                "Please sign in once using the 'Employee ID' tab."
                        }
                    )
                )
            }

            // Firebase sign-in did not succeed: check Supabase directly before failing!
            // This handles accounts created directly in Supabase Auth or synced via Employee ID.
            val directSupa = supabaseClient.loginWithEmail(normalizedEmail, password)
            if (directSupa.isSuccess) {
                val session = directSupa.getOrThrow()
                return@withContext finalizeSupabaseProfileSession(
                    session = session,
                    fallbackEmpId = linkedEmployeeId ?: normalizedEmail.substringBefore("@").uppercase(),
                    fallbackName = null,
                    emailForHint = normalizedEmail,
                    authMethodHint = "email"
                )
            }

            if (!linkedEmployeeId.isNullOrBlank()) {
                val empSupaRes = authRepository.login(linkedEmployeeId, password)
                if (empSupaRes.isSuccess) {
                    userPrefs.saveEmailAuthMethodHint(normalizedEmail, "email")
                    return@withContext empSupaRes
                }
            }

            val ex = firebaseException
            if (ex is FirebaseNetworkException || (ex != null && authRepository.isNetworkFailure(ex))) {
                return@withContext Result.failure(
                    IOException("Unable to reach authentication server. Please check your internet connection.")
                )
            }

            if (methodHint == "google") {
                return@withContext Result.failure(
                    IOException("This email ($trimmed) is registered with Google Sign-In. Please tap 'Sign in with Google' below.")
                )
            }

            val errorMsg = when (ex) {
                is FirebaseAuthInvalidUserException ->
                    "No email/password account found for $trimmed. Kindly Create Account, or tap 'Sign in with Google' if you registered with Google."
                is FirebaseAuthInvalidCredentialsException -> {
                    when (ex.errorCode) {
                        "ERROR_USER_NOT_FOUND" ->
                            "No email/password account found for $trimmed. Kindly Create Account, or tap 'Sign in with Google' if you registered with Google."
                        "ERROR_WRONG_PASSWORD" ->
                            "Incorrect password for $trimmed. Please verify or tap 'Forgot Password?'."
                        "ERROR_INVALID_EMAIL" ->
                            "Please enter a valid email address."
                        else -> {
                            // NOTE: When Firebase Email Enumeration Protection is enabled (default since 2023),
                            // signInWithEmailAndPassword throws ERROR_INVALID_CREDENTIAL and fetchSignInMethodsForEmail
                            // returns an empty list even for existing accounts. Never infer 'No account found' from an empty list!
                            val methods = try {
                                auth.fetchSignInMethodsForEmail(normalizedEmail).await().signInMethods
                            } catch (_: Exception) {
                                null
                            }
                            when {
                                methods != null && methods.isNotEmpty() && !methods.contains("password") ->
                                    "This email ($trimmed) is registered with Google Sign-In. Please tap 'Sign in with Google' below."
                                methods != null && methods.contains("password") ->
                                    "Incorrect password for $trimmed. Please verify or tap 'Forgot Password?'."
                                !linkedEmployeeId.isNullOrBlank() ->
                                    "Incorrect password for $trimmed (Employee ID: $linkedEmployeeId). Verify your password, tap 'Forgot Password?', or sign in via the 'Employee ID' tab."
                                else ->
                                    "Invalid password or sign-in method for $trimmed. If you registered with Google, tap 'Sign in with Google'; otherwise check your password, try 'Employee ID', or Create Account."
                            }
                        }
                    }
                }
                else -> ex?.localizedMessage ?: "Sign-in failed. Please check your credentials."
            }
            Result.failure(IOException(errorMsg))
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Email sign-in error", e)
            Result.failure(IOException(e.localizedMessage ?: "Sign-in failed. Please check credentials."))
        }
    }

    /**
     * Create Firebase Email/Password account without altering Supabase session storage.
     * Stores full name & normalized Employee ID metadata on the Firebase user so email sign-in works across devices.
     */
    suspend fun createFirebaseEmailAccount(
        email: String,
        password: String,
        fullName: String = "",
        employeeId: String = ""
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        try {
            val res = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = res.user ?: return@withContext Result.failure(IOException("Firebase registration failed"))
            val normalizedEmp = if (employeeId.isNotBlank()) SupabaseConfig.normalizeEmployeeId(employeeId) else ""
            if (fullName.isNotBlank() || normalizedEmp.isNotBlank()) {
                runCatching {
                    val profileUpdates = userProfileChangeRequest {
                        if (fullName.isNotBlank()) {
                            displayName = fullName.trim()
                        }
                        if (normalizedEmp.isNotBlank()) {
                            photoUri = Uri.parse("https://taskz.app/emp/$normalizedEmp")
                        }
                    }
                    user.updateProfile(profileUpdates).await()
                }
            }
            Result.success(user)
        } catch (ex: FirebaseAuthUserCollisionException) {
            Result.failure(
                IOException("Email address '$trimmedEmail' is already registered. Kindly sign in (or tap 'Sign in with Google' if you used Google).")
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("email-already-in-use", ignoreCase = true)) {
                Result.failure(
                    IOException("Email address '$trimmedEmail' is already registered. Kindly sign in (or tap 'Sign in with Google' if you used Google).")
                )
            } else {
                Result.failure(IOException(e.localizedMessage ?: "Email registration failed."))
            }
        }
    }

    /**
     * Rollback helper if Supabase signup fails after creating a Firebase Email account.
     */
    suspend fun rollbackFirebaseEmailAccount(email: String) = withContext(Dispatchers.IO) {
        try {
            val current = auth.currentUser
            if (current != null && current.email.equals(email.trim(), ignoreCase = true)) {
                current.delete().await()
            }
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "Rollback Firebase email user warning: ${e.message}")
        }
    }

    /**
     * Interactive Google Sign-In using Credential Manager.
     * Exchanges Firebase Auth Google credential for an authoritative Supabase session.
     * Returns an incomplete UserProfile (employeeId = "") for first-time Google users so the UI collects profile details once.
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
                resolveGoogleUserInSupabase(user, user.displayName)
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
     * Sign in with Google ID token received from CredentialManager / Play Services.
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
            resolveGoogleUserInSupabase(user, displayName ?: user.displayName)
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Google ID token sign-in failed", e)
            Result.failure(IOException(e.localizedMessage ?: "Google Sign-In failed"))
        }
    }

    /**
     * Resolves whether the Firebase Google user already has a Supabase Taskz account + profile.
     * - Existing user: saves real Supabase JWT + UUID session and proceeds directly into the app.
     * - First-time Google user: returns pending UserProfile with empty employeeId to trigger profile completion.
     */
    private suspend fun resolveGoogleUserInSupabase(
        user: FirebaseUser,
        displayName: String?
    ): Result<UserProfile> {
        val bridgeEmail = SupabaseConfig.toGoogleAuthEmail(user.uid)
        val bridgeSecret = SupabaseConfig.toGoogleBridgeSecret(user.uid)

        val supaLogin = supabaseClient.loginWithEmail(bridgeEmail, bridgeSecret)
        if (supaLogin.isSuccess) {
            val session = supaLogin.getOrThrow()
            val profileRes = supabaseClient.fetchProfileById(session.accessToken, session.userId)
            val remoteProfile = profileRes.getOrNull()

            if (remoteProfile != null && remoteProfile.employeeId.isNotBlank()) {
                if (!remoteProfile.isActive) {
                    auth.signOut()
                    return Result.failure(
                        IOException("Your Taskz account is currently inactive. Please contact the administrator.")
                    )
                }
                return finalizeSupabaseProfileSession(
                    session = session,
                    fallbackEmpId = remoteProfile.employeeId,
                    fallbackName = displayName ?: remoteProfile.fullName,
                    emailForHint = user.email,
                    preloadedProfile = remoteProfile,
                    authMethodHint = "google"
                )
            }
        } else {
            val err = supaLogin.exceptionOrNull()
            if (err != null && authRepository.isNetworkFailure(err)) {
                return Result.failure(
                    IOException("Unable to reach Taskz server for Google Sign-In. Please check your internet connection.")
                )
            }
        }

        if (!user.email.isNullOrBlank()) {
            userPrefs.saveEmailAuthMethodHint(user.email!!, "google")
        }

        // First-time Google user: profile not registered on Supabase yet
        return Result.success(
            UserProfile(
                id = user.uid,
                employeeId = "", // Signals GoogleProfileCompletionDialog is required
                fullName = displayName?.takeIf { it.isNotBlank() } ?: "Google User",
                mobile = "",
                appRole = "employee",
                isActive = true
            )
        )
    }

    /**
     * Complete first-time user profile for Google Sign-In users.
     * Creates the authoritative Supabase Auth account and public.profiles row once.
     */
    suspend fun completeGoogleProfile(
        uid: String,
        fullName: String,
        mobile: String,
        employeeId: String,
        appRole: String = "employee"
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid != uid) {
            return@withContext Result.failure(
                IOException("Google Sign-In session expired. Please tap Continue with Google again.")
            )
        }

        val trimmedName = fullName.trim()
        val normalizedMobile = normalizeMobile(mobile)
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val bridgeEmail = SupabaseConfig.toGoogleAuthEmail(uid)
        val bridgeSecret = SupabaseConfig.toGoogleBridgeSecret(uid)

        // Check if Supabase bridge account already exists; if not, sign up once
        var sessionResult = supabaseClient.signUpWithEmail(
            email = bridgeEmail,
            password = bridgeSecret,
            employeeId = normalizedEmpId,
            fullName = trimmedName,
            mobile = normalizedMobile
        )

        if (sessionResult.isFailure) {
            val errMsg = sessionResult.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("already registered", ignoreCase = true) ||
                errMsg.contains("User already exists", ignoreCase = true)
            ) {
                sessionResult = supabaseClient.loginWithEmail(bridgeEmail, bridgeSecret)
            } else {
                return@withContext Result.failure(
                    IOException(errMsg.ifBlank { "Failed to create Google profile on server." })
                )
            }
        }

        var session = sessionResult.getOrElse {
            return@withContext Result.failure(it)
        }

        if (session.accessToken.isBlank()) {
            val loginRetry = supabaseClient.loginWithEmail(bridgeEmail, bridgeSecret)
            if (loginRetry.isSuccess) {
                session = loginRetry.getOrThrow()
            } else {
                return@withContext Result.failure(
                    IOException(
                        "SUPABASE_EMAIL_CONFIRMATION_REQUIRED: Account created on server, but email confirmation is enabled in Supabase."
                    )
                )
            }
        }

        // Confirm profile row in Supabase (created by handle_new_auth_user trigger)
        var remoteProfile = supabaseClient.fetchProfileById(session.accessToken, session.userId).getOrNull()
        if (remoteProfile == null) {
            delay(400)
            remoteProfile = supabaseClient.fetchProfileById(session.accessToken, session.userId).getOrNull()
        }

        val resolvedProfile = remoteProfile ?: UserProfile(
            id = session.userId,
            employeeId = normalizedEmpId,
            fullName = trimmedName,
            mobile = normalizedMobile,
            profilePhotoPath = null,
            appRole = "employee",
            isActive = true
        )

        if (remoteProfile == null) {
            supabaseClient.upsertProfile(session.accessToken, resolvedProfile)
        }

        finalizeSupabaseProfileSession(
            session = session,
            fallbackEmpId = resolvedProfile.employeeId.ifBlank { normalizedEmpId },
            fallbackName = trimmedName,
            emailForHint = currentUser.email,
            preloadedProfile = resolvedProfile,
            authMethodHint = "google"
        )
    }

    private suspend fun finalizeSupabaseProfileSession(
        session: AuthSessionResponse,
        fallbackEmpId: String,
        fallbackName: String?,
        emailForHint: String?,
        preloadedProfile: UserProfile? = null,
        authMethodHint: String? = null
    ): Result<UserProfile> {
        val remoteProfile = preloadedProfile
            ?: supabaseClient.fetchProfileById(session.accessToken, session.userId).getOrNull()
            ?: UserProfile(
                id = session.userId,
                employeeId = fallbackEmpId,
                fullName = fallbackName?.takeIf { it.isNotBlank() } ?: "Employee $fallbackEmpId",
                mobile = "",
                appRole = "employee",
                isActive = true
            )

        if (!remoteProfile.isActive) {
            auth.signOut()
            return Result.failure(
                IOException("Your Taskz account is currently inactive. Please contact the administrator.")
            )
        }

        val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(session.accessToken, remoteProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        val finalProfile = remoteProfile.copy(avatarSignedUrl = signedUrl)

        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        userPrefs.saveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            employeeId = finalProfile.employeeId,
            mustChangePassword = finalProfile.mustChangePassword
        )
        if (!emailForHint.isNullOrBlank() && finalProfile.employeeId.isNotBlank()) {
            userPrefs.saveEmailEmployeeHint(emailForHint, finalProfile.employeeId)
        }
        if (!emailForHint.isNullOrBlank() && !authMethodHint.isNullOrBlank()) {
            userPrefs.saveEmailAuthMethodHint(emailForHint, authMethodHint)
        }

        val now = System.currentTimeMillis()
        try {
            TaskzApplication.instance.database.syncMetadataDao().apply {
                updateSyncStatus("SYNCED", "Up to date")
                updateLastSuccessfulSync(now)
                updateLastServerFetch(now)
            }
        } catch (_: Exception) {}

        return Result.success(finalProfile)
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

    private fun normalizeMobile(rawNumber: String): String {
        val cleaned = rawNumber.trim().replace(Regex("[^0-9+]"), "")
        if (cleaned.startsWith("+")) return cleaned
        val digits = cleaned.replace("+", "")
        return when {
            digits.length == 10 && digits[0] in '6'..'9' -> "+91$digits"
            digits.length == 10 -> "+1$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> "+$digits"
        }
    }

    internal fun extractEmployeeIdFromUri(uri: Uri?): String? {
        if (uri == null || uri.host != "taskz.app") return null
        val segments = uri.pathSegments ?: return null
        if (segments.size >= 2 && segments[0] == "emp") {
            return segments[1].trim().uppercase().takeIf { it.isNotBlank() }
        }
        return null
    }
}
