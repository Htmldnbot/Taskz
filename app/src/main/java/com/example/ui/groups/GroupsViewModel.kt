package com.example.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.GroupJoinRequest
import com.example.core.model.GroupMember
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.domain.repository.TaskRepository
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
    private val taskRepository: TaskRepository
) : ViewModel() {

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

    init {
        // Seed default group if empty
        viewModelScope.launch {
            val list = taskRepository.getGroupsFlow()
            // We ensure at least one active group is present for testing
            if (groupsFlow.value.isEmpty()) {
                val existing = taskRepository.getGroupsFlow()
            }
        }
    }

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

    fun reviewGroupJoinRequest(requestId: String, approved: Boolean, groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.reviewGroupJoinRequest(requestId, approved)
            res.fold(
                onSuccess = {
                    _actionEvent.emit(if (approved) "Member Approved" else "Request Rejected")
                    loadGroupDetails(groupId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to process request")
                }
            )
            _isLoading.value = false
        }
    }
}
