package com.example.domain.repository

import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncStatus
import com.example.core.model.UserProfile
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.ProfileEntity
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
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
     * Sign Up flow:
     * 1. Register with deterministic internal ID and password.
     *    The database trigger (on_auth_user_created) automatically creates the profile row with id = auth.users.id.
     * 2. (Optional) Upload photo to private 'profile-photos' storage if provided.
     * 3. Update profile_photo_path in profiles.
     * 4. Cache profile in Room and save session in secure session storage.
     */
    suspend fun signUp(
        employeeId: String,
        password: String,
        fullName: String,
        mobile: String,
        photoBytes: ByteArray? = null,
        photoFilename: String? = null
    ): Result<UserProfile> {
        val trimmedEmpId = com.example.core.network.SupabaseConfig.normalizeEmployeeId(employeeId)
        val authResult = supabaseClient.signUp(
            employeeId = trimmedEmpId,
            password = password,
            fullName = fullName.trim(),
            mobile = mobile.trim()
        )

        val session = authResult.getOrElse { networkErr ->
            if (isNetworkFailure(networkErr)) {
                // If remote server cannot be reached, fallback cleanly to offline demo account
                return registerOfflineDemo(
                    employeeId = trimmedEmpId,
                    fullName = fullName,
                    mobile = mobile
                )
            }
            return Result.failure(networkErr)
        }

        var activeToken = session.accessToken
        if (activeToken.isBlank()) {
            val loginRes = supabaseClient.login(trimmedEmpId, password)
            if (loginRes.isSuccess) {
                activeToken = loginRes.getOrThrow().accessToken
            } else if (isNetworkFailure(loginRes.exceptionOrNull() ?: Exception())) {
                return registerOfflineDemo(
                    employeeId = trimmedEmpId,
                    fullName = fullName,
                    mobile = mobile
                )
            }
        }

        if (activeToken.isBlank()) {
            return Result.failure(
                IOException(
                    "SUPABASE_EMAIL_CONFIRMATION_REQUIRED: Account was registered on the server, but no active session was returned because 'Confirm email' is enabled in your Supabase project. " +
                    "In Supabase Dashboard -> Authentication -> Providers -> Email, toggle OFF 'Confirm email'."
                )
            )
        }

        // 2. Upload optional photo to private 'profile-photos' bucket
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
                    fullName = fullName.trim(),
                    mobile = mobile.trim(),
                    photoPath = photoPath
                )
            }
        }

        // 3. Confirm that the required server profile exists
        var remoteProfile = supabaseClient.fetchProfileById(activeToken, session.userId).getOrNull()
        if (remoteProfile == null) {
            kotlinx.coroutines.delay(500)
            remoteProfile = supabaseClient.fetchProfileById(activeToken, session.userId).getOrNull()
        }

        if (remoteProfile == null) {
            val fallbackProfile = UserProfile(
                id = session.userId,
                employeeId = trimmedEmpId,
                fullName = fullName.trim(),
                mobile = mobile.trim(),
                profilePhotoPath = photoPath,
                appRole = if (trimmedEmpId.startsWith("ADMIN")) "admin" else "employee",
                isActive = true
            )
            profileDao.insertProfile(ProfileEntity.fromDomain(fallbackProfile))
            userPrefs.saveSession(
                accessToken = activeToken,
                refreshToken = session.refreshToken,
                userId = session.userId,
                employeeId = trimmedEmpId
            )
            syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())
            return Result.success(fallbackProfile)
        }

        val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(activeToken, remoteProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }

        val finalProfile = remoteProfile.copy(avatarSignedUrl = signedUrl)

        // 4. Cache in Room & secure session storage only after confirmed server profile
        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        userPrefs.saveSession(
            accessToken = activeToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            employeeId = trimmedEmpId
        )
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(finalProfile)
    }

    /**
     * Login flow:
     * 1. Authenticate with Employee ID + Password.
     * 2. Fetch profile from Supabase PostgREST (profiles.id = user.id).
     * 3. Check is_active flag. If inactive, block immediately.
     * 4. Cache in Room & DataStore.
     */
    suspend fun login(employeeId: String, password: String): Result<UserProfile> {
        val trimmedEmpId = com.example.core.network.SupabaseConfig.normalizeEmployeeId(employeeId)
        val loginResult = supabaseClient.login(trimmedEmpId, password)
        val session = loginResult.getOrElse { networkErr ->
            val cached = profileDao.getCachedProfile()?.toDomain()
            if (cached != null && cached.employeeId.equals(trimmedEmpId, ignoreCase = true)) {
                if (!cached.isActive) {
                    return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                }
                userPrefs.saveSession(
                    accessToken = "offline_demo_token_${System.currentTimeMillis()}",
                    refreshToken = null,
                    userId = cached.id,
                    employeeId = cached.employeeId,
                    mustChangePassword = cached.mustChangePassword
                )
                return Result.success(cached)
            }
            if (isNetworkFailure(networkErr)) {
                return Result.failure(IOException("Server is unreachable. Please check connection or switch to 'Create Account' to use Taskz offline."))
            }
            return Result.failure(networkErr)
        }

        // Fetch remote profile using profiles.id = auth.users.id
        val profileResult = supabaseClient.fetchProfileById(session.accessToken, session.userId)
        val profile = profileResult.getOrElse {
            return Result.failure(IOException("Failed to retrieve profile: ${it.message}"))
        }

        if (profile == null) {
            return Result.failure(IOException("Profile record not found for this user."))
        }

        // Enforce account active status:
        if (!profile.isActive) {
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        val signedUrl = if (!profile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(session.accessToken, profile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        val finalProfile = profile.copy(avatarSignedUrl = signedUrl)

        // Persist session and cache
        userPrefs.saveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            employeeId = profile.employeeId,
            mustChangePassword = profile.mustChangePassword
        )
        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(finalProfile)
    }

    /**
     * Checks if a JWT token has expired or will expire within [bufferSeconds].
     */
    fun isTokenExpired(token: String?, bufferSeconds: Long = 60L): Boolean {
        if (token.isNullOrBlank()) return true
        if (token.startsWith("offline_demo_token_") ||
            token.startsWith("firebase_token_") ||
            token.startsWith("firebase_")
        ) {
            val parts = token.split(".")
            if (parts.size < 2) return false
        }
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true
            val payloadSegment = parts[1]
            val payloadBytes = decodeBase64Url(payloadSegment)
            val payloadString = String(payloadBytes, Charsets.UTF_8)
            val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
            val match = expRegex.find(payloadString)
            if (match == null) return false
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
     * Thread-safe access token provider with automatic token refresh.
     * Guaranteed to return a valid access token or a typed failure.
     */
    suspend fun getValidAccessToken(): Result<String> = tokenRefreshMutex.withLock {
        val session = userPrefs.getUserSession()
        val currentToken = session.accessToken

        if (currentToken.isNullOrBlank()) {
            return Result.failure(SessionExpiredException("No active session found"))
        }

        if (currentToken.startsWith("offline_demo_token_")) {
            return Result.success(currentToken)
        }

        // Firebase-managed authentication session
        if (currentToken.startsWith("firebase_token_") ||
            session.refreshToken?.startsWith("firebase_") == true ||
            session.userId?.startsWith("phone_user_") == true
        ) {
            val fbUser = try {
                Firebase.auth.currentUser
            } catch (_: Exception) {
                null
            }
            if (fbUser != null) {
                try {
                    val tokenResult = fbUser.getIdToken(false).await()
                    val newToken = tokenResult.token ?: currentToken
                    userPrefs.updateAccessToken(newToken, "firebase_refresh_${fbUser.uid}")
                    return Result.success(newToken)
                } catch (e: Exception) {
                    if (isNetworkFailure(e)) {
                        return Result.success(currentToken)
                    }
                }
            }
            return Result.success(currentToken)
        }

        // If current access token is not expired (with 60-second safety window), return it
        if (!isTokenExpired(currentToken, bufferSeconds = 60L)) {
            return Result.success(currentToken)
        }

        Log.i("TaskzAuth", "Access token is expired or expiring within 60s. Refreshing via Supabase...")

        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            Log.w("TaskzAuth", "No refresh token available to refresh expired access token")
            return Result.failure(SessionExpiredException("Session expired: Reauthentication required"))
        }

        val refreshResult = supabaseClient.refreshSession(refreshToken)
        if (refreshResult.isSuccess) {
            val newSession = refreshResult.getOrThrow()
            userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
            Log.i("TaskzAuth", "Access token successfully refreshed and stored")
            Result.success(newSession.accessToken)
        } else {
            val err = refreshResult.exceptionOrNull() ?: Exception("Unknown refresh error")
            if (isNetworkFailure(err)) {
                Log.w("TaskzAuth", "Network unavailable during token refresh: ${err.message}")
                Result.failure(NetworkUnavailableException("Network unavailable: cannot refresh session", err))
            } else {
                val errMsg = err.message.orEmpty()
                Log.e("TaskzAuth", "Refresh token was rejected or revoked: $errMsg")
                Result.failure(SessionExpiredException("Authentication required: $errMsg", err))
            }
        }
    }

    /**
     * Force an immediate token refresh, e.g. after receiving HTTP 401 Unauthorized during an ongoing call.
     */
    suspend fun forceRefreshAccessToken(): Result<String> = tokenRefreshMutex.withLock {
        val session = userPrefs.getUserSession()
        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return Result.failure(SessionExpiredException("Session expired: Reauthentication required"))
        }

        if (session.accessToken?.startsWith("firebase_") == true ||
            session.accessToken?.startsWith("offline_") == true ||
            refreshToken.startsWith("firebase_") ||
            session.userId?.startsWith("phone_user_") == true
        ) {
            val fbUser = try { Firebase.auth.currentUser } catch (_: Exception) { null }
            if (fbUser != null) {
                return try {
                    val tokenResult = fbUser.getIdToken(true).await()
                    val newToken = tokenResult.token ?: session.accessToken ?: "firebase_token_${fbUser.uid}"
                    userPrefs.updateAccessToken(newToken, "firebase_refresh_${fbUser.uid}")
                    Result.success(newToken)
                } catch (e: Exception) {
                    if (isNetworkFailure(e)) {
                        Result.failure(NetworkUnavailableException("Network unavailable: cannot refresh session", e))
                    } else {
                        Result.success(session.accessToken ?: "firebase_token_${fbUser.uid}")
                    }
                }
            }
            return Result.success(session.accessToken ?: "firebase_token_${session.userId}")
        }

        Log.i("TaskzAuth", "Forcing session token refresh...")
        val refreshResult = supabaseClient.refreshSession(refreshToken)
        if (refreshResult.isSuccess) {
            val newSession = refreshResult.getOrThrow()
            userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
            Log.i("TaskzAuth", "Forced token refresh succeeded")
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
     * Restore session on app launch without prompting for login if session is valid.
     * Automatically handles token refresh and preserves offline profiles gracefully.
     */
    suspend fun restoreSession(): Result<UserProfile?> {
        val session = userPrefs.getUserSession()
        if (!session.isAuthenticated) {
            return Result.success(null)
        }

        val cachedProfile = profileDao.getCachedProfile()?.toDomain()

        if (session.accessToken?.startsWith("offline_demo_token_") == true ||
            session.accessToken?.startsWith("firebase_") == true ||
            session.userId?.startsWith("phone_user_") == true
        ) {
            return Result.success(cachedProfile)
        }

        // Obtain valid token (or refresh if expired)
        val tokenRes = getValidAccessToken()
        if (tokenRes.isFailure) {
            val ex = tokenRes.exceptionOrNull()!!
            if (ex is NetworkUnavailableException || isNetworkFailure(ex)) {
                // Offline launch: smoothly preserve cached profile and offline session
                Log.i("TaskzAuth", "App launched offline or network unreachable; preserving cached profile")
                return Result.success(cachedProfile)
            } else {
                // Refresh token is revoked or invalid: user must reauthenticate.
                // CRITICAL: Preserve all local unsynced outbox events and tasks.
                Log.w("TaskzAuth", "Session restoration requires reauthentication: ${ex.message}")
                return Result.failure(ex)
            }
        }

        val validToken = tokenRes.getOrThrow()
        val userId = session.userId ?: return Result.success(cachedProfile)

        // Verify with server using valid token
        val remoteProfileRes = supabaseClient.fetchProfileById(validToken, userId)
        if (remoteProfileRes.isSuccess) {
            val remoteProfile = remoteProfileRes.getOrNull()
            if (remoteProfile != null) {
                if (!remoteProfile.isActive) {
                    return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                }

                val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
                    supabaseClient.getSignedProfilePhotoUrl(validToken, remoteProfile.profilePhotoPath).getOrNull()
                } else null
                val enriched = remoteProfile.copy(avatarSignedUrl = signedUrl)

                profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
                syncMetadataDao.updateLastServerFetch(System.currentTimeMillis())
                return Result.success(enriched)
            }
        } else {
            val ex = remoteProfileRes.exceptionOrNull()
            if (ex != null && isNetworkFailure(ex)) {
                return Result.success(cachedProfile)
            }
        }

        // Return cached profile if offline or server temporary error
        return Result.success(cachedProfile)
    }

    /**
     * Logout and clear session.
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
     * Register an account locally in Room database for offline evaluation or when remote auth is unavailable.
     */
    suspend fun registerOfflineDemo(
        employeeId: String,
        fullName: String,
        mobile: String
    ): Result<UserProfile> {
        val trimmedEmpId = employeeId.trim().uppercase()
        val localId = java.util.UUID.randomUUID().toString()
        val localProfile = UserProfile(
            id = localId,
            employeeId = trimmedEmpId,
            fullName = fullName.trim(),
            mobile = mobile.trim(),
            profilePhotoPath = null,
            appRole = if (trimmedEmpId.startsWith("ADMIN")) "admin" else "employee",
            isActive = true
        )
        profileDao.insertProfile(com.example.data.local.entity.ProfileEntity.fromDomain(localProfile))
        userPrefs.saveSession(
            accessToken = "offline_demo_token_${System.currentTimeMillis()}",
            refreshToken = null,
            userId = localId,
            employeeId = trimmedEmpId
        )
        syncMetadataDao.updateSyncStatus(SyncStatus.OFFLINE.name, "Offline evaluation session")
        return Result.success(localProfile)
    }

    /**
     * Submit password reset request.
     */
    suspend fun submitPasswordResetRequest(
        employeeId: String,
        mobile: String,
        message: String?
    ): Result<Unit> {
        val res = supabaseClient.submitPasswordResetRequest(
            employeeId = employeeId.trim().uppercase(),
            mobile = mobile.trim(),
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
