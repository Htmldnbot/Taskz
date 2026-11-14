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

enum class CompletionPolicy(val label: String, val description: String) {
    SINGLE_ASSIGNEE("Single Assignee", "Completes when assigned employee submits completion"),
    ALL_SELECTED_LANS("All Selected LANs", "Each selected LAN must independently confirm completion (100%)"),
    ALL_REQUIRED_PARTICIPANTS("All Required Participants", "All participants must individually submit completion"),
    CREATOR_CONFIRMATION("Creator Confirmation", "Requires creator manual approval before completing"),
    CUSTOM_REQUIRED_COUNT("Custom Required Count", "Completes when required number of confirmations is achieved")
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
    val acknowledgedAt: String? = null,
    val completionPolicy: CompletionPolicy = CompletionPolicy.SINGLE_ASSIGNEE,
    val responseFormSchemaJson: String? = null
) {
    val isAssignedToMe: Boolean
        get() = currentUserParticipantStatus == "active"

    val isPendingApproval: Boolean
        get() = currentUserJoinStatus == "pending"

    fun scheduledExecutionMillis(): Long? =
        executionTime?.let { parseIsoToMillis(it) }
            ?: dueAt?.let { parseIsoToMillis(it) }
            ?: startsAt?.let { parseIsoToMillis(it) }

    fun isBeforeScheduledExecution(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (taskType == TaskType.IMMEDIATE) return false
        val targetMillis = scheduledExecutionMillis() ?: return false
        return nowMillis < targetMillis
    }

    val displayStatus: TaskStatus
        get() {
            if (status.equals("cancelled", ignoreCase = true)) return TaskStatus.CANCELLED
            if (status.equals("completed", ignoreCase = true)) return TaskStatus.COMPLETED
            if (isPendingCompletionConfirmation) return TaskStatus.PENDING_CONFIRMATION
            if (status.equals("in_progress", ignoreCase = true)) return TaskStatus.IN_PROGRESS
            if (isAcknowledged) return TaskStatus.ACKNOWLEDGED
            if (taskType == TaskType.IMMEDIATE) return TaskStatus.ACTION_REQUIRED

            val now = System.currentTimeMillis()
            val execMillis = executionTime?.let { parseIsoToMillis(it) }
            val dueMillis = dueAt?.let { parseIsoToMillis(it) } ?: execMillis
            if (dueMillis != null && dueMillis < now) {
                return TaskStatus.OVERDUE
            }

            // If executionTime is in the future, do not mark ACTION_REQUIRED even if startsAt was set earlier
            if (execMillis != null && execMillis > now) {
                return if (execMillis - now <= 30 * 60 * 1000L) TaskStatus.UPCOMING else TaskStatus.SCHEDULED
            }

            val startMillis = execMillis ?: startsAt?.let { parseIsoToMillis(it) } ?: dueMillis
            if (startMillis != null) {
                if (startMillis <= now) return TaskStatus.ACTION_REQUIRED
                if (startMillis - now <= 30 * 60 * 1000L) return TaskStatus.UPCOMING
                return TaskStatus.SCHEDULED
            }

            return TaskStatus.SCHEDULED
        }

    companion object {
        const val PAST_TIME_ERROR_MESSAGE = "Selected execution time has already passed. Please choose a future date and time."

        fun validateExecutionTime(executionTimeStr: String, nowMillis: Long = System.currentTimeMillis()): String? {
            if (executionTimeStr.isBlank()) return null
            val parsedMillis = parseIsoToMillis(executionTimeStr)
                ?: return "Invalid execution date/time format. Please choose a valid date and time."
            if (parsedMillis < nowMillis) {
                return PAST_TIME_ERROR_MESSAGE
            }
            return null
        }

        fun formatForDisplay(isoOrDateString: String): String? {
            val millis = parseIsoToMillis(isoOrDateString) ?: return null
            return try {
                val zdt = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a (z)")
                zdt.format(formatter)
            } catch (_: Exception) {
                null
            }
        }

        fun parseIsoToMillis(isoString: String): Long? {
            if (isoString.isBlank()) return null
            val trimmed = isoString.trim()
            val defaultTz = java.util.TimeZone.getDefault()
            val defaultZoneId = java.time.ZoneId.systemDefault()

            // 1. Try standard Instant (e.g. 2026-10-19T10:00:00Z)
            try {
                return java.time.Instant.parse(trimmed).toEpochMilli()
            } catch (_: Exception) {}

            // 2. Try OffsetDateTime (e.g. 2026-10-19T10:00:00+05:30)
            try {
                return java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (_: Exception) {}

            // 3. Try ZonedDateTime
            try {
                return java.time.ZonedDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (_: Exception) {}

            // 4. Try LocalDateTime at device zone
            try {
                return java.time.LocalDateTime.parse(trimmed).atZone(defaultZoneId).toInstant().toEpochMilli()
            } catch (_: Exception) {}

            // 5. Try standard date & time patterns using device timezone
            val patterns = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd hh:mm a",
                "MMM d, yyyy, hh:mm a",
                "MMM d, yyyy, HH:mm",
                "yyyy-MM-dd"
            )
            for (pattern in patterns) {
                try {
                    val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                        timeZone = defaultTz
                    }
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) {
                        return parsed.time
                    }
                } catch (_: Exception) {}
            }

            // 6. Time only fallback (hh:mm a or HH:mm) on today's date in device timezone
            try {
                val cal = java.util.Calendar.getInstance(defaultTz)
                val timeSdf = if (trimmed.contains("AM", ignoreCase = true) || trimmed.contains("PM", ignoreCase = true)) {
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
                } else {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                }
                timeSdf.timeZone = defaultTz
                val parsedTime = timeSdf.parse(trimmed)
                if (parsedTime != null) {
                    val timeCal = java.util.Calendar.getInstance(defaultTz).apply { time = parsedTime }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, timeCal.get(java.util.Calendar.HOUR_OF_DAY))
                    cal.set(java.util.Calendar.MINUTE, timeCal.get(java.util.Calendar.MINUTE))
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}

            return null
        }
    }
}
