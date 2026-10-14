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
import java.io.IOException

class AuthRepository(
    private val supabaseClient: SupabaseClient,
    private val profileDao: ProfileDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val userPrefs: UserPreferencesDataStore
) {
    companion object {
        private const val TAG = "TaskzAuthRepo"
    }

    val currentProfileFlow: Flow<UserProfile?> = profileDao.getCachedProfileFlow().map { it?.toDomain() }

    /**
     * Complete Sign Up flow:
     * 1. Register with deterministic internal ID and password
     * 2. (Optional) Upload photo to Supabase storage if provided
     * 3. Insert record in 'profiles' table
     * 4. Cache profile in Room
     * 5. Save session in DataStore
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

        var photoUrl: String? = null
        if (photoBytes != null && photoFilename != null && session.accessToken.isNotBlank()) {
            val uploadRes = supabaseClient.uploadProfilePhoto(
                accessToken = session.accessToken,
                authUserId = session.authUserId,
                imageBytes = photoBytes,
                filename = photoFilename
            )
            photoUrl = uploadRes.getOrNull()
        }

        // If access token is empty (e.g., Supabase email confirmation required), attempt immediate login
        var activeToken = session.accessToken
        if (activeToken.isBlank()) {
            val loginRes = supabaseClient.login(trimmedEmpId, password)
            if (loginRes.isSuccess) {
                activeToken = loginRes.getOrThrow().accessToken
            }
        }

        val newProfile = UserProfile(
            authUserId = session.authUserId,
            employeeId = trimmedEmpId,
            fullName = fullName.trim(),
            mobile = mobile.trim(),
            profilePhotoUrl = photoUrl,
            appRole = "employee",
            isActive = true
        )

        val createResult = if (activeToken.isNotBlank()) {
            supabaseClient.createProfile(activeToken, newProfile)
        } else {
            Result.success(newProfile)
        }

        val createdProfile = createResult.getOrElse { return Result.failure(it) }

        // Cache in Room & DataStore
        profileDao.insertProfile(ProfileEntity.fromDomain(createdProfile))
        if (activeToken.isNotBlank()) {
            userPrefs.saveSession(
                accessToken = activeToken,
                refreshToken = session.refreshToken,
                authUserId = session.authUserId,
                employeeId = trimmedEmpId
            )
            syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())
        }

        return Result.success(createdProfile)
    }

    /**
     * Login flow:
     * 1. Authenticate with Employee ID + Password
     * 2. Fetch user profile from Supabase PostgREST
     * 3. Check is_active flag. If inactive, block access immediately!
     * 4. Cache in Room & DataStore
     */
    suspend fun login(employeeId: String, password: String): Result<UserProfile> {
        val trimmedEmpId = employeeId.trim().uppercase()
        val loginResult = supabaseClient.login(trimmedEmpId, password)
        val session = loginResult.getOrElse { return Result.failure(it) }

        // Fetch remote profile
        val profileResult = supabaseClient.fetchProfileByAuthId(session.accessToken, session.authUserId)
        val profile = profileResult.getOrElse {
            return Result.failure(IOException("Failed to retrieve profile: ${it.message}"))
        }

        if (profile == null) {
            return Result.failure(IOException("Profile record not found for this user."))
        }

        // Enforce account status:
        if (!profile.isActive) {
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        // Persist session and cache
        userPrefs.saveSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            authUserId = session.authUserId,
            employeeId = profile.employeeId,
            mustChangePassword = profile.mustChangePassword
        )
        profileDao.insertProfile(ProfileEntity.fromDomain(profile))
        syncMetadataDao.updateLastSuccessfulSync(System.currentTimeMillis())

        return Result.success(profile)
    }

    /**
     * Restore session on app launch without prompting for login if valid.
     */
    suspend fun restoreSession(): Result<UserProfile?> {
        val session = userPrefs.getUserSession()
        if (!session.isAuthenticated) {
            return Result.success(null)
        }

        val cachedProfile = profileDao.getCachedProfile()?.toDomain()

        // Verify with server in background
        val token = session.accessToken ?: return Result.success(cachedProfile)
        val remoteProfileRes = supabaseClient.fetchProfileByAuthId(token, session.authUserId ?: "")

        if (remoteProfileRes.isSuccess) {
            val remoteProfile = remoteProfileRes.getOrNull()
            if (remoteProfile != null) {
                if (!remoteProfile.isActive) {
                    // Account was deactivated! Clear session and notify
                    logout()
                    return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                }
                profileDao.insertProfile(ProfileEntity.fromDomain(remoteProfile))
                syncMetadataDao.updateLastServerFetch(System.currentTimeMillis())
                return Result.success(remoteProfile)
            }
        } else {
            // Attempt token refresh if error looks like expired token
            val refresh = session.refreshToken
            if (!refresh.isNullOrBlank()) {
                val refreshRes = supabaseClient.refreshSession(refresh)
                if (refreshRes.isSuccess) {
                    val newSession = refreshRes.getOrThrow()
                    userPrefs.updateAccessToken(newSession.accessToken, newSession.refreshToken)
                    val retryProfileRes = supabaseClient.fetchProfileByAuthId(newSession.accessToken, session.authUserId ?: "")
                    if (retryProfileRes.isSuccess && retryProfileRes.getOrNull() != null) {
                        val validProfile = retryProfileRes.getOrThrow()!!
                        if (!validProfile.isActive) {
                            logout()
                            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
                        }
                        profileDao.insertProfile(ProfileEntity.fromDomain(validProfile))
                        return Result.success(validProfile)
                    }
                }
            }
        }

        // If offline, return cached profile gracefully
        return Result.success(cachedProfile)
    }

    /**
     * Logout and clear all sensitive data.
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
