package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AlarmType
import com.example.core.model.CompletionPolicy
import com.example.core.model.LanProgressItem
import com.example.core.model.ResponseType
import com.example.core.model.TaskActivityLog
import com.example.core.model.TaskGroup
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskJoinRequest
import com.example.core.model.TaskParticipant
import com.example.core.model.TaskResponseItem
import com.example.core.model.TaskRoleRequirement
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
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

    val taskGroupsFlow: StateFlow<List<TaskGroup>> = taskRepository.getGroupsFlow()
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

    private val _taskResponses = MutableStateFlow<List<TaskResponseItem>>(emptyList())
    val taskResponses: StateFlow<List<TaskResponseItem>> = _taskResponses.asStateFlow()

    private val _lanProgress = MutableStateFlow<List<LanProgressItem>>(emptyList())
    val lanProgress: StateFlow<List<LanProgressItem>> = _lanProgress.asStateFlow()

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
            val q = query.trim().lowercase()
            subFiltered.filter {
                it.title.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true) ||
                (it.groupTitle?.lowercase()?.contains(q) == true) ||
                (it.location?.lowercase()?.contains(q) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

            // Collect responses reactively
            launch {
                taskRepository.getResponsesForTaskFlow(taskId).collect { resps ->
                    _taskResponses.value = resps
                }
            }

            // Collect LAN progress if task has LAN targets
            if (updated != null && updated.lanTargetList.isNotEmpty()) {
                launch {
                    taskRepository.getLanProgressFlow(taskId, updated.lanTargetList).collect { lans ->
                        _lanProgress.value = lans
                    }
                }
            }

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

    fun leaveTask(taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.leaveTask(taskId)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Left task successfully")
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
                    _actionEvent.emit("Participant removed")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to remove participant")
                }
            )
            _isLoading.value = false
        }
    }

    fun startTask(taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.updateTaskStatus(taskId, "in_progress")
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Task marked In Progress")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to start task")
                }
            )
            _isLoading.value = false
        }
    }

    fun submitCompletion(taskId: String, notes: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.submitCompletionRequest(taskId, notes)
            res.fold(
                onSuccess = { msg ->
                    _actionEvent.emit(msg)
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to submit completion")
                }
            )
            _isLoading.value = false
        }
    }

    fun reviewCompletion(requestId: String, confirmed: Boolean, taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.reviewCompletionRequest(requestId, confirmed)
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

    // Phase 3 Operations
    fun acknowledgeAlarm(taskId: String) {
        viewModelScope.launch {
            val res = taskRepository.acknowledgeTaskAlarm(taskId)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Alarm Acknowledged")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to acknowledge alarm")
                }
            )
        }
    }

    fun snoozeAlarm(taskId: String, minutes: Int) {
        viewModelScope.launch {
            val res = taskRepository.snoozeAlarm(taskId, minutes)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Alarm snoozed for $minutes minutes")
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to snooze alarm")
                }
            )
        }
    }

    fun submitTaskResponse(
        taskId: String,
        responseType: ResponseType,
        responseValue: String? = null,
        percentage: Int? = null,
        numeric1: Double? = null,
        numeric2: Double? = null,
        textValue: String? = null,
        lanNumber: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.submitTaskResponse(
                taskId = taskId,
                responseType = responseType,
                responseValue = responseValue,
                percentage = percentage,
                numeric1 = numeric1,
                numeric2 = numeric2,
                textValue = textValue,
                lanNumber = lanNumber
            )
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Response submitted successfully")
                    loadTaskDetails(taskId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to submit response")
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
        taskType: TaskType = TaskType.SIMPLE,
        importance: TaskImportance = TaskImportance.NORMAL,
        urgencyType: TimeUrgency = TimeUrgency.BEFORE_DEADLINE,
        executionTime: String? = null,
        targetPercentage: Int? = 100,
        repeatIntervalMinutes: Int? = null,
        alarmType: AlarmType = AlarmType.SOUND_ALERT,
        advanceRemindersMinutes: List<Int> = listOf(0, 5),
        remindUntilCompleted: Boolean = false,
        overdueReminderIntervalMinutes: Int? = 5,
        maxOverdueReminders: Int? = 3,
        responseType: ResponseType = ResponseType.COMPLETED_NOT_COMPLETED,
        lanTargetList: List<String> = emptyList(),
        completionPolicy: CompletionPolicy = CompletionPolicy.SINGLE_ASSIGNEE,
        responseFormSchemaJson: String? = null,
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
                requirements = requirements,
                taskType = taskType,
                importance = importance,
                urgencyType = urgencyType,
                executionTime = executionTime,
                targetPercentage = targetPercentage,
                repeatIntervalMinutes = repeatIntervalMinutes,
                alarmType = alarmType,
                advanceRemindersMinutes = advanceRemindersMinutes,
                remindUntilCompleted = remindUntilCompleted,
                overdueReminderIntervalMinutes = overdueReminderIntervalMinutes,
                maxOverdueReminders = maxOverdueReminders,
                responseType = responseType,
                lanTargetList = lanTargetList,
                completionPolicy = completionPolicy,
                responseFormSchemaJson = responseFormSchemaJson
            )
            res.fold(
                onSuccess = { task ->
                    _actionEvent.emit("Task Created Successfully with Alarms")
                    onSuccess(task)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create task")
                }
            )
            _isLoading.value = false
        }
    }

    fun createTask(
        task: TaskItem,
        onSuccess: (TaskItem) -> Unit
    ) {
        createTask(
            groupId = task.groupId,
            shiftId = task.shiftId,
            title = task.title,
            description = task.description,
            location = task.location,
            startsAt = task.startsAt,
            dueAt = task.dueAt,
            priority = task.priority,
            visibility = task.visibility,
            selectedEmployeeIds = emptyList(),
            allowSelfJoin = task.allowSelfJoin,
            joinRequiresApproval = task.joinRequiresApproval,
            notifyCreatorOnJoin = task.notifyCreatorOnJoin,
            requiresCompletionConfirmation = task.requiresCompletionConfirmation,
            maxParticipants = task.maxParticipants,
            requirements = emptyList(),
            taskType = task.taskType,
            importance = task.importance,
            urgencyType = task.urgencyType,
            executionTime = task.executionTime,
            targetPercentage = task.targetPercentage,
            repeatIntervalMinutes = task.repeatIntervalMinutes,
            alarmType = task.alarmType,
            advanceRemindersMinutes = task.advanceRemindersMinutes,
            remindUntilCompleted = task.remindUntilCompleted,
            overdueReminderIntervalMinutes = task.overdueReminderIntervalMinutes,
            maxOverdueReminders = task.maxOverdueReminders,
            responseType = task.responseType,
            lanTargetList = task.lanTargetList,
            completionPolicy = task.completionPolicy,
            responseFormSchemaJson = task.responseFormSchemaJson,
            onSuccess = onSuccess
        )
    }

    fun searchEmployees(query: String) {
        viewModelScope.launch {
            _directoryEmployees.value = taskRepository.searchEmployees(query)
        }
    }
}
