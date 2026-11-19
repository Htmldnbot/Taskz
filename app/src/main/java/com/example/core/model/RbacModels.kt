package com.example.core.model

enum class TaskzRole(
    val code: String,
    val displayName: String,
    val hierarchyRank: Int,
    val defaultScope: ScopeLevel,
    val isOnboardingAllowed: Boolean = false
) {
    IT_MANAGER("IT_MANAGER", "IT Manager", 10, ScopeLevel.CENTRE, isOnboardingAllowed = true),
    VCO("VCO", "VCO", 20, ScopeLevel.CITY, isOnboardingAllowed = true),
    CITY_HEAD("CITY_HEAD", "City Head", 30, ScopeLevel.CITY),
    ROM("ROM", "ROM", 40, ScopeLevel.STATE),
    ZOM("ZOM", "ZOM", 50, ScopeLevel.ZONE),
    COMMAND_CENTER("COMMAND_CENTER", "SPOC", 60, ScopeLevel.GLOBAL),
    SUPER_ADMIN("SUPER_ADMIN", "Admin", 100, ScopeLevel.GLOBAL);

    companion object {
        fun fromCode(raw: String?): TaskzRole {
            return when (raw?.trim()?.uppercase()) {
                "IT_MANAGER", "ITM", "SO", "SITE_OFFICER", "SITE OFFICER",
                "SYSTEM_OPERATOR", "SYST_OPERATOR", "EMPLOYEE", "", null -> IT_MANAGER
                "VCO", "VENUE_HEAD" -> VCO
                "CITY_HEAD", "HOM" -> CITY_HEAD
                "ROM" -> ROM
                "ZOM" -> ZOM
                "COMMAND_CENTER", "SPOC" -> COMMAND_CENTER
                "SUPER_ADMIN", "ADMIN" -> SUPER_ADMIN
                else -> IT_MANAGER
            }
        }

        val onboardingRoles: List<TaskzRole> = listOf(IT_MANAGER, VCO)
        val escalationChain: List<TaskzRole> = listOf(IT_MANAGER, VCO, CITY_HEAD, ROM, ZOM, SUPER_ADMIN)
    }
}

enum class ScopeLevel(val code: String, val label: String, val rank: Int) {
    CENTRE("CENTRE", "Centre", 10),
    CITY("CITY", "City", 20),
    STATE("STATE", "State", 30),
    ZONE("ZONE", "Zone", 40),
    GLOBAL("GLOBAL", "Global", 50);

    companion object {
        fun fromCode(raw: String?): ScopeLevel {
            return entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: CENTRE
        }
    }
}

data class AssignedRoleInfo(
    val roleId: String,
    val roleCode: String,
    val roleName: String,
    val hierarchyLevel: Int = 10,
    val defaultScopeLevel: String = "CENTRE",
    val isPrimary: Boolean = false
) {
    val taskzRole: TaskzRole
        get() = TaskzRole.fromCode(roleCode)
}

data class OrgScope(
    val effectiveScopeLevel: ScopeLevel = ScopeLevel.CENTRE,
    val zoneId: String? = null,
    val zoneName: String? = null,
    val stateId: String? = null,
    val stateName: String? = null,
    val cityId: String? = null,
    val cityName: String? = null,
    val centreId: String? = null,
    val centreCode: String? = null,
    val assignedLan: String? = null
) {
    fun summaryLabel(): String {
        return when (effectiveScopeLevel) {
            ScopeLevel.GLOBAL -> "Global"
            ScopeLevel.ZONE -> zoneName ?: "Assigned Zone"
            ScopeLevel.STATE -> stateName ?: "Assigned State"
            ScopeLevel.CITY -> listOfNotNull(cityName, centreCode).joinToString(" • ").ifBlank { "Assigned City" }
            ScopeLevel.CENTRE -> listOfNotNull(centreCode, cityName, assignedLan?.takeIf { it.isNotBlank() }).joinToString(" • ").ifBlank { "Assigned Centre" }
        }
    }
}

data class AuthorizationContext(
    val userId: String = "",
    val employeeId: String = "",
    val fullName: String = "",
    val isActive: Boolean = true,
    val isOnLeave: Boolean = false,
    val activeRole: TaskzRole = TaskzRole.IT_MANAGER,
    val primaryRole: TaskzRole = TaskzRole.IT_MANAGER,
    val assignedRoles: List<AssignedRoleInfo> = emptyList(),
    val scope: OrgScope = OrgScope(),
    val roleVersion: Long = 1L,
    val scopeVersion: Long = 1L,
    val serverNow: String? = null
) {
    val combinedVersion: Long get() = roleVersion + scopeVersion
}

data class OrgZone(val id: String, val code: String, val name: String)
data class OrgState(val id: String, val zoneId: String, val code: String, val name: String)
data class OrgCity(val id: String, val stateId: String, val zoneId: String, val code: String, val name: String)
data class OrgCentre(
    val id: String,
    val externalCode: String?,
    val cityId: String,
    val stateId: String,
    val zoneId: String,
    val code: String,
    val name: String,
    val districtName: String? = null
)

data class OrgHierarchyData(
    val zones: List<OrgZone> = emptyList(),
    val states: List<OrgState> = emptyList(),
    val cities: List<OrgCity> = emptyList(),
    val centres: List<OrgCentre> = emptyList()
)

data class ScopedMemberSearchResult(
    val id: String,
    val employeeId: String,
    val fullName: String,
    val email: String? = null,
    val mobile: String? = null,
    val activeRoleCode: String = "IT_MANAGER",
    val assignedRoles: List<String> = emptyList(),
    val centreId: String? = null,
    val centreCode: String? = null,
    val cityId: String? = null,
    val cityName: String? = null,
    val stateId: String? = null,
    val stateName: String? = null,
    val zoneId: String? = null,
    val zoneName: String? = null,
    val isActive: Boolean = true,
    val isOnLeave: Boolean = false
)

data class ScopedDashboardSummary(
    val activeRole: TaskzRole = TaskzRole.IT_MANAGER,
    val pendingTasks: Int = 0,
    val overdueTasks: Int = 0,
    val assignedTasks: Int = 0,
    val openIncidents: Int = 0,
    val escalatedIncidents: Int = 0,
    val unreadNotifications: Int = 0,
    val activeUsers: Int = 0,
    val totalCentres: Int = 0
)

data class AdminAuditEntry(
    val id: Long,
    val adminUserId: String?,
    val adminName: String?,
    val targetUserId: String?,
    val targetName: String?,
    val targetEmployeeId: String?,
    val action: String,
    val entityType: String?,
    val metadataJson: String,
    val createdAt: String
)

enum class NotificationCategory(val channelId: String, val displayName: String, val urgent: Boolean = false) {
    TASK_ASSIGNMENT("taskz_task_assignment", "Task Assignment", urgent = false),
    TASK_REMINDER("taskz_task_reminder", "Task Reminder", urgent = true),
    TASK_UPDATE("taskz_task_update", "Task Update", urgent = false),
    INCIDENT("taskz_incident", "Incident Alerts", urgent = false),
    INCIDENT_ESCALATION("taskz_incident_escalation", "Incident Escalation", urgent = true),
    SYSTEM_ALERT("taskz_system_alert", "System Alert", urgent = true),
    ADMIN_ALERT("taskz_admin_alert", "Administrative Alert", urgent = false);

    companion object {
        fun fromRaw(category: String?, type: String? = null): NotificationCategory {
            val normalized = category?.trim()?.lowercase()
            return when (normalized) {
                "task_assignment" -> TASK_ASSIGNMENT
                "task_reminder" -> TASK_REMINDER
                "task_update" -> TASK_UPDATE
                "incident" -> INCIDENT
                "incident_escalation" -> INCIDENT_ESCALATION
                "system_alert" -> SYSTEM_ALERT
                "admin_alert" -> ADMIN_ALERT
                else -> when (type?.trim()?.lowercase()) {
                    "task_assigned" -> TASK_ASSIGNMENT
                    "incident_escalated" -> INCIDENT_ESCALATION
                    "role_changed" -> ADMIN_ALERT
                    else -> TASK_UPDATE
                }
            }
        }
    }
}
