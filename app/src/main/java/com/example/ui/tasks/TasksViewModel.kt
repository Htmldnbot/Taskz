package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.TaskActivityLog
import com.example.core.model.TaskItem
import com.example.core.model.TaskJoinRequest
import com.example.core.model.TaskParticipant
import com.example.core.model.TaskRoleRequirement
import com.example.core.model.UserProfile
import com.example.core.model.WorkRole
import com.example.domain.repository.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskTabFilter {
    MY_TASKS,
    AVAILABLE,
    CREATED_BY_ME
}

enum class TaskSubFilter {
    ALL,
    TODAY,
    UPCOMING,
    COMPLETED
}

class TasksViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val allTasksFlow: StateFlow<List<TaskItem>> = taskRepository.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkRolesFlow: StateFlow<List<WorkRole>> = taskRepository.getActiveWorkRolesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(TaskTabFilter.MY_TASKS)
    val selectedTab: StateFlow<TaskTabFilter> = _selectedTab.asStateFlow()

    private val _selectedSubFilter = MutableStateFlow(TaskSubFilter.ALL)
    val selectedSubFilter: StateFlow<TaskSubFilter> = _selectedSubFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTask = MutableStateFlow<TaskItem?>(null)
    val selectedTask: StateFlow<TaskItem?> = _selectedTask.asStateFlow()

    private val _taskRequirements = MutableStateFlow<List<TaskRoleRequirement>>(emptyList())
    val taskRequirements: StateFlow<List<TaskRoleRequirement>> = _taskRequirements.asStateFlow()

    private val _taskParticipants = MutableStateFlow<List<TaskParticipant>>(emptyList())
    val taskParticipants: StateFlow<List<TaskParticipant>> = _taskParticipants.asStateFlow()

    private val _taskJoinRequests = MutableStateFlow<List<TaskJoinRequest>>(emptyList())
    val taskJoinRequests: StateFlow<List<TaskJoinRequest>> = _taskJoinRequests.asStateFlow()

    private val _taskActivityLog = MutableStateFlow<List<TaskActivityLog>>(emptyList())
    val taskActivityLog: StateFlow<List<TaskActivityLog>> = _taskActivityLog.asStateFlow()

    private val _directoryEmployees = MutableStateFlow<List<UserProfile>>(emptyList())
    val directoryEmployees: StateFlow<List<UserProfile>> = _directoryEmployees.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Filtered tasks state derived from tab, sub-filter, query, and tasks flow
    val filteredTasks: StateFlow<List<TaskItem>> = combine(
        allTasksFlow,
        _selectedTab,
        _selectedSubFilter,
        _searchQuery
    ) { tasks, tab, subFilter, query ->
        val currentUserId = taskRepository.getActiveUserId()
        val baseFiltered = when (tab) {
            TaskTabFilter.MY_TASKS -> tasks.filter { it.isAssignedToMe || it.currentUserParticipantStatus == "active" }
            TaskTabFilter.AVAILABLE -> tasks.filter {
                it.status == "open" && it.allowSelfJoin && !it.isAssignedToMe && it.createdBy != currentUserId
            }
            TaskTabFilter.CREATED_BY_ME -> tasks.filter { it.createdBy == currentUserId }
        }

        val subFiltered = when (subFilter) {
            TaskSubFilter.ALL -> baseFiltered
            TaskSubFilter.TODAY -> baseFiltered.filter { it.status != "completed" && it.status != "cancelled" }
            TaskSubFilter.UPCOMING -> baseFiltered.filter { it.status == "open" || it.status == "draft" }
            TaskSubFilter.COMPLETED -> baseFiltered.filter { it.status == "completed" }
        }

        if (query.isBlank()) {
            subFiltered
        } else {
            subFiltered.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.groupTitle?.contains(query, ignoreCase = true) == true ||
                        it.location?.contains(query, ignoreCase = true) == true ||
                        it.priority.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            taskRepository.fetchWorkRoles()
            searchEmployees("")
        }
    }

    fun setTab(tab: TaskTabFilter) {
        _selectedTab.value = tab
    }

    fun setSubFilter(filter: TaskSubFilter) {
        _selectedSubFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTask(task: TaskItem) {
        _selectedTask.value = task
        loadTaskDetails(task.id)
    }

    fun loadTaskDetails(taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val updated = taskRepository.getTaskById(taskId)
            _selectedTask.value = updated
            _taskRequirements.value = taskRepository.getTaskRoleRequirements(taskId)
            _taskParticipants.value = taskRepository.getTaskParticipants(taskId)
            _taskJoinRequests.value = taskRepository.getTaskJoinRequests(taskId)
            _taskActivityLog.value = taskRepository.getTaskActivityLog(taskId)
            _isLoading.value = false
        }
    }

    fun joinTask(taskId: String, roleId: String, message: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.joinTask(taskId, roleId, message)
            res.fold(
                onSuccess = { msg ->
                    _actionEvent.emit(msg)
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to join task")
                }
            )
            _isLoading.value = false
        }
    }

    fun reviewJoinRequest(requestId: String, approved: Boolean, taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.reviewTaskJoinRequest(requestId, approved)
            res.fold(
                onSuccess = {
                    _actionEvent.emit(if (approved) "Request Approved" else "Request Rejected")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to process request")
                }
            )
            _isLoading.value = false
        }
    }

    fun assignEmployee(taskId: String, userId: String, roleId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.assignEmployee(taskId, userId, roleId)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Employee Assigned Successfully")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to assign employee")
                }
            )
            _isLoading.value = false
        }
    }

    fun leaveTask(taskId: String, reason: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.leaveTask(taskId, reason)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Left Task")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to leave task")
                }
            )
            _isLoading.value = false
        }
    }

    fun removeParticipant(participantId: String, reason: String?, taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.removeParticipant(participantId, reason)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Participant Removed")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to remove participant")
                }
            )
            _isLoading.value = false
        }
    }

    fun completeTask(taskId: String, note: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.completeTask(taskId, note)
            res.fold(
                onSuccess = { msg ->
                    _actionEvent.emit(msg)
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to complete task")
                }
            )
            _isLoading.value = false
        }
    }

    fun reviewCompletion(taskId: String, confirmed: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.reviewTaskCompletion(taskId, confirmed)
            res.fold(
                onSuccess = {
                    _actionEvent.emit(if (confirmed) "Task Completion Confirmed" else "Returned for Revision")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to review completion")
                }
            )
            _isLoading.value = false
        }
    }

    fun createTask(
        groupId: String,
        shiftId: String?,
        title: String,
        description: String?,
        location: String?,
        startsAt: String?,
        dueAt: String?,
        priority: String,
        visibility: String,
        selectedEmployeeIds: List<String>,
        allowSelfJoin: Boolean,
        joinRequiresApproval: Boolean,
        notifyCreatorOnJoin: Boolean,
        requiresCompletionConfirmation: Boolean,
        maxParticipants: Int?,
        requirements: List<Pair<String, Int>>,
        onSuccess: (TaskItem) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.createTask(
                groupId = groupId,
                shiftId = shiftId,
                title = title,
                description = description,
                location = location,
                startsAt = startsAt,
                dueAt = dueAt,
                priority = priority,
                visibility = visibility,
                selectedEmployeeIds = selectedEmployeeIds,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                requiresCompletionConfirmation = requiresCompletionConfirmation,
                maxParticipants = maxParticipants,
                requirements = requirements
            )
            res.fold(
                onSuccess = { task ->
                    _actionEvent.emit("Task Created Successfully")
                    onSuccess(task)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create task")
                }
            )
            _isLoading.value = false
        }
    }

    fun searchEmployees(query: String) {
        viewModelScope.launch {
            _directoryEmployees.value = taskRepository.searchEmployees(query)
        }
    }
}
