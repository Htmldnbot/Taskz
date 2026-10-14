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
        val authId = session.authUserId
            ?: return Result.failure(IOException("No active user ID"))

        val result = supabaseClient.fetchProfileByAuthId(token, authId)
        val profile = result.getOrElse { return Result.failure(it) }
            ?: return Result.failure(IOException("Profile record not found"))

        if (!profile.isActive) {
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        profileDao.insertProfile(ProfileEntity.fromDomain(profile))
        return Result.success(profile)
    }

    suspend fun updateProfile(
        fullName: String,
        mobile: String,
        newPhotoBytes: ByteArray? = null,
        newPhotoFilename: String? = null
    ): Result<UserProfile> {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
            ?: return Result.failure(IOException("No active user session"))
        val authId = session.authUserId
            ?: return Result.failure(IOException("No active user ID"))

        var photoUrl: String? = null
        if (newPhotoBytes != null && newPhotoFilename != null) {
            val uploadRes = supabaseClient.uploadProfilePhoto(
                accessToken = token,
                authUserId = authId,
                imageBytes = newPhotoBytes,
                filename = newPhotoFilename
            )
            photoUrl = uploadRes.getOrElse { return Result.failure(it) }
        }

        val updateRes = supabaseClient.updateProfile(
            accessToken = token,
            authUserId = authId,
            fullName = fullName.trim(),
            mobile = mobile.trim(),
            photoUrl = photoUrl
        )

        val updatedProfile = updateRes.getOrElse { return Result.failure(it) }
        profileDao.insertProfile(ProfileEntity.fromDomain(updatedProfile))
        return Result.success(updatedProfile)
    }
}
