package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.UserProfile

@Entity(tableName = "cached_profile")
data class ProfileEntity(
    @PrimaryKey val authUserId: String,
    val id: String,
    val employeeId: String,
    val fullName: String,
    val mobile: String,
    val profilePhotoUrl: String?,
    val appRole: String,
    val isActive: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain(): UserProfile = UserProfile(
        id = id,
        authUserId = authUserId,
        employeeId = employeeId,
        fullName = fullName,
        mobile = mobile,
        profilePhotoUrl = profilePhotoUrl,
        appRole = appRole,
        isActive = isActive,
        mustChangePassword = mustChangePassword,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: UserProfile): ProfileEntity = ProfileEntity(
            authUserId = domain.authUserId,
            id = domain.id,
            employeeId = domain.employeeId,
            fullName = domain.fullName,
            mobile = domain.mobile,
            profilePhotoUrl = domain.profilePhotoUrl,
            appRole = domain.appRole,
            isActive = domain.isActive,
            mustChangePassword = domain.mustChangePassword,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
