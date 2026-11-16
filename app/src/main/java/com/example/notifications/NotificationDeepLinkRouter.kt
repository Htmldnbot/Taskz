package com.example.notifications

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeepLinkTarget {
    val notificationId: String?

    data class TaskDetails(
        val taskId: String,
        override val notificationId: String? = null
    ) : DeepLinkTarget

    data class IncidentDetails(
        val incidentId: String,
        override val notificationId: String? = null
    ) : DeepLinkTarget

    data class ProfileRoles(
        override val notificationId: String? = null
    ) : DeepLinkTarget

    data class AdminControls(
        override val notificationId: String? = null
    ) : DeepLinkTarget
}

/**
 * Parses deep links from Android notifications & push intents.
 * Does NOT grant authorization from payload; target screens always refresh from server first.
 */
object NotificationDeepLinkRouter {
    private val _pendingTarget = MutableStateFlow<DeepLinkTarget?>(null)
    val pendingTarget: StateFlow<DeepLinkTarget?> = _pendingTarget.asStateFlow()

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val deepLink = intent.getStringExtra(PushNotificationManager.EXTRA_DEEP_LINK)
            ?: intent.getStringExtra("deep_link")
        val taskId = intent.getStringExtra(PushNotificationManager.EXTRA_TASK_ID)
            ?: intent.getStringExtra("task_id")
        val incidentId = intent.getStringExtra(PushNotificationManager.EXTRA_INCIDENT_ID)
            ?: intent.getStringExtra("incident_id")
        val category = intent.getStringExtra(PushNotificationManager.EXTRA_CATEGORY)
            ?: intent.getStringExtra("category")
        val notificationId = intent.getStringExtra(PushNotificationManager.EXTRA_NOTIFICATION_ID)
            ?: intent.getStringExtra("notification_id")

        val resolved = parse(
            deepLink = deepLink,
            taskId = taskId,
            incidentId = incidentId,
            category = category,
            notificationId = notificationId
        )
        if (resolved != null) {
            _pendingTarget.value = resolved
        }
    }

    fun route(target: DeepLinkTarget) {
        _pendingTarget.value = target
    }

    fun consume() {
        _pendingTarget.value = null
    }

    fun parse(
        deepLink: String? = null,
        taskId: String? = null,
        incidentId: String? = null,
        category: String? = null,
        notificationId: String? = null
    ): DeepLinkTarget? {
        val raw = deepLink?.trim().orEmpty()
        if (raw.startsWith("taskz://task/", ignoreCase = true)) {
            val id = raw.removePrefix("taskz://task/").trim()
            if (id.isNotBlank()) return DeepLinkTarget.TaskDetails(id, notificationId)
        }
        if (raw.startsWith("taskz://incident/", ignoreCase = true)) {
            val id = raw.removePrefix("taskz://incident/").trim()
            if (id.isNotBlank()) return DeepLinkTarget.IncidentDetails(id, notificationId)
        }
        if (raw.startsWith("taskz://profile", ignoreCase = true)) {
            return DeepLinkTarget.ProfileRoles(notificationId)
        }
        if (raw.startsWith("taskz://admin", ignoreCase = true)) {
            return DeepLinkTarget.AdminControls(notificationId)
        }

        if (!taskId.isNullOrBlank()) return DeepLinkTarget.TaskDetails(taskId, notificationId)
        if (!incidentId.isNullOrBlank()) return DeepLinkTarget.IncidentDetails(incidentId, notificationId)
        if (category?.contains("ADMIN", ignoreCase = true) == true) return DeepLinkTarget.AdminControls(notificationId)
        return null
    }
}
