package com.example.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AlarmType
import com.example.core.model.AuthorizationContext
import com.example.core.model.CompletionPolicy
import com.example.core.model.ResponseType
import com.example.core.model.Shift
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
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
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ExamChecklistTemplateItem(
    val key: String,
    val title: String,
    val offsetMinutes: Int,
    val shift1Only: Boolean = false
)

val STANDARD_EXAM_CHECKLIST: List<ExamChecklistTemplateItem> = listOf(
    ExamChecklistTemplateItem("drive_download", "Drive Downloaded?", 20, shift1Only = false),
    ExamChecklistTemplateItem("backup_configured", "Backup Configured?", 25, shift1Only = true),
    ExamChecklistTemplateItem("launching_10", "10% Launching Done?", 45, shift1Only = true),
    ExamChecklistTemplateItem("rd_login_100", "100% RD Login Done?", 60, shift1Only = true),
    ExamChecklistTemplateItem("launching_100", "100% Launching Done?", 75, shift1Only = true),
    ExamChecklistTemplateItem("candidate_reg", "Candidate Registration Started?", 100, shift1Only = false),
    ExamChecklistTemplateItem("qp_download", "Question Paper Downloaded?", 120, shift1Only = false),
    ExamChecklistTemplateItem("process_bundle", "Process Bundle Done?", 151, shift1Only = false),
    ExamChecklistTemplateItem("allow_candidate", "Allow Candidate Done?", 170, shift1Only = false),
    ExamChecklistTemplateItem("exam_started", "Exam Started?", 181, shift1Only = false),
    ExamChecklistTemplateItem("paper_verification", "Paper Verification Done?", 230, shift1Only = false)
)

data class TaskCopyPreview(
    val taskId: String,
    val title: String,
    val sourceTime: String,
    val newTime: String,
    val isShift1Only: Boolean,
    val defaultSelected: Boolean
)

class ShiftsViewModel(
    private val taskRepository: TaskRepository,
    private val permissionManager: TaskzPermissionManager = TaskzApplication.instance.permissionManager
) : ViewModel() {

    val authContextFlow: StateFlow<AuthorizationContext> = permissionManager.contextFlow

    val allShiftsFlow: StateFlow<List<Shift>> = taskRepository.getAllShiftsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun canManageShifts(): Boolean = permissionManager.canManageGroups()
    fun canSelfJoinShifts(): Boolean = permissionManager.canSelfJoinGroups()

    private val _currentGroupId = MutableStateFlow<String>("")

    private val _selectedShift = MutableStateFlow<Shift?>(null)
    val selectedShift: StateFlow<Shift?> = _selectedShift.asStateFlow()

    private val _shiftTasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val shiftTasks: StateFlow<List<TaskItem>> = _shiftTasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    fun getShiftsForGroup(groupId: String): StateFlow<List<Shift>> {
        _currentGroupId.value = groupId
        return taskRepository.getShiftsForGroupFlow(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun selectShift(shift: Shift) {
        _selectedShift.value = shift
        loadShiftDetails(shift.id)
    }

    fun loadShiftDetails(shiftId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val updated = taskRepository.getShiftById(shiftId)
            _selectedShift.value = updated
            _isLoading.value = false
        }
    }

    fun createShift(
        groupId: String,
        name: String,
        shiftNumber: Int?,
        startsAt: String,
        endsAt: String,
        notes: String?,
        onSuccess: (Shift) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.createShift(
                groupId = groupId,
                name = name,
                shiftNumber = shiftNumber,
                startsAt = startsAt,
                endsAt = endsAt,
                notes = notes
            )
            res.fold(
                onSuccess = { shift ->
                    _actionEvent.emit("Shift Created")
                    onSuccess(shift)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create shift")
                }
            )
            _isLoading.value = false
        }
    }

    fun createChecklistTasksForShift(
        groupId: String,
        shiftId: String,
        anchorTimeMillis: Long,
        lanTags: List<String>,
        selectedTemplates: List<ExamChecklistTemplateItem>,
        onComplete: (createdCount: Int, totalCount: Int) -> Unit = { _, _ -> }
    ) {
        if (selectedTemplates.isEmpty()) {
            onComplete(0, 0)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val scope = permissionManager.getUserScope()
            val cleanedLans = lanTags.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val locationLabel = cleanedLans.takeIf { it.isNotEmpty() }?.let { "LAN: ${it.joinToString(", ")}" }
            var created = 0
            for (item in selectedTemplates) {
                val targetMs = anchorTimeMillis + (item.offsetMinutes * 60_000L)
                val targetIso = Instant.ofEpochMilli(targetMs).toString()
                val res = taskRepository.createTask(
                    groupId = groupId,
                    shiftId = shiftId,
                    title = item.title,
                    description = null,
                    location = locationLabel,
                    startsAt = targetIso,
                    dueAt = targetIso,
                    priority = "high",
                    visibility = "everyone",
                    selectedEmployeeIds = emptyList(),
                    allowSelfJoin = true,
                    joinRequiresApproval = false,
                    notifyCreatorOnJoin = true,
                    requiresCompletionConfirmation = false,
                    maxParticipants = null,
                    requirements = emptyList(),
                    taskType = TaskType.TIME_SPECIFIC,
                    importance = TaskImportance.HIGH,
                    urgencyType = TimeUrgency.SPECIFIC_TIME,
                    executionTime = targetIso,
                    targetPercentage = 100,
                    repeatIntervalMinutes = null,
                    alarmType = AlarmType.SOUND_ALERT,
                    advanceRemindersMinutes = listOf(0, 5),
                    remindUntilCompleted = false,
                    overdueReminderIntervalMinutes = 5,
                    maxOverdueReminders = 3,
                    responseType = ResponseType.YES_NO,
                    lanTargetList = cleanedLans,
                    completionPolicy = CompletionPolicy.SINGLE_ASSIGNEE,
                    scopeLevel = scope.effectiveScopeLevel,
                    zoneId = scope.zoneId,
                    stateId = scope.stateId,
                    cityId = scope.cityId,
                    centreId = scope.centreId,
                    targetRoleCodes = listOf("IT_MANAGER", "VCO")
                )
                if (res.isSuccess) {
                    created++
                }
            }
            taskRepository.syncTasks()
            loadShiftDetails(shiftId)
            _isLoading.value = false
            _actionEvent.emit("Added $created of ${selectedTemplates.size} checklist tasks")
            onComplete(created, selectedTemplates.size)
        }
    }

    fun updateShift(
        shiftId: String,
        name: String,
        shiftNumber: Int?,
        startsAt: String,
        endsAt: String,
        notes: String?,
        status: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.updateShift(
                shiftId = shiftId,
                name = name,
                shiftNumber = shiftNumber,
                startsAt = startsAt,
                endsAt = endsAt,
                notes = notes,
                status = status
            )
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Shift Updated")
                    loadShiftDetails(shiftId)
                    onSuccess()
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to update shift")
                }
            )
            _isLoading.value = false
        }
    }

    fun generateCopyPreview(
        sourceShift: Shift,
        sourceTasks: List<TaskItem>,
        newStartsAt: String
    ): List<TaskCopyPreview> = buildCopyPreview(
        sourceShiftStartsAt = sourceShift.startsAt,
        sourceTasks = sourceTasks,
        newStartsAt = newStartsAt
    )

    fun copyShift(
        sourceShiftId: String,
        newShiftName: String,
        newStartsAt: String,
        copyRequirements: Boolean,
        copyAssignments: Boolean,
        selectedTaskIds: List<String>? = null,
        onSuccess: (Shift) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.copyShift(
                sourceShiftId = sourceShiftId,
                newShiftName = newShiftName,
                newStartsAt = newStartsAt,
                copyManpowerRequirements = copyRequirements,
                copyAssignedEmployees = copyAssignments,
                selectedTaskIds = selectedTaskIds
            )
            res.fold(
                onSuccess = { newShift ->
                    _actionEvent.emit("Shift Copied")
                    onSuccess(newShift)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to copy shift")
                }
            )
            _isLoading.value = false
        }
    }

    fun joinShiftTasksBulk(shiftId: String) {
        viewModelScope.launch {
            if (permissionManager.isOnLeave()) {
                _actionEvent.emit("You are On Leave. Switch to On Duty to join tasks.")
                return@launch
            }
            _isLoading.value = true
            val res = taskRepository.joinShiftTasksBulk(shiftId)
            res.fold(
                onSuccess = { count ->
                    _actionEvent.emit(
                        if (count > 0) "Joined $count shift task(s)" else "All shift tasks already joined"
                    )
                    loadShiftDetails(shiftId)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to join shift tasks")
                }
            )
            _isLoading.value = false
        }
    }

    fun parseIsoToMillis(isoString: String): Long? = TaskItem.parseIsoToMillis(isoString)

    fun formatMillisToIso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    companion object {
        fun isShift1OnlyTask(title: String): Boolean {
            val q = title.lowercase(Locale.US)
            return q.contains("backup") ||
                q.contains("10% launch") ||
                q.contains("100% launch") ||
                q.contains("rd login") ||
                q.contains("rd config")
        }

        fun buildCopyPreview(
            sourceShiftStartsAt: String,
            sourceTasks: List<TaskItem>,
            newStartsAt: String
        ): List<TaskCopyPreview> {
            val srcStartMs = TaskItem.parseIsoToMillis(sourceShiftStartsAt) ?: System.currentTimeMillis()
            val newStartMs = TaskItem.parseIsoToMillis(newStartsAt) ?: srcStartMs
            val offsetMs = newStartMs - srcStartMs

            val sortedTasks = sourceTasks.sortedBy { task ->
                (task.executionTime ?: task.startsAt ?: task.dueAt)
                    ?.let { TaskItem.parseIsoToMillis(it) }
                    ?: Long.MAX_VALUE
            }

            return sortedTasks.map { task ->
                val rawTime = task.executionTime ?: task.startsAt ?: task.dueAt
                val srcTaskStartMs = rawTime?.let { TaskItem.parseIsoToMillis(it) } ?: srcStartMs
                val newTaskStartMs = srcTaskStartMs + offsetMs
                val srcTaskEndMs = task.dueAt?.let { TaskItem.parseIsoToMillis(it) }
                    ?.takeIf { abs(it - srcTaskStartMs) > 60_000L }

                val sourceTimeLabel = if (srcTaskEndMs != null) {
                    "${formatTimeOnly(srcTaskStartMs)} - ${formatTimeOnly(srcTaskEndMs)}"
                } else {
                    formatTimeOnly(srcTaskStartMs)
                }
                val newTimeLabel = if (srcTaskEndMs != null) {
                    "${formatTimeOnly(newTaskStartMs)} - ${formatTimeOnly(srcTaskEndMs + offsetMs)}"
                } else {
                    formatTimeOnly(newTaskStartMs)
                }

                val shift1Only = isShift1OnlyTask(task.title)
                TaskCopyPreview(
                    taskId = task.id,
                    title = task.title,
                    sourceTime = sourceTimeLabel,
                    newTime = newTimeLabel,
                    isShift1Only = shift1Only,
                    defaultSelected = !shift1Only
                )
            }
        }

        fun computeOffsetSummary(sourceStartsAt: String, newStartsAt: String): String {
            val srcMs = TaskItem.parseIsoToMillis(sourceStartsAt) ?: return ""
            val newMs = TaskItem.parseIsoToMillis(newStartsAt) ?: return ""
            val diffMins = kotlin.math.round((newMs - srcMs) / 60_000.0).toLong()
            if (diffMins == 0L) return "No time shift"
            val sign = if (diffMins >= 0) "+" else "-"
            val absMins = abs(diffMins)
            val hrs = absMins / 60
            val rem = absMins % 60
            return buildString {
                append(sign)
                if (hrs > 0) append("${hrs}h ")
                if (rem > 0 || hrs == 0L) append("${rem}m")
            }.trim()
        }

        fun formatTimeOnly(millis: Long): String {
            return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
        }
    }
}
