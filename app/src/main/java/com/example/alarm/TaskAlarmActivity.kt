package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class TaskAlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLockScreenFlags()
        startAlarmAudioAndVibration()

        val taskId = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_ID) ?: ""
        val title = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Action Required"
        val importance = intent.getStringExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE) ?: "NORMAL"
        val reminderLabel = intent.getStringExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL) ?: "Scheduled Alarm"
        val scheduledTime = intent.getStringExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME) ?: ""
        val description = intent.getStringExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION) ?: ""
        val location = intent.getStringExtra(TaskAlarmScheduler.EXTRA_LOCATION) ?: ""
        val taskType = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE) ?: "SIMPLE"
        val alarmId = intent.getIntExtra(TaskAlarmScheduler.EXTRA_ALARM_ID, taskId.hashCode())

        setContent {
            MyApplicationTheme {
                TaskAlarmScreen(
                    title = title,
                    importance = importance,
                    reminderLabel = reminderLabel,
                    scheduledTime = scheduledTime,
                    description = description,
                    location = location,
                    taskType = taskType,
                    onAcknowledge = {
                        stopAlarmAudioAndVibration()
                        recordAcknowledgement(taskId)
                        finish()
                    },
                    onSnooze = { minutes ->
                        stopAlarmAudioAndVibration()
                        snoozeAlarm(taskId, title, importance, minutes)
                        finish()
                    },
                    onOpenTask = {
                        stopAlarmAudioAndVibration()
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onMarkCompleted = {
                        stopAlarmAudioAndVibration()
                        markTaskCompleted(taskId)
                        finish()
                    }
                )
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startAlarmAudioAndVibration() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means loop
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            // Ignore sound initialization issues
        }
    }

    private fun stopAlarmAudioAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun recordAcknowledgement(taskId: String) {
        val db = TaskzDatabase.getInstance(applicationContext)
        val context = applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = db.profileDao().getCachedProfile()
                val empId = profile?.employeeId ?: "EMP_UNKNOWN"
                val userId = profile?.id ?: UUID.randomUUID().toString()
                val nowIso = java.time.Instant.now().toString()
                val eventId = UUID.randomUUID().toString()

                val ack = TaskAcknowledgementEntity(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    userId = userId,
                    employeeId = empId,
                    eventId = eventId,
                    deviceTimestamp = System.currentTimeMillis(),
                    acknowledgedAt = nowIso,
                    syncStatus = "PENDING_SYNC"
                )
                db.taskResponseDao().insertAcknowledgement(ack)
                db.taskDao().markTaskAcknowledged(taskId, true, nowIso)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun snoozeAlarm(taskId: String, title: String, importance: String, minutes: Int) {
        val scheduler = TaskAlarmScheduler(applicationContext)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            scheduler.scheduleSnoozeAlarm(taskId, title, importance, "FULL_SCREEN_ALARM", minutes)
        }
    }

    private fun markTaskCompleted(taskId: String) {
        val db = TaskzDatabase.getInstance(applicationContext)
        val scheduler = TaskAlarmScheduler(applicationContext)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            scheduler.cancelAlarmsForTask(taskId)
            db.taskDao().updateTaskStatus(taskId, "completed")
        }
    }

    override fun onDestroy() {
        stopAlarmAudioAndVibration()
        super.onDestroy()
    }
}

@Composable
fun TaskAlarmScreen(
    title: String,
    importance: String,
    reminderLabel: String,
    scheduledTime: String,
    description: String,
    location: String,
    taskType: String,
    onAcknowledge: () -> Unit,
    onSnooze: (Int) -> Unit,
    onOpenTask: () -> Unit,
    onMarkCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCritical = importance.equals("CRITICAL", ignoreCase = true)
    val isHigh = importance.equals("HIGH", ignoreCase = true)

    val primaryBgColor = when {
        isCritical -> Color(0xFF1E0A0A)
        isHigh -> Color(0xFF1F1206)
        else -> MaterialTheme.colorScheme.background
    }

    val accentColor = when {
        isCritical -> TaskzError
        isHigh -> TaskzWarning
        else -> MaterialTheme.colorScheme.primary
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmPulse"
    )

    var showSnoozeOptions by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(primaryBgColor)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Pulsing Alarm Icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(3.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCritical) Icons.Default.NotificationImportant else Icons.Default.Alarm,
                        contentDescription = "Alarm Active",
                        tint = accentColor,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Importance Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "$importance PRIORITY • $reminderLabel".uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Task Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Type & Schedule row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = taskType.replace("_", " "),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (scheduledTime.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = scheduledTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary: Acknowledge (Stops alarm, does NOT complete task)
                Button(
                    onClick = onAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("alarm_btn_acknowledge")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACKNOWLEDGE ALARM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Mark Completed button
                OutlinedButton(
                    onClick = onMarkCompleted,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TaskzSuccess),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("alarm_btn_complete")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "MARK TASK COMPLETED", fontWeight = FontWeight.SemiBold)
                }

                // Snooze & Open Task row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSnoozeOptions = !showSnoozeOptions },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("alarm_btn_snooze")
                    ) {
                        Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Snooze")
                    }

                    Button(
                        onClick = onOpenTask,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("alarm_btn_open_task")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Open Task", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Snooze options selector
                AnimatedVisibility(visible = showSnoozeOptions) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Select Snooze Duration:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(2, 5, 10).forEach { mins ->
                                    Button(
                                        onClick = { onSnooze(mins) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("snooze_${mins}m")
                                    ) {
                                        Text("${mins}m")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
