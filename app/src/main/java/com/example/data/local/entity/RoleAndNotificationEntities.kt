package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.AppNotification

@Entity(tableName = "cached_notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String?,
    val taskId: String?,
    val groupId: String?,
    val isRead: Boolean,
    val readAt: String?,
    val createdAt: String
) {
    fun toDomain(): AppNotification = AppNotification(
        id = id,
        userId = userId,
        type = type,
        title = title,
        body = body,
        taskId = taskId,
        groupId = groupId,
        isRead = isRead,
        readAt = readAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: AppNotification): NotificationEntity = NotificationEntity(
            id = domain.id,
            userId = domain.userId,
            type = domain.type,
            title = domain.title,
            body = domain.body,
            taskId = domain.taskId,
            groupId = domain.groupId,
            isRead = domain.isRead,
            readAt = domain.readAt,
            createdAt = domain.createdAt
        )
    }
}
