package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AdminAuditEntry
import com.example.core.model.OrgHierarchyData
import com.example.core.model.PasswordResetRequest
import com.example.core.model.ScopedMemberSearchResult
import com.example.core.model.UserProfile
import com.example.domain.repository.AdminRepository
import com.example.sync.TaskzSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminActionState {
    object Idle : AdminActionState
    object Loading : AdminActionState
    data class Success(val message: String) : AdminActionState
    data class Error(val message: String) : AdminActionState
}

class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val taskzSyncManager: TaskzSyncManager = TaskzApplication.instance.taskzSyncManager
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users: StateFlow<List<UserProfile>> = _users.asStateFlow()

    private val _scopedMembers = MutableStateFlow<List<ScopedMemberSearchResult>>(emptyList())
    val scopedMembers: StateFlow<List<ScopedMemberSearchResult>> = _scopedMembers.asStateFlow()

    val orgHierarchy: StateFlow<OrgHierarchyData> = taskzSyncManager.orgHierarchy

    private val _auditLogs = MutableStateFlow<List<AdminAuditEntry>>(emptyList())
    val auditLogs: StateFlow<List<AdminAuditEntry>> = _auditLogs.asStateFlow()

    private val _resetRequests = MutableStateFlow<List<PasswordResetRequest>>(emptyList())
    val resetRequests: StateFlow<List<PasswordResetRequest>> = _resetRequests.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(false)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()

    private val _isLoadingRequests = MutableStateFlow(false)
    val isLoadingRequests: StateFlow<Boolean> = _isLoadingRequests.asStateFlow()

    private val _isLoadingAudits = MutableStateFlow(false)
    val isLoadingAudits: StateFlow<Boolean> = _isLoadingAudits.asStateFlow()

    private val _actionState = MutableStateFlow<AdminActionState>(AdminActionState.Idle)
    val actionState: StateFlow<AdminActionState> = _actionState.asStateFlow()

    init {
        loadAllUsers()
        loadResetRequests()
        loadOrgHierarchy()
        loadAuditLogs()
    }

    fun loadAllUsers() {
        viewModelScope.launch {
            _isLoadingUsers.value = true
            val res = adminRepository.getAllUsers()
            _isLoadingUsers.value = false
            res.onSuccess { _users.value = it }
        }
    }

    fun loadOrgHierarchy() {
        viewModelScope.launch {
            taskzSyncManager.refreshOrgHierarchy()
        }
    }

    fun searchScopedMembers(
        query: String = "",
        roleCode: String? = null,
        centreId: String? = null
    ) {
        viewModelScope.launch {
            val res = adminRepository.searchScopedMembers(query = query, roleCode = roleCode, centreId = centreId)
            res.onSuccess { _scopedMembers.value = it }
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch {
            _isLoadingAudits.value = true
            val res = adminRepository.getAdminAuditLogs(limit = 60)
            _isLoadingAudits.value = false
            res.onSuccess { _auditLogs.value = it }
        }
    }

    fun loadResetRequests() {
        viewModelScope.launch {
            _isLoadingRequests.value = true
            val res = adminRepository.getPasswordResetRequests()
            _isLoadingRequests.value = false
            res.onSuccess { _resetRequests.value = it }
        }
    }

    fun updateUserAccess(
        targetUser: UserProfile,
        roleCodes: List<String>,
        activeRoleCode: String?,
        zoneId: String?,
        stateId: String?,
        cityId: String?,
        centreId: String?,
        isActive: Boolean
    ) {
        if (roleCodes.isEmpty()) {
            _actionState.value = AdminActionState.Error("Select at least one role for ${targetUser.fullName}.")
            return
        }
        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.adminUpdateUserAccess(
                targetUserId = targetUser.id,
                roleCodes = roleCodes,
                activeRoleCode = activeRoleCode,
                zoneId = zoneId,
                stateId = stateId,
                cityId = cityId,
                centreId = centreId,
                isActive = isActive
            )
            res.fold(
                onSuccess = {
                    _actionState.value = AdminActionState.Success(
                        "Updated roles & scope for ${targetUser.fullName} (${targetUser.employeeId})"
                    )
                    loadAllUsers()
                    loadAuditLogs()
                    taskzSyncManager.syncNow(forceFull = true)
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Failed to update user access")
                }
            )
        }
    }

    fun toggleUserStatus(profile: UserProfile) {
        val newStatus = !profile.isActive
        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.toggleUserActiveStatus(profile.id, newStatus)
            res.fold(
                onSuccess = {
                    _actionState.value = AdminActionState.Success(
                        "Employee ${profile.employeeId} is now ${if (newStatus) "Active" else "Inactive"}"
                    )
                    loadAllUsers()
                    loadAuditLogs()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Failed to update status")
                }
            )
        }
    }

    fun executeResetPassword(
        requestId: String?,
        employeeId: String,
        temporaryPassword: String
    ) {
        if (temporaryPassword.length < 6) {
            _actionState.value = AdminActionState.Error("Temporary password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.resetEmployeePassword(
                requestId = requestId,
                employeeId = employeeId,
                newTemporaryPassword = temporaryPassword
            )
            res.fold(
                onSuccess = { msg ->
                    _actionState.value = AdminActionState.Success(msg)
                    loadResetRequests()
                    loadAllUsers()
                    loadAuditLogs()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Password reset failed")
                }
            )
        }
    }

    fun upsertOrgCity(
        id: String? = null,
        code: String? = null,
        name: String,
        stateId: String? = null,
        stateName: String? = null,
        zoneId: String? = null,
        zoneName: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (name.isBlank()) {
            _actionState.value = AdminActionState.Error("City name is required.")
            return
        }
        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.upsertOrgCity(
                id = id,
                code = code,
                name = name,
                stateId = stateId,
                stateName = stateName,
                zoneId = zoneId,
                zoneName = zoneName
            )
            res.fold(
                onSuccess = { saved ->
                    _actionState.value = AdminActionState.Success(
                        "Saved city ${saved.name} (${saved.code})"
                    )
                    loadOrgHierarchy()
                    onSuccess()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Failed to save city")
                }
            )
        }
    }

    fun upsertOrgCentre(
        id: String? = null,
        code: String,
        name: String,
        cityId: String? = null,
        districtName: String? = null,
        address: String? = null,
        subHub: String? = null,
        availableLans: List<String> = emptyList(),
        stateName: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        if (code.isBlank() || name.isBlank()) {
            _actionState.value = AdminActionState.Error("Centre code and name are required.")
            return
        }
        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.upsertOrgCentre(
                id = id,
                code = code,
                name = name,
                cityId = cityId,
                districtName = districtName,
                address = address,
                subHub = subHub,
                availableLans = availableLans,
                stateName = stateName
            )
            res.fold(
                onSuccess = { saved ->
                    _actionState.value = AdminActionState.Success(
                        "Saved centre ${saved.code} · ${saved.name} (${saved.resolvedLans().joinToString(", ")})"
                    )
                    loadOrgHierarchy()
                    onSuccess()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Failed to save centre")
                }
            )
        }
    }

    fun clearActionState() {
        _actionState.value = AdminActionState.Idle
    }
}

