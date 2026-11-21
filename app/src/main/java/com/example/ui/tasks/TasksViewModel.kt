package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AlarmType
import com.example.core.model.AuthorizationContext
import com.example.core.model.CompletionPolicy
import com.example.core.model.LanProgressItem
import com.example.core.model.OrgCentre
import com.example.core.model.OrgCity
import com.example.core.model.OrgHierarchyData
import com.example.core.model.OrgState
import com.example.core.model.ResponseType
import com.example.core.model.ScopeLevel
import com.example.core.model.ScopedMemberSearchResult
import com.example.core.model.Shift
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

data class TasksFilterState(
    val stateId: String? = null,
    val cityId: String? = null,
    val centreId: String? = null,
    val examName: String? = null,
    val lan: String? = null,
    val shift: String? = null,
    val dateFilter: String = "All"
)

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

    val allShiftsFlow: StateFlow<List<Shift>> = taskRepository.getAllShiftsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWorkRolesFlow: StateFlow<List<WorkRole>> = taskRepository.getActiveWorkRolesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun findShift1Candidates(shifts: List<Shift> = allShiftsFlow.value): List<Shift> {
        val shift1Matches = shifts.filter {
            it.shiftNumber == 1 ||
                it.name.contains("shift 1", ignoreCase = true) ||
                it.name.contains("shift-1", ignoreCase = true) ||
                it.name.trim().endsWith("1")
        }
        return (if (shift1Matches.isNotEmpty()) shift1Matches else shifts)
            .sortedByDescending { TaskItem.parseIsoToMillis(it.startsAt) ?: 0L }
    }

    private val _selectedTab = MutableStateFlow(TaskTabFilter.MY_TASKS)
    val selectedTab: StateFlow<TaskTabFilter> = _selectedTab.asStateFlow()

    private val _selectedSubFilter = MutableStateFlow(TaskSubFilter.ALL)
    val selectedSubFilter: StateFlow<TaskSubFilter> = _selectedSubFilter.asStateFlow()

    private val _tasksFilterState = MutableStateFlow(TasksFilterState())
    val tasksFilterState: StateFlow<TasksFilterState> = _tasksFilterState.asStateFlow()

    val selectedCityFilter: StateFlow<String?> = _tasksFilterState
        .map { it.cityId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    val availableDateOptions: List<String> = listOf("All", "Today", "Yesterday", "Last 7 Days")

    val availableStates: StateFlow<List<OrgState>> = combine(
        allTasksFlow,
        orgHierarchyFlow,
        permissionManager.contextFlow
    ) { tasks, orgHierarchy, _ ->
        val scopedTasks = tasks.filter { permissionManager.canViewTask(it) }
        val stateIdsFromTasks = scopedTasks.mapNotNull { t ->
            t.stateId ?: t.centreId?.let { cid -> orgHierarchy.centres.firstOrNull { it.id == cid }?.stateId }
                ?: t.cityId?.let { cityId -> orgHierarchy.cities.firstOrNull { it.id == cityId }?.stateId }
        }.toSet()

        if (orgHierarchy.states.isNotEmpty()) {
            val matching = orgHierarchy.states.filter { it.id in stateIdsFromTasks }
            (if (matching.isNotEmpty()) matching else orgHierarchy.states).sortedBy { it.name }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCities: StateFlow<List<OrgCity>> = combine(
        _tasksFilterState,
        orgHierarchyFlow
    ) { filter, orgHierarchy ->
        orgHierarchy.cities
            .filter { filter.stateId == null || it.stateId == filter.stateId }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCentres: StateFlow<List<OrgCentre>> = combine(
        _tasksFilterState,
        orgHierarchyFlow
    ) { filter, orgHierarchy ->
        orgHierarchy.centres
            .filter { c ->
                (filter.stateId == null || c.stateId == filter.stateId) &&
                    (filter.cityId == null || c.cityId == filter.cityId)
            }
            .sortedBy { it.code }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableExams: StateFlow<List<String>> = combine(
        allTasksFlow,
        taskGroupsFlow,
        _tasksFilterState,
        orgHierarchyFlow,
        permissionManager.contextFlow
    ) { tasks, groups, filter, orgHierarchy, _ ->
        val fromTasks = tasks.asSequence()
            .filter { permissionManager.canViewTask(it) }
            .filter { matchesUpstreamScope(it, filter, orgHierarchy) }
            .mapNotNull { extractTaskExamName(it, groups) }
            .toList()
        val fromGroups = if (filter.stateId == null && filter.cityId == null && filter.centreId == null) {
            groups.mapNotNull { it.title.trim().takeIf { title -> title.isNotBlank() } }
        } else {
            emptyList()
        }
        (fromTasks + fromGroups)
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real LANs only — derived exclusively from existing tasks inside upstream State -> City -> Centre filter
    val availableLans: StateFlow<List<String>> = combine(
        allTasksFlow,
        _tasksFilterState,
        orgHierarchyFlow,
        permissionManager.contextFlow
    ) { tasks, filter, orgHierarchy, _ ->
        tasks.asSequence()
            .filter { permissionManager.canViewTask(it) }
            .filter { matchesUpstreamScope(it, filter, orgHierarchy) }
            .flatMap { extractTaskLans(it).asSequence() }
            .distinct()
            .sortedWith(compareBy({ it.removePrefix("LAN ").trim().toIntOrNull() ?: Int.MAX_VALUE }, { it }))
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableShifts: StateFlow<List<String>> = combine(
        allTasksFlow,
        taskGroupsFlow,
        _tasksFilterState,
        orgHierarchyFlow,
        permissionManager.contextFlow
    ) { tasks, groups, filter, orgHierarchy, _ ->
        tasks.asSequence()
            .filter { permissionManager.canViewTask(it) }
            .filter { matchesUpstreamScope(it, filter, orgHierarchy) }
            .filter {
                filter.examName.isNullOrBlank() ||
                    extractTaskExamName(it, groups)?.equals(filter.examName, ignoreCase = true) == true
            }
            .mapNotNull { it.shiftName?.trim()?.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .sorted()
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun repeatShift1ToShift2() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.repeatShift1ToShift2()
            res.fold(
                onSuccess = { shift ->
                    _actionEvent.emit("Shift 1 tasks repeated to ${shift.name} ✓")
                    taskRepository.syncTasks()
                },
                onFailure = { err ->
                    _actionEvent.emit(err.message ?: "Unable to repeat Shift 1 tasks")
                }
            )
            _isLoading.value = false
        }
    }

    private data class TaskFilterCriteria(
        val tab: TaskTabFilter,
        val subFilter: TaskSubFilter,
        val cascadeFilter: TasksFilterState,
        val query: String
    )

    private val filterCriteriaFlow = combine(
        _selectedTab,
        _selectedSubFilter,
        _tasksFilterState,
        _searchQuery
    ) { tab, subFilter, cascadeFilter, query ->
        TaskFilterCriteria(tab, subFilter, cascadeFilter, query)
    }

    // Filtered tasks state derived from RBAC permission scope, tab, sub-filter, cascading filters, query, and tasks flow
    val filteredTasks: StateFlow<List<TaskItem>> = combine(
        allTasksFlow,
        taskGroupsFlow,
        filterCriteriaFlow,
        permissionManager.contextFlow,
        orgHierarchyFlow
    ) { tasks, groups, criteria, _, orgHierarchy ->
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

        val f = criteria.cascadeFilter
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusDays(6)

        val cascadeFiltered = subFiltered.filter { task ->
            val matchesScope = matchesUpstreamScope(task, f, orgHierarchy)
            val matchesExam = f.examName.isNullOrBlank() ||
                extractTaskExamName(task, groups)?.equals(f.examName, ignoreCase = true) == true
            val matchesLan = f.lan.isNullOrBlank() || extractTaskLans(task).any { it.equals(f.lan, ignoreCase = true) }
            val matchesShift = f.shift.isNullOrBlank() ||
                task.shiftName?.equals(f.shift, ignoreCase = true) == true ||
                task.shiftName?.contains(f.shift, ignoreCase = true) == true
            val matchesDate = when (f.dateFilter) {
                "Today" -> extractTaskLocalDate(task) == today
                "Yesterday" -> extractTaskLocalDate(task) == yesterday
                "Last 7 Days" -> {
                    val d = extractTaskLocalDate(task)
                    d != null && !d.isBefore(weekStart) && !d.isAfter(today)
                }
                else -> true
            }
            matchesScope && matchesExam && matchesLan && matchesShift && matchesDate
        }

        if (criteria.query.isBlank()) {
            cascadeFiltered
        } else {
            val q = criteria.query.trim().lowercase()
            cascadeFiltered.filter {
                it.title.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true) ||
                    (it.groupTitle?.lowercase()?.contains(q) == true) ||
                    (it.location?.lowercase()?.contains(q) == true) ||
                    it.scopeLevel.label.lowercase().contains(q) ||
                    it.targetRoleCodes.any { role -> role.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun matchesUpstreamScope(
        task: TaskItem,
        f: TasksFilterState,
        orgHierarchy: OrgHierarchyData
    ): Boolean {
        val matchesState = if (f.stateId.isNullOrBlank()) {
            true
        } else {
            val selectedStateName = orgHierarchy.states.firstOrNull { it.id == f.stateId }?.name?.lowercase()
            val cityIdsInState = orgHierarchy.cities.filter { it.stateId == f.stateId }.map { it.id }.toSet()
            val centreIdsInState = orgHierarchy.centres.filter { it.stateId == f.stateId }.map { it.id }.toSet()
            task.stateId == f.stateId ||
                (task.cityId != null && task.cityId in cityIdsInState) ||
                (task.centreId != null && task.centreId in centreIdsInState) ||
                (!selectedStateName.isNullOrBlank() && task.location?.lowercase()?.contains(selectedStateName) == true)
        }

        val matchesCity = if (f.cityId.isNullOrBlank()) {
            true
        } else {
            val selectedCityName = orgHierarchy.cities.firstOrNull { it.id == f.cityId }?.name?.lowercase()
            val centreIdsInCity = orgHierarchy.centres.filter { it.cityId == f.cityId }.map { it.id }.toSet()
            task.cityId == f.cityId ||
                (task.centreId != null && task.centreId in centreIdsInCity) ||
                (!selectedCityName.isNullOrBlank() && task.location?.lowercase()?.contains(selectedCityName) == true)
        }

        val matchesCentre = if (f.centreId.isNullOrBlank()) {
            true
        } else {
            val centre = orgHierarchy.centres.firstOrNull { it.id == f.centreId }
            task.centreId == f.centreId ||
                (centre != null && (
                    task.location?.contains(centre.code, ignoreCase = true) == true ||
                        task.location?.contains(centre.name, ignoreCase = true) == true
                    ))
        }

        return matchesState && matchesCity && matchesCentre
    }

    private fun extractTaskLans(task: TaskItem): List<String> {
        val result = mutableListOf<String>()
        result.addAll(task.lanTargetList.map { it.trim() }.filter { it.isNotBlank() && !it.equals("All LANs", ignoreCase = true) })
        val loc = task.location
        if (!loc.isNullOrBlank() && loc.startsWith("LAN:", ignoreCase = true)) {
            val lanSection = loc.substringAfter(":", "").substringBefore("•").trim()
            lanSection.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals("All LANs", ignoreCase = true) }
                .forEach { if (it !in result) result.add(it) }
        }
        return result.distinct()
    }

    private fun extractTaskExamName(task: TaskItem, groups: List<TaskGroup>): String? {
        return task.groupTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: groups.firstOrNull { it.id == task.groupId }?.title?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractTaskLocalDate(task: TaskItem): LocalDate? {
        val raw = task.executionTime ?: task.startsAt ?: task.dueAt ?: task.createdAt
        val millis = TaskItem.parseIsoToMillis(raw) ?: return null
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

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

    fun setStateFilter(stateId: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(
            stateId = stateId,
            cityId = null,
            centreId = null,
            lan = null
        )
    }

    fun setCityFilter(cityId: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(
            cityId = cityId,
            centreId = null,
            lan = null
        )
    }

    fun setCentreFilter(centreId: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(
            centreId = centreId,
            lan = null
        )
    }

    fun setExamFilter(examName: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(examName = examName)
    }

    fun setLanFilter(lan: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(lan = lan)
    }

    fun setShiftFilter(shift: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(shift = shift)
    }

    fun setDateFilter(dateFilter: String?) {
        _tasksFilterState.value = _tasksFilterState.value.copy(
            dateFilter = dateFilter?.takeIf { it.isNotBlank() } ?: "All"
        )
    }

    fun clearAllFilters() {
        _selectedSubFilter.value = TaskSubFilter.ALL
        _tasksFilterState.value = TasksFilterState()
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

            responseCollectionJob?.cancel()
            responseCollectionJob = launch {
                taskRepository.getResponsesForTaskFlow(taskId).collect { resps ->
                    _taskResponses.value = resps
                }
            }

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

            launch {
                taskRepository.syncTaskResponses(taskId)
                _selectedTask.value = taskRepository.getTaskById(taskId) ?: _selectedTask.value
            }

            _isLoading.value = false
        }
    }

    fun joinTask(taskId: String, roleId: String, message: String?) {
        viewModelScope.launch {
            if (permissionManager.isOnLeave()) {
                _actionEvent.emit("You are currently On Leave. Switch to On Duty to join tasks.")
                return@launch
            }
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
        lanNumber: String? = null,
        nextReminderMinutes: Int? = null
    ) {
        viewModelScope.launch {
            val current = _selectedTask.value
            if (current != null && current.id == taskId && current.isBeforeScheduledExecution()) {
                _actionEvent.emit("Task confirmation is locked until the scheduled task time.")
                return@launch
            }
            val effectiveResponseType = if (current?.taskType == TaskType.PROGRESS) {
                ResponseType.PERCENTAGE
            } else {
                responseType
            }
            _isLoading.value = true
            val res = taskRepository.submitTaskResponse(
                taskId = taskId,
                responseType = effectiveResponseType,
                responseValue = responseValue,
                percentage = percentage,
                numeric1 = numeric1,
                numeric2 = numeric2,
                textValue = textValue,
                lanNumber = lanNumber,
                nextReminderMinutes = nextReminderMinutes
            )
            res.fold(
                onSuccess = { item ->
                    val reminderMins = (current?.overdueReminderIntervalMinutes ?: current?.repeatIntervalMinutes ?: 10).coerceAtLeast(1)
                    val targetPct = (current?.targetPercentage ?: 100).coerceAtLeast(1)
                    val msg = when {
                        percentage != null && percentage >= targetPct -> "Task 100% completed"
                        percentage != null && nextReminderMinutes != null -> "Saved $percentage% • Next reminder in ${nextReminderMinutes}m"
                        effectiveResponseType == ResponseType.YES_NO && responseValue.equals("NO", ignoreCase = true) ->
                            "Saved NO • Reminder in ${reminderMins}m"
                        item.isPendingSync -> "Saved offline • Queued for sync"
                        else -> "Response saved"
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

    fun updateTaskDetails(
        taskId: String,
        title: String,
        description: String?,
        location: String?,
        executionTime: String?,
        dueAt: String?,
        priority: String,
        importance: TaskImportance,
        alarmType: AlarmType,
        overdueReminderIntervalMinutes: Int,
        targetPercentage: Int,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.updateTaskDetails(
                taskId = taskId,
                title = title,
                description = description,
                location = location,
                executionTime = executionTime,
                dueAt = dueAt,
                priority = priority,
                importance = importance,
                alarmType = alarmType,
                overdueReminderIntervalMinutes = overdueReminderIntervalMinutes,
                targetPercentage = targetPercentage
            )
            _isLoading.value = false
            res.fold(
                onSuccess = { updated ->
                    _selectedTask.value = updated
                    _actionEvent.emit("Task updated")
                    loadTaskDetails(taskId)
                    onSuccess()
                },
                onFailure = { err ->
                    _actionEvent.emit(err.message ?: "Failed to update task")
                }
            )
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
        centreId: String? = null,
        stateId: String? = null,
        cityId: String? = null
    ) {
        viewModelScope.launch {
            val rpcRes = adminRepository.searchScopedMembers(
                query = query,
                roleCode = roleCode,
                centreId = centreId,
                stateId = stateId,
                cityId = cityId
            )
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
                        isActive = m.isActive,
                        isOnLeave = m.isOnLeave
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
                        isActive = u.isActive,
                        isOnLeave = u.isOnLeave
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
