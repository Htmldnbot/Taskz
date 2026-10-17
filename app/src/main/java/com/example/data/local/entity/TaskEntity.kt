package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.AlarmType
import com.example.core.model.ResponseType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency

@Entity(tableName = "cached_tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val groupTitle: String?,
    val shiftId: String?,
    val shiftName: String?,
    val title: String,
    val description: String?,
    val location: String?,
    val startsAt: String?,
    val dueAt: String?,
    val priority: String,
    val status: String,
    val visibility: String,
    val allowSelfJoin: Boolean,
    val joinRequiresApproval: Boolean,
    val notifyCreatorOnJoin: Boolean,
    val requiresCompletionConfirmation: Boolean,
    val maxParticipants: Int?,
    val createdBy: String,
    val creatorName: String?,
    val createdAt: String,
    val updatedAt: String,
    
    // Phase 3 fields
    val taskType: String = "SIMPLE",
    val importance: String = "NORMAL",
    val urgencyType: String = "BEFORE_DEADLINE",
    val executionTime: String? = null,
    val targetPercentage: Int? = 100,
    val repeatIntervalMinutes: Int? = null,
    val alarmType: String = "SOUND_ALERT",
    val advanceRemindersMinutesCsv: String = "0,5",
    val remindUntilCompleted: Boolean = false,
    val overdueReminderIntervalMinutes: Int? = 5,
    val maxOverdueReminders: Int? = 3,
    val responseType: String = "COMPLETED_NOT_COMPLETED",
    val lanTargetListCsv: String = "",
    val currentProgress: Int = 0,
    val lastProgressUpdate: String? = null,
    val isAcknowledged: Boolean = false,
    val acknowledgedAt: String? = null,
    val completionPolicy: String = "SINGLE_ASSIGNEE",
    val responseFormSchemaJson: String? = null
) {
    fun toDomain(): TaskItem = TaskItem(
        id = id,
        groupId = groupId,
        groupTitle = groupTitle,
        shiftId = shiftId,
        shiftName = shiftName,
        title = title,
        description = description,
        location = location,
        startsAt = startsAt,
        dueAt = dueAt,
        priority = priority,
        status = status,
        visibility = visibility,
        allowSelfJoin = allowSelfJoin,
        joinRequiresApproval = joinRequiresApproval,
        notifyCreatorOnJoin = notifyCreatorOnJoin,
        requiresCompletionConfirmation = requiresCompletionConfirmation,
        maxParticipants = maxParticipants,
        createdBy = createdBy,
        creatorName = creatorName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        taskType = runCatching { TaskType.valueOf(taskType) }.getOrDefault(TaskType.SIMPLE),
        importance = runCatching { TaskImportance.valueOf(importance) }.getOrDefault(TaskImportance.NORMAL),
        urgencyType = runCatching { TimeUrgency.valueOf(urgencyType) }.getOrDefault(TimeUrgency.BEFORE_DEADLINE),
        executionTime = executionTime,
        targetPercentage = targetPercentage,
        repeatIntervalMinutes = repeatIntervalMinutes,
        alarmType = runCatching { AlarmType.valueOf(alarmType) }.getOrDefault(AlarmType.SOUND_ALERT),
        advanceRemindersMinutes = parseCsvToIntList(advanceRemindersMinutesCsv),
        remindUntilCompleted = remindUntilCompleted,
        overdueReminderIntervalMinutes = overdueReminderIntervalMinutes,
        maxOverdueReminders = maxOverdueReminders,
        responseType = runCatching { ResponseType.valueOf(responseType) }.getOrDefault(ResponseType.COMPLETED_NOT_COMPLETED),
        lanTargetList = if (lanTargetListCsv.isBlank()) emptyList() else lanTargetListCsv.split(",").map { it.trim() },
        currentProgress = currentProgress,
        lastProgressUpdate = lastProgressUpdate,
        isAcknowledged = isAcknowledged,
        acknowledgedAt = acknowledgedAt,
        completionPolicy = runCatching { com.example.core.model.CompletionPolicy.valueOf(completionPolicy) }.getOrDefault(com.example.core.model.CompletionPolicy.SINGLE_ASSIGNEE),
        responseFormSchemaJson = responseFormSchemaJson
    )

    companion object {
        fun fromDomain(domain: TaskItem): TaskEntity = TaskEntity(
            id = domain.id,
            groupId = domain.groupId,
            groupTitle = domain.groupTitle,
            shiftId = domain.shiftId,
            shiftName = domain.shiftName,
            title = domain.title,
            description = domain.description,
            location = domain.location,
            startsAt = domain.startsAt,
            dueAt = domain.dueAt,
            priority = domain.priority,
            status = domain.status,
            visibility = domain.visibility,
            allowSelfJoin = domain.allowSelfJoin,
            joinRequiresApproval = domain.joinRequiresApproval,
            notifyCreatorOnJoin = domain.notifyCreatorOnJoin,
            requiresCompletionConfirmation = domain.requiresCompletionConfirmation,
            maxParticipants = domain.maxParticipants,
            createdBy = domain.createdBy,
            creatorName = domain.creatorName,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            taskType = domain.taskType.name,
            importance = domain.importance.name,
            urgencyType = domain.urgencyType.name,
            executionTime = domain.executionTime,
            targetPercentage = domain.targetPercentage,
            repeatIntervalMinutes = domain.repeatIntervalMinutes,
            alarmType = domain.alarmType.name,
            advanceRemindersMinutesCsv = domain.advanceRemindersMinutes.joinToString(","),
            remindUntilCompleted = domain.remindUntilCompleted,
            overdueReminderIntervalMinutes = domain.overdueReminderIntervalMinutes,
            maxOverdueReminders = domain.maxOverdueReminders,
            responseType = domain.responseType.name,
            lanTargetListCsv = domain.lanTargetList.joinToString(","),
            currentProgress = domain.currentProgress,
            lastProgressUpdate = domain.lastProgressUpdate,
            isAcknowledged = domain.isAcknowledged,
            acknowledgedAt = domain.acknowledgedAt,
            completionPolicy = domain.completionPolicy.name,
            responseFormSchemaJson = domain.responseFormSchemaJson
        )

        private fun parseCsvToIntList(csv: String): List<Int> {
            if (csv.isBlank()) return emptyList()
            return csv.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
    }
}
