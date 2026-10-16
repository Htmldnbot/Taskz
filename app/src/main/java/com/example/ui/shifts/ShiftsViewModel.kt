package com.example.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Shift
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TaskCopyPreview(
    val title: String,
    val sourceTime: String,
    val newTime: String
)

class ShiftsViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

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
            // Load tasks belonging to this shift
            // We use getTasksForShiftFlow
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
                    _actionEvent.emit("Shift Created Successfully")
                    onSuccess(shift)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create shift")
                }
            )
            _isLoading.value = false
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
                    _actionEvent.emit("Shift Updated Successfully")
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
    ): List<TaskCopyPreview> {
        val srcStartMs = parseDateTimeToMillis(sourceShift.startsAt)
        val newStartMs = parseDateTimeToMillis(newStartsAt)
        val offsetMs = newStartMs - srcStartMs

        return sourceTasks.map { task ->
            val srcTaskStartMs = task.startsAt?.let { parseDateTimeToMillis(it) } ?: srcStartMs
            val newTaskStartMs = srcTaskStartMs + offsetMs

            val srcTaskEndMs = task.dueAt?.let { parseDateTimeToMillis(it) } ?: (srcTaskStartMs + 3600000L)
            val newTaskEndMs = srcTaskEndMs + offsetMs

            TaskCopyPreview(
                title = task.title,
                sourceTime = formatTimeOnly(srcTaskStartMs) + " - " + formatTimeOnly(srcTaskEndMs),
                newTime = formatTimeOnly(newTaskStartMs) + " - " + formatTimeOnly(newTaskEndMs)
            )
        }
    }

    fun copyShift(
        sourceShiftId: String,
        newShiftName: String,
        newStartsAt: String,
        copyRequirements: Boolean,
        copyAssignments: Boolean,
        onSuccess: (Shift) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.copyShift(
                sourceShiftId = sourceShiftId,
                newShiftName = newShiftName,
                newStartsAt = newStartsAt,
                copyManpowerRequirements = copyRequirements,
                copyAssignedEmployees = copyAssignments
            )
            res.fold(
                onSuccess = { newShift ->
                    _actionEvent.emit("Shift Copied Successfully")
                    onSuccess(newShift)
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to copy shift")
                }
            )
            _isLoading.value = false
        }
    }

    private fun parseDateTimeToMillis(isoString: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatTimeOnly(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
    }
}
