package com.example.core.model

enum class TaskType(val label: String, val description: String) {
    SIMPLE("Simple Task", "Basic task with optional due time and completion confirmation"),
    TIME_SPECIFIC("Time-Specific Task", "Exact execution time with alarm and advance reminders"),
    DEADLINE("Deadline Task", "Task with hard deadline, warning alarms, and overdue tracking"),
    IMMEDIATE("Immediate / Urgent Task", "Action needed immediately with urgent prominence"),
    PROGRESS("Progress Task", "Incremental target percentage updates towards a goal"),
    MULTI_CONFIRMATION("Multi-Confirmation Task", "Requires individual confirmation from each assigned employee / LAN"),
    RECURRING("Recurring Task", "Repeats at fixed intervals with per-occurrence tracking")
}

enum class TaskImportance(val label: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    CRITICAL("Critical")
}

enum class TimeUrgency(val label: String) {
    IMMEDIATELY("Immediately"),
    SPECIFIC_TIME("At a specific time"),
    BEFORE_DEADLINE("Before a deadline"),
    TIME_WINDOW("During a time window"),
    RECURRING_INTERVAL("Repeatedly at an interval")
}

enum class AlarmType(val label: String) {
    SILENT("Silent"),
    NORMAL_NOTIFICATION("Normal Notification"),
    SOUND_ALERT("Sound Alert"),
    FULL_SCREEN_ALARM("Full-Screen Alarm")
}

enum class ResponseType(val label: String) {
    ACKNOWLEDGE_ONLY("Acknowledge only"),
    YES_NO("Yes / No"),
    COMPLETED_NOT_COMPLETED("Completed / Not Completed"),
    PERCENTAGE("Percentage (0-100%)"),
    NUMERIC_COUNT("Numeric Count"),
    TEXT_RESPONSE("Text Response"),
    MULTI_CONFIRMATION("Multiple Employee Confirmation")
}

enum class TaskStatus(val label: String) {
    SCHEDULED("Scheduled"),
    UPCOMING("Upcoming"),
    ACTION_REQUIRED("Action Required"),
    ACKNOWLEDGED("Acknowledged"),
    IN_PROGRESS("In Progress"),
    PENDING_CONFIRMATION("Pending Confirmation"),
    COMPLETED("Completed"),
    OVERDUE("Overdue"),
    CANCELLED("Cancelled")
}

data class TaskItem(
    val id: String = "",
    val groupId: String = "",
    val groupTitle: String? = null,
    val shiftId: String? = null,
    val shiftName: String? = null,
    val title: String = "",
    val description: String? = null,
    val location: String? = null,
    val startsAt: String? = null,
    val dueAt: String? = null,
    val priority: String = "normal", // low, normal, high, urgent
    val status: String = "open", // draft, open, in_progress, completed, cancelled, archived
    val visibility: String = "everyone", // everyone, restricted
    val allowSelfJoin: Boolean = false,
    val joinRequiresApproval: Boolean = true,
    val notifyCreatorOnJoin: Boolean = true,
    val requiresCompletionConfirmation: Boolean = false,
    val maxParticipants: Int? = null,
    val createdBy: String = "",
    val creatorName: String? = null,
    val totalRequired: Int = 0,
    val totalAssigned: Int = 0,
    val totalVacant: Int = 0,
    val currentUserRoleName: String? = null,
    val currentUserParticipantStatus: String? = null, // "active", "left", "removed" or null
    val currentUserJoinStatus: String? = null, // "pending", "approved", "rejected" or null
    val isPendingCompletionConfirmation: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    
    // Phase 3 Extensions
    val taskType: TaskType = TaskType.SIMPLE,
    val importance: TaskImportance = TaskImportance.NORMAL,
    val urgencyType: TimeUrgency = TimeUrgency.BEFORE_DEADLINE,
    val executionTime: String? = null,
    val targetPercentage: Int? = 100,
    val repeatIntervalMinutes: Int? = null,
    val alarmType: AlarmType = AlarmType.SOUND_ALERT,
    val advanceRemindersMinutes: List<Int> = listOf(0, 5),
    val remindUntilCompleted: Boolean = false,
    val overdueReminderIntervalMinutes: Int? = 5,
    val maxOverdueReminders: Int? = 3,
    val responseType: ResponseType = ResponseType.COMPLETED_NOT_COMPLETED,
    val lanTargetList: List<String> = emptyList(),
    val currentProgress: Int = 0,
    val lastProgressUpdate: String? = null,
    val isAcknowledged: Boolean = false,
    val acknowledgedAt: String? = null
) {
    val isAssignedToMe: Boolean
        get() = currentUserParticipantStatus == "active"

    val isPendingApproval: Boolean
        get() = currentUserJoinStatus == "pending"

    val displayStatus: TaskStatus
        get() {
            if (status.equals("cancelled", ignoreCase = true)) return TaskStatus.CANCELLED
            if (status.equals("completed", ignoreCase = true)) return TaskStatus.COMPLETED
            if (isPendingCompletionConfirmation) return TaskStatus.PENDING_CONFIRMATION
            if (status.equals("in_progress", ignoreCase = true)) return TaskStatus.IN_PROGRESS
            if (isAcknowledged) return TaskStatus.ACKNOWLEDGED
            if (taskType == TaskType.IMMEDIATE) return TaskStatus.ACTION_REQUIRED

            // Check if overdue
            val dueMillis = dueAt?.let { parseIsoToMillis(it) } ?: executionTime?.let { parseIsoToMillis(it) }
            val now = System.currentTimeMillis()
            if (dueMillis != null && dueMillis < now && !status.equals("completed", ignoreCase = true)) {
                return TaskStatus.OVERDUE
            }

            val startMillis = startsAt?.let { parseIsoToMillis(it) } ?: executionTime?.let { parseIsoToMillis(it) }
            if (startMillis != null) {
                if (startMillis <= now) return TaskStatus.ACTION_REQUIRED
                if (startMillis - now <= 30 * 60 * 1000L) return TaskStatus.UPCOMING
                return TaskStatus.SCHEDULED
            }

            return TaskStatus.SCHEDULED
        }

    companion object {
        fun parseIsoToMillis(isoString: String): Long? {
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                null
            }
        }
    }
}
