package com.example.alarm

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.core.model.AlarmType
import com.example.core.model.TaskImportance

class TaskAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        stopAndRelease()
        stopSelf()
    }

    companion object {
        private const val TAG = "TaskAlarmService"
        const val ACTION_START_ALARM = "com.example.alarm.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.alarm.ACTION_STOP_ALARM"
        private const val MAX_RING_DURATION_MS = 2 * 60 * 1000L

        @Volatile
        var isRinging: Boolean = false
            private set

        fun stopAlarm(context: Context) {
            isRinging = false
            try {
                context.stopService(Intent(context, TaskAlarmService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop TaskAlarmService: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAndRelease()
            stopSelf()
            return START_NOT_STICKY
        }

        val taskId = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_ID) ?: ""
        val title = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Alarm"
        val importanceStr = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE) ?: TaskImportance.NORMAL.name
        val alarmTypeStr = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE) ?: AlarmType.SOUND_ALERT.name
        val reminderLabel = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL) ?: "Scheduled Time"
        val scheduledTime = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""
        val description = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION) ?: ""
        val location = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_LOCATION) ?: ""
        val taskType = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE) ?: "SIMPLE"
        val responseTypeStr = intent?.getStringExtra(TaskAlarmScheduler.EXTRA_RESPONSE_TYPE) ?: "COMPLETED_NOT_COMPLETED"
        val reminderInterval = intent?.getIntExtra(TaskAlarmScheduler.EXTRA_REMINDER_INTERVAL, 10) ?: 10
        val alarmId = intent?.getIntExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, taskId.hashCode()) ?: 1001

        val importance = runCatching { TaskImportance.valueOf(importanceStr) }.getOrDefault(TaskImportance.NORMAL)
        val alarmType = runCatching { AlarmType.valueOf(alarmTypeStr) }.getOrDefault(AlarmType.SOUND_ALERT)

        TaskAlarmReceiver.createNotificationChannels(this)

        val channelId = when {
            alarmType == AlarmType.SILENT -> TaskAlarmReceiver.CHANNEL_ID_SILENT
            importance == TaskImportance.CRITICAL || alarmType == AlarmType.FULL_SCREEN_ALARM ->
                TaskAlarmReceiver.CHANNEL_ID_CRITICAL_ALARM
            importance == TaskImportance.HIGH || alarmType == AlarmType.SOUND_ALERT ->
                TaskAlarmReceiver.CHANNEL_ID_HIGH_ALARM
            else -> TaskAlarmReceiver.CHANNEL_ID_NORMAL
        }

        val fullScreenIntent = Intent(this, TaskAlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, title)
            putExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE, importanceStr)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE, alarmTypeStr)
            putExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL, reminderLabel)
            putExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION, description)
            putExtra(TaskAlarmScheduler.EXTRA_LOCATION, location)
            putExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE, taskType)
            putExtra(TaskAlarmScheduler.EXTRA_RESPONSE_TYPE, responseTypeStr)
            putExtra(TaskAlarmScheduler.EXTRA_REMINDER_INTERVAL, reminderInterval)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId * 10 + 4,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            alarmId * 10 + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = Intent(this, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmScheduler.ACTION_ACKNOWLEDGE
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId * 10 + 2,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmScheduler.ACTION_SNOOZE
            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
            putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, title)
            putExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE, importanceStr)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE, alarmTypeStr)
            putExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId * 10 + 3,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = Intent(this, TaskAlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId * 10 + 5,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shouldFullScreen = alarmType == AlarmType.FULL_SCREEN_ALARM ||
            importance == TaskImportance.CRITICAL ||
            importance == TaskImportance.HIGH

        val subtext = "[$importanceStr] $reminderLabel"
        val bodyText = buildString {
            if (location.isNotBlank()) append("Location: $location • ")
            if (description.isNotBlank()) append(description) else append("Immediate action required.")
        }

        val notifBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setSubText(subtext)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\nScheduled: $scheduledTime"))
            .setPriority(
                if (shouldFullScreen) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(if (shouldFullScreen) fullScreenPendingIntent else openAppPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Acknowledge", ackPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Snooze 5m", snoozePendingIntent)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)

        if (shouldFullScreen) {
            notifBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val notifId = if (alarmId == 0) 1001 else alarmId
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, notifId, notifBuilder.build(), fgsType)

        acquireWakeLock()

        val shouldPlaySound = alarmType != AlarmType.SILENT &&
            alarmType != AlarmType.NORMAL_NOTIFICATION &&
            (importance == TaskImportance.CRITICAL ||
                alarmType == AlarmType.SOUND_ALERT ||
                (alarmType == AlarmType.FULL_SCREEN_ALARM && importance != TaskImportance.HIGH))
        val shouldVibrate = alarmType != AlarmType.SILENT

        stopAudioAndVibration()
        if (shouldPlaySound) {
            startLoopingAlarmSound()
        }
        if (shouldVibrate) {
            startAlarmVibration()
        }

        isRinging = true
        autoStopHandler.removeCallbacks(autoStopRunnable)
        autoStopHandler.postDelayed(autoStopRunnable, MAX_RING_DURATION_MS)

        if (shouldFullScreen) {
            launchFullScreenIfPermitted(fullScreenIntent)
        }

        return START_NOT_STICKY
    }

    private fun launchFullScreenIfPermitted(fullScreenIntent: Intent) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val canDirectLaunch = Settings.canDrawOverlays(this) ||
            keyguardManager.isKeyguardLocked ||
            !powerManager.isInteractive ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        if (canDirectLaunch) {
            try {
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Direct full-screen Activity start deferred to fullScreenIntent: ${e.message}")
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock?.isHeld != true) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Taskz::AlarmWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(3 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun startLoopingAlarmSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(applicationContext, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed, falling back to Ringtone: ${e.message}")
            try {
                fallbackRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    audioAttributes = attrs
                    play()
                }
            } catch (re: Exception) {
                Log.e(TAG, "Ringtone fallback failed: ${re.message}")
            }
        }
    }

    private fun startAlarmVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 250, 600, 250, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration start failed: ${e.message}")
        }
    }

    private fun stopAudioAndVibration() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            fallbackRingtone?.stop()
        } catch (_: Exception) {}
        fallbackRingtone = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun stopAndRelease() {
        isRinging = false
        autoStopHandler.removeCallbacks(autoStopRunnable)
        stopAudioAndVibration()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onDestroy() {
        stopAndRelease()
        super.onDestroy()
    }
}
