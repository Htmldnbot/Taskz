package com.example.notifications

import android.util.Log
import com.example.TaskzApplication
import com.example.core.model.NotificationCategory
import com.example.sync.SyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles real FCM push token rotation and incoming push notifications.
 * Deduplicates notifications by event_key across FCM, Realtime, and Delta Sync,
 * renders tray alerts reliably when closed, and triggers expedited background sync + alarm scheduling.
 */
class TaskzFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "TaskzFCMService"
        private const val FCM_SYNC_WINDOW_MS = 8_000L
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val clean = token.trim()
        if (clean.isBlank()) return
        Log.i(TAG, "Received refreshed FCM token (length=${clean.length})")
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(5_000L) {
                runCatching {
                    val app = applicationContext as? TaskzApplication ?: TaskzApplication.instance
                    app.deviceTokenManager.onNewPushToken(clean)
                }.onFailure {
                    Log.w(TAG, "Deferred FCM token registration: ${it.message}")
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = (applicationContext as? TaskzApplication)
            ?: runCatching { TaskzApplication.instance }.getOrNull()
            ?: return
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

        // Display tray notification unless OS already rendered a notification block while backgrounded
        val shouldRenderLocalTray = message.notification == null || app.isAppInForeground
        val isRoleOrAdminUpdate = category == NotificationCategory.ADMIN_ALERT ||
            typeRaw.equals("role_changed", ignoreCase = true)

        // Hold FCM's execution wake window long enough to display the notification and run/schedule background sync
        runBlocking(Dispatchers.IO) {
            val session = app.userPreferences.getUserSession()
            val activeUserId = session.userId.orEmpty()
            if (!session.isAuthenticated || activeUserId.isBlank()) {
                Log.d(TAG, "Ignoring incoming push while signed out")
                return@runBlocking
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

            // Always schedule expedited WorkManager sync so closed-app task/alarm sync survives process freeze
            runCatching {
                SyncWorker.enqueueExpeditedSync(applicationContext, forceFull = isRoleOrAdminUpdate)
            }

            if (app.networkMonitor.isCurrentlyConnected()) {
                withTimeoutOrNull(FCM_SYNC_WINDOW_MS) {
                    runCatching {
                        app.taskzSyncManager.syncNow(forceFull = isRoleOrAdminUpdate)
                    }
                }
            }
        }
    }
}
