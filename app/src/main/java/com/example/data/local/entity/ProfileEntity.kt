package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.UserProfile

@Entity(tableName = "cached_profile")
data class ProfileEntity(
    @PrimaryKey val id: String, // UUID matching auth.users(id) - ONE identity model
    val employeeId: String,
    val fullName: String,
    val mobile: String,
    val email: String? = null,
    val profilePhotoPath: String?,
    val avatarSignedUrl: String?,
    val appRole: String,
    val activeRoleCode: String = "IT_MANAGER",
    val primaryRoleCode: String = "IT_MANAGER",
    val assignedRoleCodesCsv: String = "IT_MANAGER",
    val zoneId: String? = null,
    val zoneName: String? = null,
    val stateId: String? = null,
    val stateName: String? = null,
    val cityId: String? = null,
    val cityName: String? = null,
    val centreId: String? = null,
    val centreCode: String? = null,
    val roleVersion: Long = 1L,
    val scopeVersion: Long = 1L,
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
        email = email,
        profilePhotoPath = profilePhotoPath,
        avatarSignedUrl = avatarSignedUrl,
        appRole = appRole,
        activeRoleCode = activeRoleCode,
        primaryRoleCode = primaryRoleCode,
        assignedRoleCodes = assignedRoleCodesCsv.split(",").mapNotNull { it.trim().takeIf(String::isNotEmpty) }.ifEmpty { listOf(activeRoleCode) },
        zoneId = zoneId,
        zoneName = zoneName,
        stateId = stateId,
        stateName = stateName,
        cityId = cityId,
        cityName = cityName,
        centreId = centreId,
        centreCode = centreCode,
        roleVersion = roleVersion,
        scopeVersion = scopeVersion,
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
            email = domain.email,
            profilePhotoPath = domain.profilePhotoPath,
            avatarSignedUrl = domain.avatarSignedUrl,
            appRole = domain.appRole,
            activeRoleCode = domain.activeRoleCode,
            primaryRoleCode = domain.primaryRoleCode,
            assignedRoleCodesCsv = domain.assignedRoleCodes.joinToString(","),
            zoneId = domain.zoneId,
            zoneName = domain.zoneName,
            stateId = domain.stateId,
            stateName = domain.stateName,
            cityId = domain.cityId,
            cityName = domain.cityName,
            centreId = domain.centreId,
            centreCode = domain.centreCode,
            roleVersion = domain.roleVersion,
            scopeVersion = domain.scopeVersion,
            isActive = domain.isActive,
            mustChangePassword = domain.mustChangePassword,
            lastSeenAt = domain.lastSeenAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
