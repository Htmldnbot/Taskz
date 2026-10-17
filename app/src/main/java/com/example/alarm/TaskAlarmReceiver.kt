package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AlarmType
import com.example.core.model.TaskImportance
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.data.local.entity.TaskResponseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val CHANNEL_ID_CRITICAL_ALARM = "taskz_critical_alarms"
        const val CHANNEL_ID_HIGH_ALARM = "taskz_high_alarms"
        const val CHANNEL_ID_NORMAL = "taskz_normal_notifications"
        const val CHANNEL_ID_SILENT = "taskz_silent_notifications"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                // Critical channel with maximum importance and alarm sound
                val criticalChannel = NotificationChannel(
                    CHANNEL_ID_CRITICAL_ALARM,
                    "Taskz Critical Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Time-critical examination alarms and full-screen alerts"
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }

                // High priority channel with notification sound
                val highChannel = NotificationChannel(
                    CHANNEL_ID_HIGH_ALARM,
                    "Taskz High Priority Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High priority operational tasks and scheduled reminders"
                    setSound(soundUri, audioAttributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }

                // Normal channel
                val normalChannel = NotificationChannel(
                    CHANNEL_ID_NORMAL,
                    "Taskz Task Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Standard task notifications and status updates"
                }

                // Silent channel
                val silentChannel = NotificationChannel(
                    CHANNEL_ID_SILENT,
                    "Taskz Silent Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Silent task notifications"
                    setSound(null, null)
                    enableVibration(false)
                }

                notificationManager.createNotificationChannels(
                    listOf(criticalChannel, highChannel, normalChannel, silentChannel)
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_ID) ?: return
        val alarmId = intent.getIntExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, taskId.hashCode())

        when (action) {
            TaskAlarmScheduler.ACTION_ALARM_TRIGGER -> {
                handleAlarmTrigger(context, intent, taskId, alarmId)
            }
            TaskAlarmScheduler.ACTION_ACKNOWLEDGE -> {
                handleAcknowledge(context, taskId, alarmId)
            }
            TaskAlarmScheduler.ACTION_SNOOZE -> {
                val title = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"
                val importance = intent.getStringExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE) ?: "NORMAL"
                val alarmType = intent.getStringExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE) ?: "SOUND_ALERT"
                handleSnooze(context, taskId, title, importance, alarmType, alarmId)
            }
            TaskAlarmScheduler.ACTION_COMPLETE -> {
                handleComplete(context, taskId, alarmId)
            }
        }
    }

    private fun handleAlarmTrigger(
        context: Context,
        intent: Intent,
        taskId: String,
        alarmId: Int
    ) {
        val title = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val importanceStr = intent.getStringExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE) ?: "NORMAL"
        val alarmTypeStr = intent.getStringExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE) ?: "SOUND_ALERT"
        val reminderLabel = intent.getStringExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL) ?: "Scheduled Time"
        val scheduledTime = intent.getStringExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""
        val description = intent.getStringExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION) ?: ""
        val location = intent.getStringExtra(TaskAlarmScheduler.EXTRA_LOCATION) ?: ""
        val taskType = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE) ?: "SIMPLE"

        createNotificationChannels(context)

        // Mark this alarm as fired in local database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskzDatabase.getInstance(context).scheduledAlarmDao().markAlarmFired(alarmId)
            } catch (e: Exception) {
                Log.e(TAG, "Error marking alarm fired: ${e.message}")
            }
        }

        val alarmType = runCatching { AlarmType.valueOf(alarmTypeStr) }.getOrDefault(AlarmType.SOUND_ALERT)
        val importance = runCatching { TaskImportance.valueOf(importanceStr) }.getOrDefault(TaskImportance.NORMAL)

        val channelId = when {
            alarmType == AlarmType.SILENT -> CHANNEL_ID_SILENT
            importance == TaskImportance.CRITICAL || alarmType == AlarmType.FULL_SCREEN_ALARM -> CHANNEL_ID_CRITICAL_ALARM
            importance == TaskImportance.HIGH || alarmType == AlarmType.SOUND_ALERT -> CHANNEL_ID_HIGH_ALARM
            else -> CHANNEL_ID_NORMAL
        }

        // 1. Content Intent to open MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            alarmId * 10 + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: Acknowledge
        val ackIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmScheduler.ACTION_ACKNOWLEDGE
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId * 10 + 2,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Action: Snooze 5 min
        val snoozeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmScheduler.ACTION_SNOOZE
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, title)
            putExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE, importanceStr)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE, alarmTypeStr)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId * 10 + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtext = "[$importanceStr] $reminderLabel"
        val bodyText = buildString {
            if (location.isNotBlank()) append("📍 $location • ")
            if (description.isNotBlank()) append(description) else append("Immediate action required.")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setSubText(subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\nScheduled: $scheduledTime"))
            .setPriority(
                if (importance == TaskImportance.CRITICAL || alarmType == AlarmType.FULL_SCREEN_ALARM)
                    NotificationCompat.PRIORITY_MAX
                else if (importance == TaskImportance.HIGH)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(R.mipmap.ic_launcher, "Acknowledge", ackPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Snooze 5m", snoozePendingIntent)

        // If Full-Screen Alarm, add FullScreenIntent to launch TaskAlarmActivity
        if (alarmType == AlarmType.FULL_SCREEN_ALARM || importance == TaskImportance.CRITICAL) {
            val fullScreenIntent = Intent(context, TaskAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, title)
                putExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE, importanceStr)
                putExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE, alarmTypeStr)
                putExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL, reminderLabel)
                putExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
                putExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION, description)
                putExtra(TaskAlarmScheduler.EXTRA_LOCATION, location)
                putExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE, taskType)
                putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                alarmId * 10 + 4,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(alarmId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing: ${e.message}")
        }
    }

    private fun handleAcknowledge(context: Context, taskId: String, alarmId: Int) {
        // Cancel notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(alarmId)

        // Record acknowledgement in Room database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TaskzDatabase.getInstance(context)
                val userPrefs = UserPreferencesDataStore(context)
                val profile = db.profileDao().getCachedProfile()
                val activeUserId = userPrefs.getUserSession().userId ?: profile?.id ?: return@launch
                val empId = profile?.employeeId ?: userPrefs.getUserSession().employeeId ?: "EMP_UNKNOWN"
                val eventId = UUID.randomUUID().toString()
                val nowIso = java.time.Instant.now().toString()

                val ackEntity = TaskAcknowledgementEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    userId = activeUserId,
                    employeeId = empId,
                    eventId = eventId,
                    deviceTimestamp = System.currentTimeMillis(),
                    acknowledgedAt = nowIso,
                    syncStatus = "PENDING_SYNC"
                )
                db.taskResponseDao().insertAcknowledgement(ackEntity)

                // Enqueue in outbox and trigger sync
                val payload = org.json.JSONObject().apply {
                    put("deviceTimestamp", nowIso)
                }
                db.outboxDao().enqueueEvent(
                    com.example.data.local.entity.OutboxEventEntity(
                        eventId = eventId,
                        userId = activeUserId,
                        taskId = taskId,
                        operationType = "TASK_ACKNOWLEDGEMENT",
                        payloadJson = payload.toString(),
                        syncStatus = "PENDING",
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Update task isAcknowledged locally
                db.taskDao().markTaskAcknowledged(taskId, true, nowIso)
                com.example.sync.SyncWorker.enqueueSync(context)
                Log.d(TAG, "Task $taskId acknowledged by $empId (enqueued in outbox)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record acknowledgement: ${e.message}")
            }
        }
    }

    private fun handleSnooze(
        context: Context,
        taskId: String,
        title: String,
        importance: String,
        alarmType: String,
        alarmId: Int
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(alarmId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = TaskAlarmScheduler(context)
                scheduler.scheduleSnoozeAlarm(taskId, title, importance, alarmType, 5)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to snooze alarm: ${e.message}")
            }
        }
    }

    private fun handleComplete(context: Context, taskId: String, alarmId: Int) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(alarmId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TaskzDatabase.getInstance(context)
                val userPrefs = com.example.core.datastore.UserPreferencesDataStore(context)
                val profile = db.profileDao().getCachedProfile()
                val activeUserId = userPrefs.getUserSession().userId ?: profile?.id ?: return@launch
                val empId = profile?.employeeId ?: ""
                val empName = profile?.fullName ?: empId
                val nowIso = java.time.Instant.now().toString()
                val eventId = UUID.randomUUID().toString()

                // Save response locally as PENDING_SYNC
                val responseEntity = TaskResponseEntity(
                    id = eventId,
                    taskId = taskId,
                    userId = activeUserId,
                    employeeId = empId,
                    employeeName = empName,
                    lanNumber = null,
                    responseType = com.example.core.model.ResponseType.COMPLETED_NOT_COMPLETED.name,
                    responseValue = "COMPLETED",
                    percentageValue = 100,
                    numericValue1 = null,
                    numericValue2 = null,
                    textValue = "Notification complete action",
                    dynamicValuesJson = "{}",
                    submittedAt = nowIso,
                    syncStatus = "PENDING_SYNC"
                )
                db.taskResponseDao().insertResponse(responseEntity)

                // Enqueue in Outbox
                val payload = org.json.JSONObject().apply {
                    put("responseType", com.example.core.model.ResponseType.COMPLETED_NOT_COMPLETED.name)
                    put("responseValue", "COMPLETED")
                    put("percentage", 100)
                    put("textValue", "Notification complete action")
                    put("deviceTimestamp", nowIso)
                }
                db.outboxDao().enqueueEvent(
                    com.example.data.local.entity.OutboxEventEntity(
                        eventId = eventId,
                        userId = activeUserId,
                        taskId = taskId,
                        operationType = "COMPLETION_REQUEST",
                        payloadJson = payload.toString(),
                        syncStatus = "PENDING",
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Trigger background sync
                com.example.sync.SyncWorker.enqueueSync(context)
                Log.d(TAG, "Completion request for $taskId recorded in outbox by $empId (awaiting server confirmation)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue completion request: ${e.message}")
            }
        }
    }
}
