package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.model.AppNotification
import com.example.core.model.NotificationCategory
import com.example.data.local.dao.AuthorizationDao
import com.example.data.local.entity.DispatchedNotificationKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles categorized Android notification channels, deep-linked notification display,
 * and persistent event-key deduplication so realtime/delta/retry never spam duplicate alerts.
 */
class PushNotificationManager(
    private val context: Context,
    private val authorizationDao: AuthorizationDao
) {
    companion object {
        private const val TAG = "PushNotificationMgr"
        const val EXTRA_DEEP_LINK = "extra_deep_link"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    private val mutex = Mutex()
    private val recentMemoryKeys = object : LinkedHashSet<String>() {}

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        NotificationCategory.entries.forEach { cat ->
            val importance = if (cat.urgent) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(cat.channelId, cat.displayName, importance).apply {
                description = "Taskz ${cat.displayName}"
                enableVibration(cat.urgent)
            }
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun dispatchIfUnique(notification: AppNotification): Boolean = withContext(Dispatchers.IO) {
        if (notification.isRead) return@withContext false
        val dedupKey = notification.eventKey?.takeIf { it.isNotBlank() } ?: "notif:${notification.id}"

        mutex.withLock {
            if (recentMemoryKeys.contains(dedupKey)) return@withContext false
            val alreadyShown = runCatching {
                authorizationDao.hasDispatchedNotification(dedupKey)
            }.getOrDefault(false)
            if (alreadyShown) {
                rememberInMemory(dedupKey)
                return@withContext false
            }

            rememberInMemory(dedupKey)
            runCatching {
                authorizationDao.recordDispatchedNotification(
                    DispatchedNotificationKeyEntity(
                        eventKey = dedupKey,
                        userId = notification.userId
                    )
                )
            }
        }

        showSystemNotification(
            dedupKey = dedupKey,
            title = notification.title,
            body = notification.body.orEmpty(),
            category = NotificationCategory.fromRaw(notification.category, notification.type),
            deepLink = notification.deepLink,
            taskId = notification.taskId,
            incidentId = notification.incidentId,
            notificationId = notification.id
        )
        true
    }

    fun showSystemNotification(
        dedupKey: String,
        title: String,
        body: String,
        category: NotificationCategory,
        deepLink: String?,
        taskId: String? = null,
        incidentId: String? = null,
        notificationId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.d(TAG, "POST_NOTIFICATIONS not granted; skipped tray display for $dedupKey")
                return
            }
        }

        ensureChannelsCreated()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK, deepLink)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_INCIDENT_ID, incidentId)
            putExtra(EXTRA_CATEGORY, category.name)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            dedupKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = if (category.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        val built = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "Open Taskz for authorized details" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { title }))
            .setAutoCancel(true)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            manager.notify(dedupKey.hashCode(), built)
        }.onFailure {
            Log.w(TAG, "Failed to post system notification: ${it.message}")
        }
    }

    private fun rememberInMemory(key: String) {
        if (recentMemoryKeys.size >= 512) {
            recentMemoryKeys.firstOrNull()?.let { recentMemoryKeys.remove(it) }
        }
        recentMemoryKeys.add(key)
    }

    fun clearMemoryKeys() {
        recentMemoryKeys.clear()
    }
}
