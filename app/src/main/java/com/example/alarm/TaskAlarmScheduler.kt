package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AlarmType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.ScheduledAlarmRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val database = TaskzDatabase.getInstance(context)
    private val userPrefs = UserPreferencesDataStore(context)

    companion object {
        private const val TAG = "TaskAlarmScheduler"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_IMPORTANCE = "extra_importance"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_REMINDER_LABEL = "extra_reminder_label"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_TASK_TYPE = "extra_task_type"

        const val ACTION_ALARM_TRIGGER = "com.example.alarm.ACTION_ALARM_TRIGGER"
        const val ACTION_ACKNOWLEDGE = "com.example.alarm.ACTION_ACKNOWLEDGE"
        const val ACTION_SNOOZE = "com.example.alarm.ACTION_SNOOZE"
        const val ACTION_COMPLETE = "com.example.alarm.ACTION_COMPLETE"
    }

    /**
     * Schedules all advance reminders, main execution alarm, and overdue repeats for a task.
     * Alarms are tied to the active user's identity.
     */
    suspend fun scheduleAlarmsForTask(task: TaskItem, userId: String? = null) = withContext(Dispatchers.IO) {
        val targetUserId = userId ?: userPrefs.getUserSession().userId ?: database.profileDao().getCachedProfile()?.id ?: ""
        if (targetUserId.isBlank()) {
            Log.w(TAG, "Cannot schedule alarm without authenticated user identity")
            return@withContext
        }

        if (task.status.equals("completed", ignoreCase = true) ||
            task.status.equals("cancelled", ignoreCase = true)
        ) {
            cancelAlarmsForTask(task.id)
            return@withContext
        }

        // Cancel previous alarms for this task first
        cancelAlarmsForTask(task.id)

        val mainTimeMillis = task.executionTime?.let { TaskItem.parseIsoToMillis(it) }
            ?: task.startsAt?.let { TaskItem.parseIsoToMillis(it) }
            ?: task.dueAt?.let { TaskItem.parseIsoToMillis(it) }
            ?: if (task.taskType == TaskType.IMMEDIATE) System.currentTimeMillis() else null

        if (mainTimeMillis == null) {
            Log.d(TAG, "No schedule time available for task: ${task.id}")
            return@withContext
        }

        val now = System.currentTimeMillis()
        val alarmsToInsert = mutableListOf<ScheduledAlarmRecordEntity>()

        // 1. Advance Reminders (e.g., 30m before, 10m before, 5m before, 0m at task time)
        for (minutesBefore in task.advanceRemindersMinutes) {
            val triggerMillis = mainTimeMillis - (minutesBefore * 60 * 1000L)
            if (triggerMillis > now - 60_000L) { // Allow immediate or near-future
                val label = if (minutesBefore == 0) "At task time" else "$minutesBefore minutes before"
                val alarmId = generateAlarmId(task.id, -minutesBefore)
                val alarmEntity = ScheduledAlarmRecordEntity(
                    alarmId = alarmId,
                    userId = targetUserId,
                    taskId = task.id,
                    taskTitle = task.title,
                    triggerAtMillis = triggerMillis,
                    alarmType = task.alarmType.name,
                    importance = task.importance.name,
                    reminderLabel = label
                )
                alarmsToInsert.add(alarmEntity)
                setNativeAlarm(alarmEntity, task)
            }
        }

        // 2. Overdue Reminders if remindUntilCompleted = true
        if (task.remindUntilCompleted && !task.status.equals("completed", ignoreCase = true)) {
            val interval = task.overdueReminderIntervalMinutes ?: 5
            val maxCount = task.maxOverdueReminders ?: 3
            for (i in 1..maxCount) {
                val triggerMillis = mainTimeMillis + (i * interval * 60 * 1000L)
                if (triggerMillis > now) {
                    val label = "Overdue Reminder ($i of $maxCount)"
                    val alarmId = generateAlarmId(task.id, 1000 + i)
                    val alarmEntity = ScheduledAlarmRecordEntity(
                        alarmId = alarmId,
                        userId = targetUserId,
                        taskId = task.id,
                        taskTitle = task.title,
                        triggerAtMillis = triggerMillis,
                        alarmType = if (task.importance == TaskImportance.CRITICAL) AlarmType.FULL_SCREEN_ALARM.name else task.alarmType.name,
                        importance = task.importance.name,
                        reminderLabel = label
                    )
                    alarmsToInsert.add(alarmEntity)
                    setNativeAlarm(alarmEntity, task)
                }
            }
        }

        if (alarmsToInsert.isNotEmpty()) {
            database.scheduledAlarmDao().insertAlarms(alarmsToInsert)
            Log.d(TAG, "Successfully scheduled ${alarmsToInsert.size} alarms for task ${task.id} (user $targetUserId)")
        }
    }

    /**
     * Schedules a temporary snooze alarm.
     */
    suspend fun scheduleSnoozeAlarm(
        taskId: String,
        taskTitle: String,
        importance: String,
        alarmType: String,
        snoozeMinutes: Int,
        userId: String? = null
    ) = withContext(Dispatchers.IO) {
        val targetUserId = userId ?: userPrefs.getUserSession().userId ?: database.profileDao().getCachedProfile()?.id ?: ""
        val triggerMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val alarmId = generateAlarmId(taskId, 5000 + snoozeMinutes)
        val alarmEntity = ScheduledAlarmRecordEntity(
            alarmId = alarmId,
            userId = targetUserId,
            taskId = taskId,
            taskTitle = taskTitle,
            triggerAtMillis = triggerMillis,
            alarmType = alarmType,
            importance = importance,
            reminderLabel = "Snoozed ($snoozeMinutes minutes)"
        )
        database.scheduledAlarmDao().insertAlarm(alarmEntity)

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_IMPORTANCE, importance)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
            putExtra(EXTRA_REMINDER_LABEL, alarmEntity.reminderLabel)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleWithAlarmManager(triggerMillis, pendingIntent)
        Log.d(TAG, "Scheduled snooze alarm for $taskId in $snoozeMinutes minutes")
    }

    /**
     * Cancels all scheduled alarms for a task.
     */
    suspend fun cancelAlarmsForTask(taskId: String) = withContext(Dispatchers.IO) {
        val existingAlarms = database.scheduledAlarmDao().getAlarmsForTask(taskId)
        for (record in existingAlarms) {
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                record.alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        database.scheduledAlarmDao().deleteAlarmsForTask(taskId)
        Log.d(TAG, "Cancelled all alarms for task $taskId")
    }

    /**
     * Account-specific alarm cancel on user logout / account switch.
     */
    suspend fun cancelAllAlarmsForUser(userId: String) = withContext(Dispatchers.IO) {
        val alarms = database.scheduledAlarmDao().getActiveAlarmsFlow().let {
            database.scheduledAlarmDao().getUpcomingActiveAlarms(0L).filter { it.userId == userId }
        }
        for (record in alarms) {
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                record.alarmId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        database.scheduledAlarmDao().cancelAllAlarmsForUser(userId)
        Log.d(TAG, "Cancelled all alarms for user $userId")
    }

    /**
     * Reschedules all active upcoming alarms from local database for the currently logged in account (after reboot).
     */
    suspend fun restoreAlarmsAfterReboot() = withContext(Dispatchers.IO) {
        val currentUserId = userPrefs.getUserSession().userId ?: database.profileDao().getCachedProfile()?.id
        if (currentUserId.isNullOrBlank()) {
            Log.d(TAG, "No logged in user found after reboot. Skipping alarm restore.")
            return@withContext
        }

        val now = System.currentTimeMillis()
        val upcomingAlarms = database.scheduledAlarmDao().getUpcomingActiveAlarmsForUser(currentUserId, now)
        Log.d(TAG, "Restoring ${upcomingAlarms.size} active alarms after reboot for user $currentUserId")
        for (alarm in upcomingAlarms) {
            val taskEntity = database.taskDao().getTaskById(alarm.taskId)
            val task = taskEntity?.toDomain()
            if (task != null &&
                !task.status.equals("completed", ignoreCase = true) &&
                !task.status.equals("cancelled", ignoreCase = true)
            ) {
                setNativeAlarm(alarm, task)
            }
        }
    }

    private fun setNativeAlarm(alarmEntity: ScheduledAlarmRecordEntity, task: TaskItem) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_TASK_ID, alarmEntity.taskId)
            putExtra(EXTRA_TASK_TITLE, alarmEntity.taskTitle)
            putExtra(EXTRA_IMPORTANCE, alarmEntity.importance)
            putExtra(EXTRA_ALARM_TYPE, alarmEntity.alarmType)
            putExtra(EXTRA_REMINDER_LABEL, alarmEntity.reminderLabel)
            putExtra(EXTRA_ALARM_ID, alarmEntity.alarmId)
            putExtra(EXTRA_SCHEDULED_TIME, task.executionTime ?: task.startsAt ?: task.dueAt)
            putExtra(EXTRA_DESCRIPTION, task.description)
            putExtra(EXTRA_LOCATION, task.location)
            putExtra(EXTRA_TASK_TYPE, task.taskType.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmEntity.alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleWithAlarmManager(alarmEntity.triggerAtMillis, pendingIntent)
    }

    private fun scheduleWithAlarmManager(triggerMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    // Graceful fallback to inexact when exact alarm permission is not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission unavailable, falling back to setWindow/set: ${e.message}")
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (err: Exception) {
                Log.e(TAG, "Failed to schedule alarm: ${err.message}")
            }
        }
    }

    private fun generateAlarmId(taskId: String, offset: Int): Int {
        val hash = taskId.hashCode()
        return (hash and 0x7FFFFFFF) / 10000 * 10000 + (offset and 0x1FFF)
    }
}
