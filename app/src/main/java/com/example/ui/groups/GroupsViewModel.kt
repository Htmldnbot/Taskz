package com.example.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.GroupJoinRequest
import com.example.core.model.GroupMember
import com.example.core.model.ScopeLevel
import com.example.core.model.ScopedMemberSearchResult
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.TaskzRole
import com.example.core.model.AuthorizationContext
import com.example.domain.repository.AdminRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.security.TaskzPermissionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(
    private val taskRepository: TaskRepository,
    private val adminRepository: AdminRepository = TaskzApplication.instance.adminRepository,
    private val permissionManager: TaskzPermissionManager = TaskzApplication.instance.permissionManager
) : ViewModel() {

    val authContextFlow: StateFlow<AuthorizationContext> = permissionManager.contextFlow

    fun canManageGroups(): Boolean = permissionManager.canManageGroups()
    fun canSelfJoinGroups(): Boolean = permissionManager.canSelfJoinGroups()
    fun canDeleteGroup(group: TaskGroup): Boolean {
        val ctx = permissionManager.getCurrentContext()
        return canManageGroups() || group.createdBy == ctx.userId || ctx.activeRole in setOf(
            TaskzRole.SUPER_ADMIN,
            TaskzRole.COMMAND_CENTER,
            TaskzRole.ZOM,
            TaskzRole.ROM,
            TaskzRole.CITY_HEAD,
            TaskzRole.VCO
        )
    }

    val allTasksFlow: StateFlow<List<TaskItem>> = taskRepository.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scopedDirectoryMembers = MutableStateFlow<List<ScopedMemberSearchResult>>(emptyList())
    val scopedDirectoryMembers: StateFlow<List<ScopedMemberSearchResult>> = _scopedDirectoryMembers.asStateFlow()

    val groupsFlow: StateFlow<List<TaskGroup>> = taskRepository.getGroupsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroup = MutableStateFlow<TaskGroup?>(null)
    val selectedGroup: StateFlow<TaskGroup?> = _selectedGroup.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<GroupMember>>(emptyList())
    val groupMembers: StateFlow<List<GroupMember>> = _groupMembers.asStateFlow()

    private val _groupJoinRequests = MutableStateFlow<List<GroupJoinRequest>>(emptyList())
    val groupJoinRequests: StateFlow<List<GroupJoinRequest>> = _groupJoinRequests.asStateFlow()

    private val _groupShifts = MutableStateFlow<List<Shift>>(emptyList())
    val groupShifts: StateFlow<List<Shift>> = _groupShifts.asStateFlow()

    private val _groupTasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val groupTasks: StateFlow<List<TaskItem>> = _groupTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    fun selectGroup(group: TaskGroup) {
        _selectedGroup.value = group
        loadGroupDetails(group.id)
    }

    fun loadGroupDetails(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val updated = taskRepository.getGroupById(groupId)
            _selectedGroup.value = updated
            _groupMembers.value = taskRepository.getGroupMembers(groupId)
            _groupJoinRequests.value = taskRepository.getGroupJoinRequests(groupId)
            _isLoading.value = false
        }
    }

    fun createGroup(
        title: String,
        code: String?,
        description: String?,
        startDate: String?,
        endDate: String?,
        visibility: String,
        allowSelfJoin: Boolean,
        joinRequiresApproval: Boolean,
        notifyCreatorOnJoin: Boolean,
        maxMembers: Int?,
        onSuccess: (TaskGroup) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.createGroup(
                title = title,
                code = code,
                description = description,
                startDate = startDate,
                endDate = endDate,
                visibility = visibility,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                maxMembers = maxMembers
            )
            res.fold(
                onSuccess = { group ->
                    _actionEvent.emit("Group Created Successfully")
                    onSuccess(group)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create group")
                }
            )
            _isLoading.value = false
        }
    }

    fun updateGroup(
        groupId: String,
        title: String,
        code: String?,
        description: String?,
        startDate: String?,
        endDate: String?,
        status: String,
        visibility: String,
        allowSelfJoin: Boolean,
        joinRequiresApproval: Boolean,
        notifyCreatorOnJoin: Boolean,
        maxMembers: Int?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.updateGroup(
                groupId = groupId,
                title = title,
                code = code,
                description = description,
                startDate = startDate,
                endDate = endDate,
                status = status,
                visibility = visibility,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                maxMembers = maxMembers
            )
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Group Updated Successfully")
                    loadGroupDetails(groupId)
                    onSuccess()
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to update group")
                }
            )
            _isLoading.value = false
        }
    }

    fun joinGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.joinGroup(groupId)
            res.fold(
                onSuccess = { msg ->
                    _actionEvent.emit(msg)
                    loadGroupDetails(groupId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to join group")
                }
            )
            _isLoading.value = false
        }
    }

    fun joinGroupTasksBulk(groupId: String) {
        viewModelScope.launch {
            if (permissionManager.isOnLeave()) {
                _actionEvent.emit("You are On Leave. Turn off On Leave to join tasks.")
                return@launch
            }
            _isLoading.value = true
            val res = taskRepository.joinGroupTasksBulk(groupId)
            res.fold(
                onSuccess = { count ->
                    _actionEvent.emit(
                        if (count > 0) "Joined exam group & $count tasks" else "Joined exam group"
                    )
                    loadGroupDetails(groupId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to join exam group tasks")
                }
            )
            _isLoading.value = false
        }
    }

    fun reviewGroupJoinRequest(requestId: String, approved: Boolean, groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.reviewGroupJoinRequest(requestId, approved)
            res.fold(
                onSuccess = {
                    _actionEvent.emit(if (approved) "Member Approved & Group Tasks Synced" else "Request Rejected")
                    loadGroupDetails(groupId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to process request")
                }
            )
            _isLoading.value = false
        }
    }

    fun loadScopedDirectoryMembers(query: String = "") {
        viewModelScope.launch {
            val ctx = permissionManager.getCurrentContext()
            val scope = ctx.scope
            val role = ctx.activeRole
            val useGlobal = role in setOf(TaskzRole.SUPER_ADMIN, TaskzRole.COMMAND_CENTER)
            val stateFilter = if (!useGlobal && scope.effectiveScopeLevel.rank <= ScopeLevel.STATE.rank) scope.stateId else null
            val cityFilter = if (!useGlobal && scope.effectiveScopeLevel.rank <= ScopeLevel.CITY.rank) scope.cityId else null
            val centreFilter = if (!useGlobal && scope.effectiveScopeLevel == ScopeLevel.CENTRE) scope.centreId else null

            val res = adminRepository.searchScopedMembers(
                query = query,
                roleCode = null,
                centreId = centreFilter,
                stateId = stateFilter,
                cityId = cityFilter
            )
            res.onSuccess { list ->
                _scopedDirectoryMembers.value = list.filter { it.isActive }
            }
        }
    }

    fun addGroupMembersAndSyncTasks(
        groupId: String,
        members: List<ScopedMemberSearchResult>,
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.addGroupMembersAndSyncTasks(groupId, members)
            res.fold(
                onSuccess = { added ->
                    _actionEvent.emit("Added $added member(s) & synced active group tasks")
                    loadGroupDetails(groupId)
                    onSuccess(added)
                },
                onFailure = { err ->
                    _actionEvent.emit(err.message ?: "Failed to add group members")
                }
            )
            _isLoading.value = false
        }
    }

    fun assignTasksToGroupAndSync(
        groupId: String,
        taskIds: List<String>,
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.assignTasksToGroupAndSync(groupId, taskIds)
            res.fold(
                onSuccess = { count ->
                    _actionEvent.emit("Assigned $count task(s) to all group members")
                    loadGroupDetails(groupId)
                    onSuccess(count)
                },
                onFailure = { err ->
                    _actionEvent.emit(err.message ?: "Failed to assign tasks to group")
                }
            )
            _isLoading.value = false
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.deleteGroup(groupId)
            res.fold(
                onSuccess = {
                    if (_selectedGroup.value?.id == groupId) {
                        _selectedGroup.value = null
                    }
                    _actionEvent.emit("Exam group deleted")
                    onSuccess()
                },
                onFailure = { err ->
                    _actionEvent.emit(err.message ?: "Failed to delete exam group")
                }
            )
            _isLoading.value = false
        }
    }
}
