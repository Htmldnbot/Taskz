package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AlarmType
import com.example.core.model.AuthorizationContext
import com.example.core.model.CompletionPolicy
import com.example.core.model.LanProgressItem
import com.example.core.model.OrgHierarchyData
import com.example.core.model.ResponseType
import com.example.core.model.ScopeLevel
import com.example.core.model.ScopedMemberSearchResult
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
import com.example.domain.repository.AdminRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.security.TaskzPermissionManager
import com.example.sync.TaskzSyncManager
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
    private val taskRepository: TaskRepository,
    private val adminRepository: AdminRepository = TaskzApplication.instance.adminRepository,
    private val permissionManager: TaskzPermissionManager = TaskzApplication.instance.permissionManager,
    private val taskzSyncManager: TaskzSyncManager = TaskzApplication.instance.taskzSyncManager
) : ViewModel() {

    val authContextFlow: StateFlow<AuthorizationContext> = permissionManager.contextFlow
    val orgHierarchyFlow: StateFlow<OrgHierarchyData> = taskzSyncManager.orgHierarchy

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

    private val _selectedCityFilter = MutableStateFlow<String?>(null)
    val selectedCityFilter: StateFlow<String?> = _selectedCityFilter.asStateFlow()

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

    private val _scopedMembers = MutableStateFlow<List<ScopedMemberSearchResult>>(emptyList())
    val scopedMembers: StateFlow<List<ScopedMemberSearchResult>> = _scopedMembers.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var responseCollectionJob: kotlinx.coroutines.Job? = null
    private var lanCollectionJob: kotlinx.coroutines.Job? = null

    init {
        refreshTasks()
    }

    fun refreshTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.syncTasks()
            res.onFailure { err ->
                _actionEvent.emit("Sync error: ${err.message ?: "Unable to fetch tasks"}")
            }
            _isLoading.value = false
        }
    }

    private data class TaskFilterCriteria(
        val tab: TaskTabFilter,
        val subFilter: TaskSubFilter,
        val cityId: String?,
        val query: String
    )

    private val filterCriteriaFlow = combine(
        _selectedTab,
        _selectedSubFilter,
        _selectedCityFilter,
        _searchQuery
    ) { tab, subFilter, cityId, query ->
        TaskFilterCriteria(tab, subFilter, cityId, query)
    }

    // Filtered tasks state derived from RBAC permission scope, tab, sub-filter, city filter, query, and tasks flow
    val filteredTasks: StateFlow<List<TaskItem>> = combine(
        allTasksFlow,
        filterCriteriaFlow,
        permissionManager.contextFlow,
        orgHierarchyFlow
    ) { tasks, criteria, _, orgHierarchy ->
        val currentUserId = taskRepository.getActiveUserId()
        val scopedTasks = tasks.filter { permissionManager.canViewTask(it) }
        val baseFiltered = when (criteria.tab) {
            TaskTabFilter.MY_TASKS -> scopedTasks.filter {
                it.isAssignedToMe || it.currentUserParticipantStatus == "active" || (currentUserId != null && it.createdBy == currentUserId) || it.targetRoleCodes.contains(permissionManager.getActiveRole().code)
            }
            TaskTabFilter.AVAILABLE -> scopedTasks.filter {
                it.status in setOf("open", "in_progress", "draft") &&
                    it.allowSelfJoin &&
                    !it.isAssignedToMe &&
                    !it.isPendingApproval
            }
            TaskTabFilter.CREATED_BY_ME -> scopedTasks.filter { it.createdBy == currentUserId }
        }

        val subFiltered = when (criteria.subFilter) {
            TaskSubFilter.ALL -> baseFiltered
            TaskSubFilter.TODAY -> baseFiltered.filter { it.status != "completed" && it.status != "cancelled" }
            TaskSubFilter.UPCOMING -> baseFiltered.filter { it.status == "open" || it.status == "draft" }
            TaskSubFilter.COMPLETED -> baseFiltered.filter { it.status == "completed" }
        }

        val cityFiltered = if (criteria.cityId.isNullOrBlank()) {
            subFiltered
        } else {
            val targetCityId = criteria.cityId
            val selectedCityName = orgHierarchy.cities.firstOrNull { it.id == targetCityId }?.name?.lowercase()
            val centreIdsInCity = orgHierarchy.centres
                .filter { it.cityId == targetCityId }
                .map { it.id }
                .toSet()
            subFiltered.filter { task ->
                task.cityId == targetCityId ||
                    (task.centreId != null && task.centreId in centreIdsInCity) ||
                    (!selectedCityName.isNullOrBlank() && task.location?.lowercase()?.contains(selectedCityName) == true)
            }
        }

        if (criteria.query.isBlank()) {
            cityFiltered
        } else {
            val q = criteria.query.trim().lowercase()
            cityFiltered.filter {
                it.title.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true) ||
                (it.groupTitle?.lowercase()?.contains(q) == true) ||
                (it.location?.lowercase()?.contains(q) == true) ||
                it.scopeLevel.label.lowercase().contains(q) ||
                it.targetRoleCodes.any { role -> role.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun allowedTaskScopes(): List<ScopeLevel> = permissionManager.allowedTaskScopes()
    fun canEditTask(task: TaskItem): Boolean = permissionManager.canEditTask(task)
    fun canAssignTask(task: TaskItem): Boolean = permissionManager.canAssignTask(task)
    fun canAcknowledgeTask(task: TaskItem): Boolean = permissionManager.canAcknowledgeTask(task)
    fun canCompleteTask(task: TaskItem): Boolean = permissionManager.canCompleteTask(task)
    fun canDeleteTask(task: TaskItem): Boolean = permissionManager.canDeleteTask(task)

    fun refreshOrgHierarchy() {
        viewModelScope.launch {
            taskzSyncManager.refreshOrgHierarchy()
        }
    }

    fun setTab(tab: TaskTabFilter) {
        _selectedTab.value = tab
    }

    fun setSubFilter(filter: TaskSubFilter) {
        _selectedSubFilter.value = filter
    }

    fun setCityFilter(cityId: String?) {
        _selectedCityFilter.value = cityId
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

            // Collect responses reactively (cancel prior collector when reloading)
            responseCollectionJob?.cancel()
            responseCollectionJob = launch {
                taskRepository.getResponsesForTaskFlow(taskId).collect { resps ->
                    _taskResponses.value = resps
                }
            }

            // Collect LAN progress if task has LAN targets
            lanCollectionJob?.cancel()
            if (updated != null && updated.lanTargetList.isNotEmpty()) {
                lanCollectionJob = launch {
                    taskRepository.getLanProgressFlow(taskId, updated.lanTargetList).collect { lans ->
                        _lanProgress.value = lans
                    }
                }
            } else {
                _lanProgress.value = emptyList()
            }

            // Pull latest responses from server so teammates' responses appear immediately
            launch {
                taskRepository.syncTaskResponses(taskId)
                _selectedTask.value = taskRepository.getTaskById(taskId) ?: _selectedTask.value
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
                    taskRepository.syncTasks()
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
            val current = _selectedTask.value
            if (current != null && current.id == taskId && current.isBeforeScheduledExecution()) {
                _actionEvent.emit("Alarm acknowledgement is locked until the scheduled task time.")
                return@launch
            }
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
            val current = _selectedTask.value
            if (current != null && current.id == taskId && current.isBeforeScheduledExecution()) {
                _actionEvent.emit("Snooze is locked until the scheduled task time.")
                return@launch
            }
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
            val current = _selectedTask.value
            if (current != null && current.id == taskId && current.isBeforeScheduledExecution()) {
                _actionEvent.emit("Task confirmation is locked until the scheduled task time.")
                return@launch
            }
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
                onSuccess = { item ->
                    val reminderMins = (current?.overdueReminderIntervalMinutes ?: current?.repeatIntervalMinutes ?: 10).coerceAtLeast(1)
                    val msg = when {
                        responseType == ResponseType.YES_NO && responseValue.equals("NO", ignoreCase = true) -> {
                            if (item.isPendingSync) {
                                "Response 'NO' saved offline. Reminder scheduled in ${reminderMins}m."
                            } else {
                                "Response 'NO' synced. Follow-up reminder scheduled in ${reminderMins}m."
                            }
                        }
                        item.isPendingSync -> "Offline: Response saved locally & queued for sync"
                        else -> "Response synced to server"
                    }
                    _actionEvent.emit(msg)
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
        scopeLevel: ScopeLevel = ScopeLevel.CENTRE,
        zoneId: String? = null,
        stateId: String? = null,
        cityId: String? = null,
        centreId: String? = null,
        targetRoleCodes: List<String> = emptyList(),
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
                responseFormSchemaJson = responseFormSchemaJson,
                scopeLevel = scopeLevel,
                zoneId = zoneId,
                stateId = stateId,
                cityId = cityId,
                centreId = centreId,
                targetRoleCodes = targetRoleCodes
            )
            res.fold(
                onSuccess = { task ->
                    val msg = if (task.status.equals("pending_sync", ignoreCase = true)) {
                        "Offline: Task saved locally & queued for sync"
                    } else {
                        "Task Created & Synced to Server"
                    }
                    _actionEvent.emit(msg)
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
        selectedEmployeeIds: List<String> = emptyList(),
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
            selectedEmployeeIds = selectedEmployeeIds,
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
            scopeLevel = task.scopeLevel,
            zoneId = task.zoneId,
            stateId = task.stateId,
            cityId = task.cityId,
            centreId = task.centreId,
            targetRoleCodes = task.targetRoleCodes,
            onSuccess = onSuccess
        )
    }

    fun searchEmployees(query: String) {
        viewModelScope.launch {
            _directoryEmployees.value = taskRepository.searchEmployees(query)
        }
    }

    fun searchScopedMembers(
        query: String,
        roleCode: String? = null,
        centreId: String? = null
    ) {
        viewModelScope.launch {
            val rpcRes = adminRepository.searchScopedMembers(query = query, roleCode = roleCode, centreId = centreId)
            val scopedList = rpcRes.getOrNull()
            if (scopedList != null) {
                _scopedMembers.value = scopedList
                _directoryEmployees.value = scopedList.map { m ->
                    UserProfile(
                        id = m.id,
                        employeeId = m.employeeId,
                        fullName = m.fullName,
                        email = m.email,
                        mobile = m.mobile.orEmpty(),
                        activeRoleCode = m.activeRoleCode,
                        assignedRoleCodes = m.assignedRoles.ifEmpty { listOf(m.activeRoleCode) },
                        zoneId = m.zoneId,
                        zoneName = m.zoneName,
                        stateId = m.stateId,
                        stateName = m.stateName,
                        cityId = m.cityId,
                        cityName = m.cityName,
                        centreId = m.centreId,
                        centreCode = m.centreCode,
                        isActive = m.isActive
                    )
                }
            } else {
                val fallback = taskRepository.searchEmployees(query)
                _directoryEmployees.value = fallback
                _scopedMembers.value = fallback.map { u ->
                    ScopedMemberSearchResult(
                        id = u.id,
                        employeeId = u.employeeId,
                        fullName = u.fullName,
                        email = u.email,
                        mobile = u.mobile,
                        activeRoleCode = u.activeRoleCode,
                        assignedRoles = u.assignedRoleCodes,
                        centreId = u.centreId,
                        centreCode = u.centreCode,
                        cityId = u.cityId,
                        cityName = u.cityName,
                        stateId = u.stateId,
                        stateName = u.stateName,
                        zoneId = u.zoneId,
                        zoneName = u.zoneName,
                        isActive = u.isActive
                    )
                }
            }
        }
    }

    fun deleteTask(taskId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = taskRepository.deleteTask(taskId)
            _isLoading.value = false
            if (result.isSuccess) {
                _actionEvent.emit("Task deleted successfully")
                onSuccess()
            } else {
                _actionEvent.emit("Failed to delete task: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun assignUserToTask(taskId: String, userId: String, roleId: String = "staff", onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = taskRepository.assignUserToTask(taskId, userId, roleId)
            _isLoading.value = false
            if (result.isSuccess) {
                _actionEvent.emit("Participant added successfully")
                loadTaskDetails(taskId)
                onSuccess()
            } else {
                _actionEvent.emit("Failed to add participant: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
