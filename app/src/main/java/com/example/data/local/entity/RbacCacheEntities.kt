package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_auth_context")
data class CachedAuthContextEntity(
    @PrimaryKey val userId: String,
    val employeeId: String,
    val fullName: String,
    val isActive: Boolean,
    val isOnLeave: Boolean = false,
    val activeRoleCode: String,
    val primaryRoleCode: String,
    val assignedRolesJson: String,
    val effectiveScopeLevel: String,
    val zoneId: String?,
    val zoneName: String?,
    val stateId: String?,
    val stateName: String?,
    val cityId: String?,
    val cityName: String?,
    val centreId: String?,
    val centreCode: String?,
    val roleVersion: Long,
    val scopeVersion: Long,
    val lastUpdatedMillis: Long
)

@Entity(tableName = "cached_org_nodes")
data class CachedOrgNodeEntity(
    @PrimaryKey val id: String,
    val nodeType: String, // ZONE | STATE | CITY | CENTRE
    val code: String,
    val name: String,
    val parentZoneId: String? = null,
    val parentStateId: String? = null,
    val parentCityId: String? = null,
    val externalCode: String? = null,
    val districtName: String? = null,
    val address: String? = null,
    val subHub: String? = null,
    val availableLansCsv: String = ""
)

@Entity(tableName = "dispatched_notification_keys")
data class DispatchedNotificationKeyEntity(
    @PrimaryKey val eventKey: String,
    val userId: String,
    val dispatchedAtMillis: Long = System.currentTimeMillis()
)
