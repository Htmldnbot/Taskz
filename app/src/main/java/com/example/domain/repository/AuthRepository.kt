package com.example.domain.repository

import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncStatus
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.ProfileEntity
import com.example.data.remote.AuthSessionResponse
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class SessionExpiredException(message: String, cause: Throwable? = null) : IOException(message, cause)
class NetworkUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

class AuthRepository(
    private val supabaseClient: SupabaseClient,
    private val profileDao: ProfileDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val userPrefs: UserPreferencesDataStore
) {
    private val tokenRefreshMutex = Mutex()

    val currentProfileFlow: Flow<UserProfile?> = profileDao.getCachedProfileFlow().map { it?.toDomain() }

    /**
     * Sign up using Employee ID + Password (internal deterministic identifier mapped cleanly to auth.users).
     */
    suspend fun signUp(
        employeeId: String,
        password: String,
        fullName: String,
        mobile: String,
        photoBytes: ByteArray? = null,
        photoFilename: String? = null
    ): Result<UserProfile> {
        val trimmedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val normalizedMobile = SupabaseConfig.normalizeMobile(mobile)
        val session = supabaseClient.signUp(
            employeeId = trimmedEmpId,
            password = password,
            fullName = fullName.trim(),
            mobile = normalizedMobile
        ).getOrElse { return Result.failure(it) }

        var activeSession = session
        if (activeSession.accessToken.isBlank()) {
            val loginRes = supabaseClient.login(trimmedEmpId, password)
            if (loginRes.isSuccess) {
                activeSession = loginRes.getOrThrow()
            }
        }

        if (activeSession.accessToken.isBlank()) {
            return Result.failure(
                IOException(
                    "Account was registered on the server, but no active session was returned. " +
                        "Please ensure 'Confirm email' is disabled in Supabase Auth -> Providers -> Email for Employee ID onboarding."
                )
            )
        }

        return finalizeAuthenticatedSession(
            session = activeSession,
            fallbackEmployeeId = trimmedEmpId,
            fallbackFullName = fullName.trim(),
            fallbackMobile = normalizedMobile,
            photoBytes = photoBytes,
            photoFilename = photoFilename
        )
    }

    /**
     * Register a new user with real Email + Password + Employee ID + Mobile in Supabase Auth.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        employeeId: String,
        fullName: String,
        mobile: String,
        photoBytes: ByteArray? = null,
        photoFilename: String? = null
    ): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val trimmedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val normalizedMobile = SupabaseConfig.normalizeMobile(mobile)

        val session = supabaseClient.signUpWithEmail(
            email = cleanEmail,
            password = password,
            employeeId = trimmedEmpId,
            fullName = fullName.trim(),
            mobile = normalizedMobile
        ).getOrElse { return Result.failure(it) }

        var activeSession = session
        if (activeSession.accessToken.isBlank()) {
            val loginRes = supabaseClient.loginWithEmail(cleanEmail, password)
            if (loginRes.isSuccess) {
                activeSession = loginRes.getOrThrow()
            }
        }

        if (activeSession.accessToken.isBlank()) {
            return Result.failure(
                IOException(
                    "Registration created for $cleanEmail. Please confirm your email from your inbox before signing in."
                )
            )
        }

        userPrefs.saveEmailEmployeeHint(cleanEmail, trimmedEmpId)
        userPrefs.saveEmailAuthMethodHint(cleanEmail, "email")

        return finalizeAuthenticatedSession(
            session = activeSession,
            fallbackEmployeeId = trimmedEmpId,
            fallbackFullName = fullName.trim(),
            fallbackMobile = normalizedMobile,
            photoBytes = photoBytes,
            photoFilename = photoFilename
        )
    }

    private suspend fun finalizeAuthenticatedSession(
        session: AuthSessionResponse,
        fallbackEmployeeId: String,
        fallbackFullName: String,
        fallbackMobile: String,
        photoBytes: ByteArray?,
        photoFilename: String?
    ): Result<UserProfile> {
        val activeToken = session.accessToken
        var photoPath: String? = null
        if (photoBytes != null && photoFilename != null) {
            val uploadRes = supabaseClient.uploadProfilePhoto(
                accessToken = activeToken,
                userId = session.userId,
                imageBytes = photoBytes,
                filename = photoFilename
            )
            photoPath = uploadRes.getOrNull()
            if (photoPath != null) {
                supabaseClient.updateProfile(
                    accessToken = activeToken,
                    userId = session.userId,
                    fullName = fallbackFullName,
                    mobile = fallbackMobile,
                    photoPath = photoPath
                )
            }
        }

        var remoteProfile = supabaseClient.fetchProfileById(activeToken, session.userId).getOrNull()
        if (remoteProfile == null) {
            kotlinx.coroutines.delay(400)
            remoteProfile = supabaseClient.fetchProfileById(activeToken, session.userId).getOrNull()
        }

        if (remoteProfile == null && fallbackEmployeeId.isNotBlank()) {
            val completed = supabaseClient.completeMyProfile(
                accessToken = activeToken,
                employeeId = fallbackEmployeeId,
                fullName = fallbackFullName,
                mobile = fallbackMobile
            )
            if (completed.isSuccess) {
                remoteProfile = completed.getOrNull()
            }
        }

        val resolvedProfile = remoteProfile ?: return Result.failure(
            IOException("Authenticated with Supabase, but user profile could not be created or loaded.")
        )

        if (!resolvedProfile.isActive) {
            supabaseClient.logout(activeToken)
            userPrefs.clearSession()
            return Result.failure(
                IOException("Your Taskz account is currently inactive. Please contact the administrator.")
            )
        }

        val signedUrl = if (!resolvedProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(activeToken, resolvedProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        val finalProfile = resolvedProfile.copy(avatarSignedUrl = signedUrl)

        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        userPrefs.saveSession(
            accessToken = activeToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            employeeId = finalProfile.employeeId,
            mustChangePassword = finalProfile.mustChangePassword
        )
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(finalProfile)
    }

    /**
     * Login with Employee ID + Password via Edge Function / Supabase Auth.
     */
    suspend fun login(employeeId: String, password: String): Result<UserProfile> {
        val trimmedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val session = supabaseClient.login(trimmedEmpId, password)
            .getOrElse { return Result.failure(it) }

        return loadProfileAndSaveSession(session)
    }

    /**
     * Login directly with Email + Password via native Supabase Auth.
     */
    suspend fun loginWithEmail(email: String, password: String): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val session = supabaseClient.loginWithEmail(cleanEmail, password)
            .getOrElse { return Result.failure(it) }

        val result = loadProfileAndSaveSession(session, allowPendingProfileCompletion = true)
        result.onSuccess { prof ->
            if (prof.employeeId.isNotBlank()) {
                userPrefs.saveEmailEmployeeHint(cleanEmail, prof.employeeId)
                userPrefs.saveEmailAuthMethodHint(cleanEmail, "email")
            }
        }
        return result
    }

    /**
     * Authenticate with Google ID Token + raw nonce against Supabase Auth (/auth/v1/token?grant_type=id_token).
     * If the authenticated user has no public.profiles row yet, saves the Supabase session so
     * completeGoogleProfile() can execute complete_my_profile() and returns a profile with blank employeeId.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String,
        displayNameHint: String? = null,
        emailHint: String? = null
    ): Result<UserProfile> {
        if (idToken.isBlank() || rawNonce.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token or nonce is missing."))
        }

        val session = supabaseClient.signInWithGoogleIdToken(idToken, rawNonce)
            .getOrElse { return Result.failure(it) }

        val result = loadProfileAndSaveSession(
            session = session,
            allowPendingProfileCompletion = true,
            fallbackFullName = displayNameHint.orEmpty()
        )
        result.onSuccess { prof ->
            val cleanEmail = emailHint?.trim()?.lowercase()
            if (!cleanEmail.isNullOrBlank()) {
                if (prof.employeeId.isNotBlank()) {
                    userPrefs.saveEmailEmployeeHint(cleanEmail, prof.employeeId)
                }
                userPrefs.saveEmailAuthMethodHint(cleanEmail, "google")
            }
        }
        return result
    }

    private suspend fun loadProfileAndSaveSession(
        session: AuthSessionResponse,
        allowPendingProfileCompletion: Boolean = false,
        fallbackFullName: String = ""
    ): Result<UserProfile> {
        val profile = supabaseClient.fetchProfileById(session.accessToken, session.userId)
            .getOrElse {
                return Result.failure(IOException("Failed to retrieve profile: ${it.message}"))
            }

        if (profile == null) {
            if (allowPendingProfileCompletion) {
                userPrefs.saveSession(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    userId = session.userId,
                    employeeId = "",
                    mustChangePassword = false
                )
                return Result.success(
                    UserProfile(
                        id = session.userId,
                        employeeId = "",
                        fullName = fallbackFullName,
                        mobile = "",
                        appRole = "employee",
                        isActive = true
                    )
                )
            }
            return Result.failure(IOException("Profile record not found for this account."))
        }

        if (!profile.isActive) {
            supabaseClient.logout(session.accessToken)
            userPrefs.clearSession()
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        val signedUrl = if (!profile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(session.accessToken, profile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        val finalProfile = profile.copy(avatarSignedUrl = signedUrl)

        userPrefs.saveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            employeeId = finalProfile.employeeId,
            mustChangePassword = finalProfile.mustChangePassword
        )
        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(finalProfile)
    }

    /**
     * Completes onboarding for an authenticated Google / email user by calling complete_my_profile().
     */
    suspend fun completeGoogleProfile(
        employeeId: String,
        fullName: String,
        mobile: String
    ): Result<UserProfile> {
        val validToken = getValidAccessToken().getOrElse { return Result.failure(it) }
        val session = userPrefs.getUserSession()
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val normalizedMobile = SupabaseConfig.normalizeMobile(mobile)

        val completedProfile = supabaseClient.completeMyProfile(
            accessToken = validToken,
            employeeId = normalizedEmpId,
            fullName = fullName.trim(),
            mobile = normalizedMobile
        ).getOrElse { return Result.failure(it) }

        if (!completedProfile.isActive) {
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        val signedUrl = if (!completedProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(validToken, completedProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        val finalProfile = completedProfile.copy(avatarSignedUrl = signedUrl)

        userPrefs.saveSession(
            accessToken = validToken,
            refreshToken = session.refreshToken,
            userId = finalProfile.id,
            employeeId = finalProfile.employeeId,
            mustChangePassword = finalProfile.mustChangePassword
        )
        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(finalProfile)
    }

    suspend fun cancelPendingProfileCompletion() {
        val session = userPrefs.getUserSession()
        if (!session.accessToken.isNullOrBlank() && session.employeeId.isNullOrBlank()) {
            supabaseClient.logout(session.accessToken)
            userPrefs.clearSession()
            profileDao.clearProfile()
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return supabaseClient.sendPasswordResetEmail(email)
    }

    /**
     * Checks if a JWT token has expired or will expire within [bufferSeconds].
     */
    fun isTokenExpired(token: String?, bufferSeconds: Long = 60L): Boolean {
        if (token.isNullOrBlank() || token.startsWith("offline_")) return true
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payloadSegment = parts[1]
            val payloadBytes = decodeBase64Url(payloadSegment)
            val payloadString = String(payloadBytes, Charsets.UTF_8)
            val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
            val match = expRegex.find(payloadString) ?: return false
            val expSeconds = match.groupValues[1].toLong()
            val currentSeconds = System.currentTimeMillis() / 1000
            currentSeconds >= (expSeconds - bufferSeconds)
        } catch (_: Exception) {
            true
        }
    }

    private fun decodeBase64Url(input: String): ByteArray {
        var str = input.replace('-', '+').replace('_', '/')
        while (str.length % 4 != 0) {
            str += "="
        }
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    /**
     * Thread-safe access token provider with automatic refresh via Supabase Auth.
     */
    suspend fun getValidAccessToken(): Result<String> = tokenRefreshMutex.withLock {
        val session = userPrefs.getUserSession()
        val currentToken = session.accessToken

        if (currentToken.isNullOrBlank()) {
            return Result.failure(SessionExpiredException("No active session found"))
        }

        if (currentToken.startsWith("offline_") || currentToken.split(".").size != 3) {
            return Result.failure(SessionExpiredException("Session upgrade required: Please sign in again."))
        }

        if (!isTokenExpired(currentToken, bufferSeconds = 60L)) {
            return Result.success(currentToken)
        }

        Log.i("TaskzAuth", "Access token expiring within 60s; refreshing via Supabase Auth")

        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return Result.failure(SessionExpiredException("Session expired: Reauthentication required"))
        }

        val refreshResult = supabaseClient.refreshSession(refreshToken)
        if (refreshResult.isSuccess) {
            val newSession = refreshResult.getOrThrow()
            userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
            Result.success(newSession.accessToken)
        } else {
            val err = refreshResult.exceptionOrNull() ?: Exception("Unknown refresh error")
            if (isNetworkFailure(err)) {
                Result.failure(NetworkUnavailableException("Network unavailable: cannot refresh session", err))
            } else {
                val errMsg = err.message.orEmpty()
                Result.failure(SessionExpiredException("Authentication required: $errMsg", err))
            }
        }
    }

    /**
     * Force an immediate token refresh after receiving HTTP 401.
     */
    suspend fun forceRefreshAccessToken(): Result<String> = tokenRefreshMutex.withLock {
        val session = userPrefs.getUserSession()
        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return Result.failure(SessionExpiredException("Session expired: Reauthentication required"))
        }

        if (session.accessToken.isNullOrBlank() ||
            session.accessToken.startsWith("offline_") ||
            session.accessToken.split(".").size != 3
        ) {
            return Result.failure(SessionExpiredException("Session upgrade required: Please sign in again."))
        }

        val refreshResult = supabaseClient.refreshSession(refreshToken)
        if (refreshResult.isSuccess) {
            val newSession = refreshResult.getOrThrow()
            userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
            Result.success(newSession.accessToken)
        } else {
            val err = refreshResult.exceptionOrNull() ?: Exception("Unknown refresh error")
            if (isNetworkFailure(err)) {
                Result.failure(NetworkUnavailableException("Network unavailable: cannot refresh session", err))
            } else {
                Result.failure(SessionExpiredException("Authentication required: ${err.message}", err))
            }
        }
    }

    /**
     * Restore valid Supabase session on app launch, automatically refreshing expired JWTs.
     * Preserves cached profile on offline launches when a valid persisted session exists.
     */
    suspend fun restoreSession(): Result<UserProfile?> {
        val session = userPrefs.getUserSession()
        if (!session.isAuthenticated) {
            return Result.success(null)
        }

        val token = session.accessToken
        if (token.isNullOrBlank() || token.startsWith("offline_") || token.split(".").size != 3) {
            userPrefs.clearSession()
            profileDao.clearProfile()
            return Result.failure(SessionExpiredException("Please sign in again to establish a Supabase session."))
        }

        val cachedProfile = profileDao.getCachedProfile()?.toDomain()

        val tokenRes = getValidAccessToken()
        if (tokenRes.isFailure) {
            val ex = tokenRes.exceptionOrNull()!!
            return if (ex is NetworkUnavailableException || isNetworkFailure(ex)) {
                Log.i("TaskzAuth", "Offline launch; preserving cached profile")
                Result.success(cachedProfile)
            } else {
                Log.w("TaskzAuth", "Session restoration requires reauthentication: ${ex.message}")
                Result.failure(ex)
            }
        }

        val validToken = tokenRes.getOrThrow()
        val userId = session.userId ?: return Result.success(cachedProfile)

        val remoteProfileRes = supabaseClient.fetchProfileById(validToken, userId)
        if (remoteProfileRes.isSuccess) {
            val remoteProfile = remoteProfileRes.getOrNull()
            if (remoteProfile != null) {
                if (!remoteProfile.isActive) {
                    return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                }

                val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
                    supabaseClient.getSignedProfilePhotoUrl(validToken, remoteProfile.profilePhotoPath).getOrNull()
                } else {
                    null
                }
                val enriched = remoteProfile.copy(avatarSignedUrl = signedUrl)

                profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
                syncMetadataDao.updateLastServerFetch(System.currentTimeMillis())
                return Result.success(enriched)
            }
            // Auth session exists, profile not yet completed
            return Result.success(null)
        } else {
            val ex = remoteProfileRes.exceptionOrNull()
            if (ex != null && isNetworkFailure(ex)) {
                return Result.success(cachedProfile)
            }
        }

        return Result.success(cachedProfile)
    }

    /**
     * Logout from Supabase Auth and clear local session/cached profile.
     */
    suspend fun logout() {
        val session = userPrefs.getUserSession()
        if (!session.accessToken.isNullOrBlank()) {
            supabaseClient.logout(session.accessToken)
        }
        userPrefs.clearSession()
        profileDao.clearProfile()
        syncMetadataDao.updateSyncStatus(SyncStatus.OFFLINE.name, "Signed out")
    }

    /**
     * Submit password reset request to Admin table.
     */
    suspend fun submitPasswordResetRequest(
        employeeId: String,
        mobile: String,
        message: String?
    ): Result<Unit> {
        val res = supabaseClient.submitPasswordResetRequest(
            employeeId = SupabaseConfig.normalizeEmployeeId(employeeId),
            mobile = SupabaseConfig.normalizeMobile(mobile),
            message = message?.trim()
        )
        return if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull()!!)
    }

    fun isNetworkFailure(throwable: Throwable): Boolean {
        if (throwable is NetworkUnavailableException) return true
        val msg = throwable.message.orEmpty()
        return throwable is java.net.UnknownHostException ||
            throwable is java.net.ConnectException ||
            throwable is java.net.SocketTimeoutException ||
            throwable is java.net.NoRouteToHostException ||
            msg.contains("resolve host", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("unreachable", ignoreCase = true) ||
            msg.contains("route to host", ignoreCase = true) ||
            msg.contains("Supabase is not configured", ignoreCase = true) ||
            msg.contains("Network unreachable", ignoreCase = true) ||
            msg.contains("No internet connection", ignoreCase = true)
    }
}
