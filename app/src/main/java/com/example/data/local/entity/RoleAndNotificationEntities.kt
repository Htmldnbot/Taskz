package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.AppNotification

@Entity(tableName = "cached_notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val category: String = "task_update",
    val title: String,
    val body: String?,
    val taskId: String?,
    val incidentId: String? = null,
    val groupId: String?,
    val eventKey: String? = null,
    val deepLink: String? = null,
    val scopeLevel: String? = null,
    val isRead: Boolean,
    val readAt: String?,
    val createdAt: String
) {
    fun toDomain(): AppNotification = AppNotification(
        id = id,
        userId = userId,
        type = type,
        category = category,
        title = title,
        body = body,
        taskId = taskId,
        incidentId = incidentId,
        groupId = groupId,
        eventKey = eventKey,
        deepLink = deepLink,
        scopeLevel = scopeLevel,
        isRead = isRead,
        readAt = readAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: AppNotification): NotificationEntity = NotificationEntity(
            id = domain.id,
            userId = domain.userId,
            type = domain.type,
            category = domain.category,
            title = domain.title,
            body = domain.body,
            taskId = domain.taskId,
            incidentId = domain.incidentId,
            groupId = domain.groupId,
            eventKey = domain.eventKey,
            deepLink = domain.deepLink,
            scopeLevel = domain.scopeLevel,
            isRead = domain.isRead,
            readAt = domain.readAt,
            createdAt = domain.createdAt
        )
    }
}
