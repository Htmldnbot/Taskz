package com.example.notifications

import android.util.Log
import com.example.TaskzApplication
import com.example.core.model.NotificationCategory
import com.example.sync.SyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles real FCM push token rotation and incoming push notifications.
 * Deduplicates notifications by event_key across FCM, Realtime, and Delta Sync,
 * and triggers scoped delta synchronization immediately when a push arrives.
 */
class TaskzFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "TaskzFCMService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val clean = token.trim()
        if (clean.isBlank()) return
        Log.i(TAG, "Received refreshed FCM token (length=${clean.length})")
        serviceScope.launch {
            runCatching {
                val app = applicationContext as? TaskzApplication ?: TaskzApplication.instance
                app.deviceTokenManager.onNewPushToken(clean)
            }.onFailure {
                Log.w(TAG, "Deferred FCM token registration: ${it.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = (applicationContext as? TaskzApplication) ?: runCatching { TaskzApplication.instance }.getOrNull() ?: return
        val data = message.data
        val eventKey = data["event_key"]?.trim()?.takeIf { it.isNotBlank() }
            ?: message.messageId?.let { "fcm:$it" }
            ?: "fcm:${System.currentTimeMillis()}"
        val notificationId = data["notification_id"]?.trim()?.takeIf { it.isNotBlank() }
        val categoryRaw = data["category"]?.trim()
        val typeRaw = data["type"]?.trim()
        val category = NotificationCategory.fromRaw(categoryRaw, typeRaw)
        val deepLink = data["deep_link"]?.trim()?.takeIf { it.isNotBlank() }
        val taskId = data["task_id"]?.trim()?.takeIf { it.isNotBlank() }
        val incidentId = data["incident_id"]?.trim()?.takeIf { it.isNotBlank() }
        val title = message.notification?.title?.takeIf { it.isNotBlank() }
            ?: data["title"]?.takeIf { it.isNotBlank() }
            ?: "Taskz Alert"
        val body = message.notification?.body?.takeIf { it.isNotBlank() }
            ?: data["body"]?.takeIf { it.isNotBlank() }
            ?: "Open Taskz for details"

        // If FCM already carried a notification block while the app was backgrounded,
        // the OS has already rendered the system tray notification; only record event_key
        // to suppress duplicate alerts when delta sync subsequently pulls the same record.
        val shouldRenderLocalTray = message.notification == null || app.isAppInForeground
        val isRoleOrAdminUpdate = category == NotificationCategory.ADMIN_ALERT ||
            typeRaw.equals("role_changed", ignoreCase = true)

        serviceScope.launch {
            val session = app.userPreferences.getUserSession()
            val activeUserId = session.userId.orEmpty()
            if (!session.isAuthenticated || activeUserId.isBlank()) {
                Log.d(TAG, "Ignoring incoming push while signed out")
                return@launch
            }
            runCatching {
                app.pushNotificationManager.dispatchRemotePushIfUnique(
                    eventKey = eventKey,
                    userId = activeUserId,
                    title = title,
                    body = body,
                    category = category,
                    deepLink = deepLink,
                    taskId = taskId,
                    incidentId = incidentId,
                    notificationId = notificationId,
                    displayTrayNotification = shouldRenderLocalTray
                )
            }.onFailure {
                Log.w(TAG, "Push dispatch/dedup warning: ${it.message}")
            }

            if (app.networkMonitor.isCurrentlyConnected()) {
                val syncResult = runCatching {
                    app.taskzSyncManager.syncNow(forceFull = isRoleOrAdminUpdate)
                }.getOrNull()
                if (syncResult == null || syncResult.isFailure) {
                    runCatching { SyncWorker.enqueueSync(applicationContext) }
                }
            } else {
                runCatching { SyncWorker.enqueueSync(applicationContext) }
            }
        }
    }
}
