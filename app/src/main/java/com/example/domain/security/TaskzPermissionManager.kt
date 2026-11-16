package com.example.domain.security

import com.example.core.model.AssignedRoleInfo
import com.example.core.model.AuthorizationContext
import com.example.core.model.OrgScope
import com.example.core.model.ScopeLevel
import com.example.core.model.SystemIncident
import com.example.core.model.TaskItem
import com.example.core.model.TaskzRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized UX Permission & Scope Manager.
 * Note: UI permission checks are strictly for UX gating; Supabase RLS and SECURITY DEFINER RPCs
 * remain the final authoritative security boundary.
 */
class TaskzPermissionManager(
    initialContext: AuthorizationContext = AuthorizationContext()
) {
    private val _context = MutableStateFlow(initialContext)
    val contextFlow: StateFlow<AuthorizationContext> = _context.asStateFlow()

    fun updateContext(newContext: AuthorizationContext) {
        _context.value = newContext
    }

    fun reset() {
        _context.value = AuthorizationContext()
    }

    fun getCurrentContext(): AuthorizationContext = _context.value

    fun getAssignedRoles(): List<AssignedRoleInfo> = _context.value.assignedRoles

    fun getActiveRole(): TaskzRole = _context.value.activeRole

    fun getUserScope(): OrgScope = _context.value.scope

    private fun isSuperAdmin(): Boolean = getActiveRole() == TaskzRole.SUPER_ADMIN

    private fun isActiveAccount(): Boolean = _context.value.isActive

    private fun matchesScope(
        targetScope: ScopeLevel,
        zoneId: String?,
        stateId: String?,
        cityId: String?,
        centreId: String?
    ): Boolean {
        val role = getActiveRole()
        val scope = getUserScope()
        if (role == TaskzRole.SUPER_ADMIN || role == TaskzRole.COMMAND_CENTER) return true
        if (targetScope == ScopeLevel.GLOBAL) {
            // Only true global records without attached org IDs match GLOBAL cleanly
            return zoneId == null && stateId == null && cityId == null && centreId == null
        }

        return when (role) {
            TaskzRole.ZOM -> scope.zoneId != null && zoneId != null && scope.zoneId == zoneId
            TaskzRole.ROM -> {
                if (targetScope == ScopeLevel.ZONE) {
                    scope.zoneId != null && zoneId != null && scope.zoneId == zoneId
                } else {
                    scope.stateId != null && stateId != null && scope.stateId == stateId
                }
            }
            TaskzRole.CITY_HEAD, TaskzRole.VCO -> {
                when (targetScope) {
                    ScopeLevel.ZONE -> scope.zoneId != null && zoneId != null && scope.zoneId == zoneId
                    ScopeLevel.STATE -> scope.stateId != null && stateId != null && scope.stateId == stateId
                    ScopeLevel.CITY -> scope.cityId != null && cityId != null && scope.cityId == cityId
                    ScopeLevel.CENTRE ->
                        (scope.centreId != null && centreId != null && scope.centreId == centreId) ||
                            (scope.cityId != null && cityId != null && scope.cityId == cityId)
                    ScopeLevel.GLOBAL -> false
                }
            }
            TaskzRole.IT_MANAGER -> {
                when (targetScope) {
                    ScopeLevel.ZONE -> scope.zoneId != null && zoneId != null && scope.zoneId == zoneId
                    ScopeLevel.STATE -> scope.stateId != null && stateId != null && scope.stateId == stateId
                    ScopeLevel.CITY -> scope.cityId != null && cityId != null && scope.cityId == cityId
                    ScopeLevel.CENTRE -> scope.centreId != null && centreId != null && scope.centreId == centreId
                    ScopeLevel.GLOBAL -> false
                }
            }
            TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN -> true
        }
    }

    // ---- TASK VISIBILITY vs ACTION PERMISSIONS ----

    fun canViewTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        val activeRole = getActiveRole()
        if (isSuperAdmin() || activeRole == TaskzRole.COMMAND_CENTER) return true
        val uid = _context.value.userId
        if (uid.isNotBlank() && (task.createdBy == uid || task.isAssignedToMe)) return true
        if (!matchesScope(task.scopeLevel, task.zoneId, task.stateId, task.cityId, task.centreId)) {
            return false
        }
        if (task.visibility.equals("restricted", ignoreCase = true)) {
            return task.currentUserParticipantStatus == "active"
        }
        val activeCode = activeRole.code
        if (task.targetRoleCodes.isNotEmpty() && !task.targetRoleCodes.any { it.equals(activeCode, ignoreCase = true) }) {
            // Supervisors above the task scope can still VIEW for oversight
            return activeRole.hierarchyRank >= TaskzRole.CITY_HEAD.hierarchyRank
        }
        return true
    }

    fun canCreateTask(): Boolean = isActiveAccount()

    fun canEditTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        if (isSuperAdmin()) return true
        val uid = _context.value.userId
        if (uid.isNotBlank() && task.createdBy == uid) return true
        if (getActiveRole() == TaskzRole.COMMAND_CENTER) {
            return matchesScope(task.scopeLevel, task.zoneId, task.stateId, task.cityId, task.centreId)
        }
        // E.g. City Head viewing a centre task created by IT Manager gets VIEW-only, NOT edit.
        return false
    }

    fun canAssignTask(task: TaskItem): Boolean = canEditTask(task)

    fun canReassignTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        if (isSuperAdmin()) return true
        return canEditTask(task)
    }

    fun canCompleteTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        if (task.status.equals("cancelled", ignoreCase = true)) return false
        val role = getActiveRole()
        if (isSuperAdmin() || role == TaskzRole.COMMAND_CENTER) return true
        val uid = _context.value.userId
        if (uid.isNotBlank() && (task.isAssignedToMe || task.createdBy == uid)) return true
        if (!matchesScope(task.scopeLevel, task.zoneId, task.stateId, task.cityId, task.centreId)) {
            return false
        }
        if (!task.visibility.equals("everyone", ignoreCase = true)) {
            return task.currentUserParticipantStatus == "active"
        }
        if (task.targetRoleCodes.isNotEmpty()) {
            return task.targetRoleCodes.any { it.equals(role.code, ignoreCase = true) }
        }
        return when (task.scopeLevel) {
            ScopeLevel.GLOBAL -> true
            ScopeLevel.ZONE -> role == TaskzRole.ZOM
            ScopeLevel.STATE -> role in setOf(TaskzRole.ROM, TaskzRole.ZOM)
            ScopeLevel.CITY -> role in setOf(TaskzRole.CITY_HEAD, TaskzRole.VCO)
            ScopeLevel.CENTRE -> role in setOf(TaskzRole.IT_MANAGER, TaskzRole.VCO)
        }
    }

    fun canAcknowledgeTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        if (task.isAcknowledged) return false
        return canCompleteTask(task)
    }

    fun canDeleteTask(task: TaskItem): Boolean {
        if (!isActiveAccount()) return false
        if (isSuperAdmin()) return true
        val uid = _context.value.userId
        return uid.isNotBlank() && task.createdBy == uid
    }

    // ---- SCOPE CREATION AUTHORIZATION ----

    fun canCreateCentreTask(): Boolean = isActiveAccount()

    fun canCreateCityTask(): Boolean = isActiveAccount() && getActiveRole() in setOf(
        TaskzRole.VCO, TaskzRole.CITY_HEAD, TaskzRole.ROM, TaskzRole.ZOM, TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN
    )

    fun canCreateStateTask(): Boolean = isActiveAccount() && getActiveRole() in setOf(
        TaskzRole.ROM, TaskzRole.ZOM, TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN
    )

    fun canCreateZoneTask(): Boolean = isActiveAccount() && getActiveRole() in setOf(
        TaskzRole.ZOM, TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN
    )

    fun canCreateGlobalTask(): Boolean = isActiveAccount() && getActiveRole() in setOf(
        TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN
    )

    fun allowedTaskScopes(): List<ScopeLevel> {
        val list = mutableListOf(ScopeLevel.CENTRE)
        if (canCreateCityTask()) list.add(ScopeLevel.CITY)
        if (canCreateStateTask()) list.add(ScopeLevel.STATE)
        if (canCreateZoneTask()) list.add(ScopeLevel.ZONE)
        if (canCreateGlobalTask()) list.add(ScopeLevel.GLOBAL)
        return list
    }

    // ---- INCIDENT PERMISSIONS ----

    fun canViewIncident(incident: SystemIncident): Boolean {
        if (!isActiveAccount()) return false
        if (isSuperAdmin() || getActiveRole() == TaskzRole.COMMAND_CENTER) return true
        val uid = _context.value.userId
        if (uid.isNotBlank() && (incident.createdBy == uid || incident.assignedTo == uid)) return true
        return matchesScope(
            targetScope = ScopeLevel.fromCode(incident.scopeLevel),
            zoneId = incident.zoneId,
            stateId = incident.stateId,
            cityId = incident.cityId,
            centreId = incident.centreId
        )
    }

    fun canCreateIncident(): Boolean = isActiveAccount()

    fun canEditIncident(incident: SystemIncident): Boolean {
        if (!isActiveAccount()) return false
        if (isSuperAdmin()) return true
        val uid = _context.value.userId
        if (uid.isNotBlank() && (incident.createdBy == uid || incident.assignedTo == uid)) return true
        return getActiveRole().hierarchyRank >= TaskzRole.VCO.hierarchyRank && canViewIncident(incident)
    }

    fun canAssignIncident(incident: SystemIncident): Boolean =
        isActiveAccount() && (isSuperAdmin() || getActiveRole().hierarchyRank >= TaskzRole.VCO.hierarchyRank) && canViewIncident(incident)

    fun canResolveIncident(incident: SystemIncident): Boolean =
        !incident.status.equals("RESOLVED", ignoreCase = true) && canEditIncident(incident)

    fun canEscalateIncident(incident: SystemIncident): Boolean =
        !incident.status.equals("RESOLVED", ignoreCase = true) &&
            !incident.escalationLevel.equals("SUPER_ADMIN", ignoreCase = true) &&
            canViewIncident(incident)

    // ---- USER / ROLE / GROUP GOVERNANCE ----

    fun canSearchUser(): Boolean = isActiveAccount()
    fun canManageUsers(): Boolean = isActiveAccount() && isSuperAdmin()
    fun canManageRoles(): Boolean = isActiveAccount() && isSuperAdmin()
    fun canManageGroups(): Boolean = isActiveAccount() && getActiveRole().hierarchyRank >= TaskzRole.CITY_HEAD.hierarchyRank
}
