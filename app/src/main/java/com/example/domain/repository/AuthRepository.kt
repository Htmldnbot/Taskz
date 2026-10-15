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

        var photoPath: String? = null
        if (photoBytes != null && photoFilename != null && activeToken.isNotBlank()) {
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

        val remoteProfile = if (activeToken.isNotBlank()) {
            val fetched = supabaseClient.fetchProfileById(activeToken, session.userId).getOrNull()
            fetched ?: UserProfile(
                id = session.userId,
                employeeId = trimmedEmpId,
                fullName = fullName.trim(),
                mobile = mobile.trim(),
                profilePhotoPath = photoPath,
                appRole = "employee",
                isActive = true
            )
        } else {
            UserProfile(
                id = session.userId,
                employeeId = trimmedEmpId,
                fullName = fullName.trim(),
                mobile = mobile.trim(),
                profilePhotoPath = photoPath,
                appRole = "employee",
                isActive = true
            )
        }

        val signedUrl = if (activeToken.isNotBlank() && !remoteProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(activeToken, remoteProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }

        val finalProfile = remoteProfile.copy(avatarSignedUrl = signedUrl)

        // Cache in Room & DataStore
        profileDao.insertProfile(ProfileEntity.fromDomain(finalProfile))
        if (activeToken.isNotBlank()) {
            userPrefs.saveSession(
                accessToken = activeToken,
                refreshToken = session.refreshToken,
                userId = session.userId,
                employeeId = trimmedEmpId
            )
            syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())
        }

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
