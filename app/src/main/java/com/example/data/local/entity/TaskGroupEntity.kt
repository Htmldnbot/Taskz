package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.TaskGroup

@Entity(tableName = "cached_task_groups")
data class TaskGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val code: String?,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val status: String,
    val visibility: String,
    val allowSelfJoin: Boolean,
    val joinRequiresApproval: Boolean,
    val notifyCreatorOnJoin: Boolean,
    val maxMembers: Int?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain(): TaskGroup = TaskGroup(
        id = id,
        title = title,
        code = code,
        description = description,
        startDate = startDate,
        endDate = endDate,
        status = status,
        visibility = visibility,
        allowSelfJoin = allowSelfJoin,
        joinRequiresApproval = joinRequiresApproval,
        notifyCreatorOnJoin = notifyCreatorOnJoin,
        maxMembers = maxMembers,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: TaskGroup): TaskGroupEntity = TaskGroupEntity(
            id = domain.id,
            title = domain.title,
            code = domain.code,
            description = domain.description,
            startDate = domain.startDate,
            endDate = domain.endDate,
            status = domain.status,
            visibility = domain.visibility,
            allowSelfJoin = domain.allowSelfJoin,
            joinRequiresApproval = domain.joinRequiresApproval,
            notifyCreatorOnJoin = domain.notifyCreatorOnJoin,
            maxMembers = domain.maxMembers,
            createdBy = domain.createdBy,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
