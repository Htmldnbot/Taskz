package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.UserProfile

@Entity(tableName = "cached_profile")
data class ProfileEntity(
    @PrimaryKey val id: String, // UUID matching auth.users(id)
    val employeeId: String,
    val fullName: String,
    val mobile: String,
    val profilePhotoPath: String?,
    val avatarSignedUrl: String?,
    val appRole: String,
    val isActive: Boolean,
    val mustChangePassword: Boolean,
    val lastSeenAt: String?,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        employeeId = employeeId,
        fullName = fullName,
        mobile = mobile,
        profilePhotoPath = profilePhotoPath,
        avatarSignedUrl = avatarSignedUrl,
        appRole = appRole,
        isActive = isActive,
        mustChangePassword = mustChangePassword,
        lastSeenAt = lastSeenAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: UserProfile): ProfileEntity = ProfileEntity(
            id = domain.id,
            employeeId = domain.employeeId,
            fullName = domain.fullName,
            mobile = domain.mobile,
            profilePhotoPath = domain.profilePhotoPath,
            avatarSignedUrl = domain.avatarSignedUrl,
            appRole = domain.appRole,
            isActive = domain.isActive,
            mustChangePassword = domain.mustChangePassword,
            lastSeenAt = domain.lastSeenAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
