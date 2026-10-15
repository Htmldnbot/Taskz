package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.TaskItem

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
    val updatedAt: String
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
        updatedAt = updatedAt
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
            updatedAt = domain.updatedAt
        )
    }
}
