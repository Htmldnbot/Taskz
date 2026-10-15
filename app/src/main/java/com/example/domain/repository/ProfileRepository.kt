package com.example.domain.repository

import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.UserProfile
import com.example.data.local.dao.ProfileDao
import com.example.data.local.entity.ProfileEntity
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

class ProfileRepository(
    private val supabaseClient: SupabaseClient,
    private val profileDao: ProfileDao,
    private val userPrefs: UserPreferencesDataStore
) {
    val profileFlow: Flow<UserProfile?> = profileDao.getCachedProfileFlow().map { it?.toDomain() }

    suspend fun getCachedProfile(): UserProfile? = profileDao.getCachedProfile()?.toDomain()

    suspend fun refreshProfile(): Result<UserProfile> {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
            ?: return Result.failure(IOException("No active user session"))
        val userId = session.userId
            ?: return Result.failure(IOException("No active user ID"))

        val result = supabaseClient.fetchProfileById(token, userId)
        val profile = result.getOrElse { return Result.failure(it) }
            ?: return Result.failure(IOException("Profile record not found"))

        if (!profile.isActive) {
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        // Resolve signed URL for private profile photo if path exists
        val signedUrl = if (!profile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(token, profile.profilePhotoPath).getOrNull()
        } else {
            null
        }

        val enriched = profile.copy(avatarSignedUrl = signedUrl)
        profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
        return Result.success(enriched)
    }

    /**
     * Updates user profile following strict safe replacement workflow:
     * 1. Upload new photo to private bucket
     * 2. Confirm upload
     * 3. Update profile_photo_path in DB
     * 4. Confirm DB update
     * 5. Only then delete old file
     */
    suspend fun updateProfile(
        fullName: String,
        mobile: String,
        newPhotoBytes: ByteArray? = null,
        newPhotoFilename: String? = null
    ): Result<UserProfile> {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
            ?: return Result.failure(IOException("No active user session"))
        val userId = session.userId
            ?: return Result.failure(IOException("No active user ID"))

        val currentCached = profileDao.getCachedProfile()
        val oldPhotoPath = currentCached?.profilePhotoPath

        var newPhotoPath: String? = null
        if (newPhotoBytes != null && newPhotoFilename != null) {
            // 1. Upload new file
            val uploadRes = supabaseClient.uploadProfilePhoto(
                accessToken = token,
                userId = userId,
                imageBytes = newPhotoBytes,
                filename = newPhotoFilename
            )
            // 2. Confirm upload
            newPhotoPath = uploadRes.getOrElse { return Result.failure(it) }
        }

        // 3. Update DB with new photo path (or preserve existing)
        val targetPath = newPhotoPath ?: oldPhotoPath
        val updateRes = supabaseClient.updateProfile(
            accessToken = token,
            userId = userId,
            fullName = fullName.trim(),
            mobile = mobile.trim(),
            photoPath = targetPath
        )

        // 4. Confirm DB update
        val updatedProfile = updateRes.getOrElse { return Result.failure(it) }

        // 5. Only now delete old file if a new file was uploaded and confirmed
        if (newPhotoPath != null && !oldPhotoPath.isNullOrBlank() && oldPhotoPath != newPhotoPath) {
            supabaseClient.deleteProfilePhoto(token, oldPhotoPath)
        }

        // 6. Generate signed URL for immediate display
        val signedUrl = if (!updatedProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(token, updatedProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }

        val enriched = updatedProfile.copy(avatarSignedUrl = signedUrl)
        profileDao.insertProfile(ProfileEntity.fromDomain(enriched))
        return Result.success(enriched)
    }
}
