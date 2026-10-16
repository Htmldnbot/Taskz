package com.example.domain.repository

import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncStatus
import com.example.core.model.UserProfile
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.ProfileEntity
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

class AuthRepository(
    private val supabaseClient: SupabaseClient,
    private val profileDao: ProfileDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val userPrefs: UserPreferencesDataStore
) {
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
        val trimmedEmpId = employeeId.trim().uppercase()
        val authResult = supabaseClient.signUp(
            employeeId = trimmedEmpId,
            password = password,
            fullName = fullName.trim(),
            mobile = mobile.trim()
        )

        val session = authResult.getOrElse { return Result.failure(it) }

        var activeToken = session.accessToken
        if (activeToken.isBlank()) {
            val loginRes = supabaseClient.login(trimmedEmpId, password)
            if (loginRes.isSuccess) {
                activeToken = loginRes.getOrThrow().accessToken
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
            return Result.failure(
                IOException("Signup verification failed: the server profile record could not be confirmed.")
            )
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
        val trimmedEmpId = employeeId.trim().uppercase()
        val loginResult = supabaseClient.login(trimmedEmpId, password)
        val session = loginResult.getOrElse { return Result.failure(it) }

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
     * Restore session on app launch without prompting for login if session is valid.
     */
    suspend fun restoreSession(): Result<UserProfile?> {
        val session = userPrefs.getUserSession()
        if (!session.isAuthenticated) {
            return Result.success(null)
        }

        val cachedProfile = profileDao.getCachedProfile()?.toDomain()

        // Verify with server
        val token = session.accessToken ?: return Result.success(cachedProfile)
        val userId = session.userId ?: return Result.success(cachedProfile)

        val remoteProfileRes = supabaseClient.fetchProfileById(token, userId)

        if (remoteProfileRes.isSuccess) {
            val remoteProfile = remoteProfileRes.getOrNull()
            if (remoteProfile != null) {
                if (!remoteProfile.isActive) {
                    logout()
                    return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                }

                val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
                    supabaseClient.getSignedProfilePhotoUrl(token, remoteProfile.profilePhotoPath).getOrNull()
                } else {
                    null
                }
                val enriched = remoteProfile.copy(avatarSignedUrl = signedUrl)

                profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
                syncMetadataDao.updateLastServerFetch(System.currentTimeMillis())
                return Result.success(enriched)
            }
        } else {
            // Attempt token refresh
            val refresh = session.refreshToken
            if (!refresh.isNullOrBlank()) {
                val refreshRes = supabaseClient.refreshSession(refresh)
                if (refreshRes.isSuccess) {
                    val newSession = refreshRes.getOrThrow()
                    userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
                    val retryProfileRes = supabaseClient.fetchProfileById(newSession.accessToken, newSession.userId)
                    if (retryProfileRes.isSuccess && retryProfileRes.getOrNull() != null) {
                        val validProfile = retryProfileRes.getOrThrow()!!
                        if (!validProfile.isActive) {
                            logout()
                            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                        }

                        val signedUrl = if (!validProfile.profilePhotoPath.isNullOrBlank()) {
                            supabaseClient.getSignedProfilePhotoUrl(newSession.accessToken, validProfile.profilePhotoPath).getOrNull()
                        } else {
                            null
                        }
                        val enriched = validProfile.copy(avatarSignedUrl = signedUrl)

                        profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
                        return Result.success(enriched)
                    }
                }
            }
        }

        // Return cached profile if offline
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
}
