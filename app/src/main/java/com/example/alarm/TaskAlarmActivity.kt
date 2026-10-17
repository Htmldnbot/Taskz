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
import android.widget.Toast
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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.CompletionPolicy
import com.example.core.model.ResponseType
import com.example.core.model.TaskResponseItem
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.data.local.entity.TaskResponseEntity
import com.example.data.remote.SupabaseClient
import com.example.sync.SyncWorker
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

enum class AlarmActionState {
    IDLE,
    PROCESSING,
    RESPONSE_SAVED_PENDING_SYNC,
    PENDING_CREATOR_CONFIRMATION,
    SERVER_CONFIRMED,
    ERROR
}

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

        setContent {
            MyApplicationTheme {
                var actionState by remember { mutableStateOf(AlarmActionState.IDLE) }
                var statusMessage by remember { mutableStateOf("") }
                val coroutineScope = rememberCoroutineScope()

                TaskAlarmScreen(
                    title = title,
                    importance = importance,
                    reminderLabel = reminderLabel,
                    scheduledTime = scheduledTime,
                    description = description,
                    location = location,
                    taskType = taskType,
                    actionState = actionState,
                    statusMessage = statusMessage,
                    onAcknowledge = {
                        stopAlarmAudioAndVibration()
                        coroutineScope.launch {
                            recordAcknowledgement(taskId)
                            finish()
                        }
                    },
                    onSnooze = { minutes ->
                        stopAlarmAudioAndVibration()
                        snoozeAlarm(taskId, title, importance, minutes)
                        finish()
                    },
                    onOpenTask = {
                        stopAlarmAudioAndVibration()
                        val mainIntent = Intent(this@TaskAlarmActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onMarkCompleted = {
                        stopAlarmAudioAndVibration()
                        actionState = AlarmActionState.PROCESSING
                        coroutineScope.launch {
                            handleCompleteAction(
                                taskId = taskId,
                                onStateChange = { newState, msg ->
                                    actionState = newState
                                    statusMessage = msg
                                }
                            )
                        }
                    },
                    onDismiss = {
                        stopAlarmAudioAndVibration()
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
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmAudioAndVibration() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private suspend fun recordAcknowledgement(taskId: String) = withContext(Dispatchers.IO) {
        val db = TaskzDatabase.getInstance(applicationContext)
        val userPrefs = UserPreferencesDataStore(applicationContext)
        val session = userPrefs.getUserSession()
        val profile = db.profileDao().getCachedProfile()
        val userId = session.userId ?: profile?.id ?: return@withContext
        val empId = profile?.employeeId ?: ""
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

        // Enqueue in durable outbox
        val payload = JSONObject().apply {
            put("deviceTimestamp", nowIso)
        }
        val outboxEvent = OutboxEventEntity(
            eventId = eventId,
            userId = userId,
            taskId = taskId,
            operationType = "TASK_ACKNOWLEDGEMENT",
            payloadJson = payload.toString(),
            syncStatus = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        db.outboxDao().enqueueEvent(outboxEvent)

        // Attempt instant sync if token available
        val token = session.accessToken
        if (!token.isNullOrBlank()) {
            val res = SupabaseClient().acknowledgeTaskAlarm(token, taskId, eventId, nowIso)
            if (res.isSuccess) {
                db.taskResponseDao().markAcknowledgementSynced(ack.id)
                db.outboxDao().markEventSynced(outboxEvent.id, "ACK_CONFIRMED", System.currentTimeMillis())
            } else {
                SyncWorker.enqueueSync(applicationContext)
            }
        } else {
            SyncWorker.enqueueSync(applicationContext)
        }
    }

    private fun snoozeAlarm(taskId: String, title: String, importance: String, minutes: Int) {
        val scheduler = TaskAlarmScheduler(applicationContext)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            scheduler.scheduleSnoozeAlarm(taskId, title, importance, "FULL_SCREEN_ALARM", minutes)
        }
    }

    /**
     * PART 3 — FIX ALARM COMPLETION
     * Validates identity, creates idempotent outbox entry, never creates false completion.
     */
    private suspend fun handleCompleteAction(
        taskId: String,
        onStateChange: (AlarmActionState, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val db = TaskzDatabase.getInstance(applicationContext)
        val userPrefs = UserPreferencesDataStore(applicationContext)
        val session = userPrefs.getUserSession()
        val profile = db.profileDao().getCachedProfile()

        val userId = session.userId ?: profile?.id
        if (userId.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                onStateChange(AlarmActionState.ERROR, "Authentication required")
            }
            return@withContext
        }

        val empId = profile?.employeeId ?: ""
        val empName = profile?.fullName ?: empId
        val nowIso = java.time.Instant.now().toString()
        val idempotencyKey = UUID.randomUUID().toString()

        val task = db.taskDao().getTaskById(taskId)?.toDomain()
        val requiresConfirmation = task?.completionPolicy == CompletionPolicy.CREATOR_CONFIRMATION ||
                task?.requiresCompletionConfirmation == true

        // 1. Save response event locally in Room cache
        val localResponse = TaskResponseEntity(
            id = idempotencyKey,
            taskId = taskId,
            userId = userId,
            employeeId = empId,
            employeeName = empName,
            lanNumber = null,
            responseType = ResponseType.COMPLETED_NOT_COMPLETED.name,
            responseValue = "COMPLETED",
            percentageValue = 100,
            numericValue1 = null,
            numericValue2 = null,
            textValue = "Marked completed from alarm",
            dynamicValuesJson = "{}",
            submittedAt = nowIso,
            syncStatus = "PENDING_SYNC"
        )
        db.taskResponseDao().insertResponse(localResponse)

        // 2. Put into durable outbox
        val payload = JSONObject().apply {
            put("responseType", ResponseType.COMPLETED_NOT_COMPLETED.name)
            put("responseValue", "COMPLETED")
            put("percentage", 100)
            put("textValue", "Marked completed from alarm")
            put("deviceTimestamp", nowIso)
        }
        val outboxId = db.outboxDao().enqueueEvent(
            OutboxEventEntity(
                eventId = idempotencyKey,
                userId = userId,
                taskId = taskId,
                operationType = "COMPLETION_REQUEST",
                payloadJson = payload.toString(),
                syncStatus = "PENDING",
                createdAt = System.currentTimeMillis()
            )
        )

        // 3. Attempt network submission if token available
        val token = session.accessToken
        var isServerConfirmed = false
        var serverStatus: String? = null

        if (!token.isNullOrBlank()) {
            val res = SupabaseClient().submitTaskResponse(
                accessToken = token,
                taskId = taskId,
                responseType = ResponseType.COMPLETED_NOT_COMPLETED.name,
                responseValue = "COMPLETED",
                percentage = 100,
                text = "Marked completed from alarm",
                idempotencyKey = idempotencyKey,
                deviceTimestamp = nowIso
            )

            if (res.isSuccess) {
                isServerConfirmed = true
                val serverObj = res.getOrThrow()
                serverStatus = serverObj.optString("task_status", "completed")
                db.outboxDao().markEventSynced(outboxId, idempotencyKey, System.currentTimeMillis())
                db.taskResponseDao().markResponseSynced(idempotencyKey)

                if (serverStatus.equals("pending_confirmation", ignoreCase = true) || requiresConfirmation) {
                    db.taskDao().updateTaskStatus(taskId, "pending_confirmation")
                } else if (serverStatus.equals("completed", ignoreCase = true)) {
                    db.taskDao().updateTaskStatus(taskId, "completed")
                    TaskAlarmScheduler(applicationContext).cancelAlarmsForTask(taskId)
                }
            } else {
                SyncWorker.enqueueSync(applicationContext)
            }
        } else {
            SyncWorker.enqueueSync(applicationContext)
        }

        withContext(Dispatchers.Main) {
            when {
                isServerConfirmed && (serverStatus == "pending_confirmation" || requiresConfirmation) -> {
                    onStateChange(AlarmActionState.PENDING_CREATOR_CONFIRMATION, "PENDING CREATOR CONFIRMATION")
                }
                isServerConfirmed -> {
                    onStateChange(AlarmActionState.SERVER_CONFIRMED, "SERVER CONFIRMED")
                }
                else -> {
                    onStateChange(AlarmActionState.RESPONSE_SAVED_PENDING_SYNC, "RESPONSE SAVED\nPENDING SYNC")
                }
            }
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
    actionState: AlarmActionState,
    statusMessage: String,
    onAcknowledge: () -> Unit,
    onSnooze: (Int) -> Unit,
    onOpenTask: () -> Unit,
    onMarkCompleted: () -> Unit,
    onDismiss: () -> Unit,
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
                        text = "$importance PRIORITY • $reminderLabel",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Task Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        lineHeight = 30.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (scheduledTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scheduled: $scheduledTime",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Status Message Box (PART 3)
                AnimatedVisibility(visible = actionState != AlarmActionState.IDLE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (actionState) {
                                AlarmActionState.SERVER_CONFIRMED -> TaskzSuccess.copy(alpha = 0.2f)
                                AlarmActionState.PENDING_CREATOR_CONFIRMATION -> TaskzWarning.copy(alpha = 0.2f)
                                AlarmActionState.RESPONSE_SAVED_PENDING_SYNC -> MaterialTheme.colorScheme.surfaceVariant
                                AlarmActionState.ERROR -> TaskzError.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (actionState) {
                                AlarmActionState.PROCESSING -> {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Processing...", style = MaterialTheme.typography.bodyMedium)
                                }
                                AlarmActionState.SERVER_CONFIRMED -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TaskzSuccess, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("SERVER CONFIRMED", fontWeight = FontWeight.Bold, color = TaskzSuccess)
                                    Text("Task completion confirmed by server", style = MaterialTheme.typography.bodySmall)
                                }
                                AlarmActionState.PENDING_CREATOR_CONFIRMATION -> {
                                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = TaskzWarning, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("PENDING CREATOR CONFIRMATION", fontWeight = FontWeight.Bold, color = TaskzWarning)
                                    Text("Response received. Awaiting creator approval.", style = MaterialTheme.typography.bodySmall)
                                }
                                AlarmActionState.RESPONSE_SAVED_PENDING_SYNC -> {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("RESPONSE SAVED\nPENDING SYNC", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    Text("Offline response stored. Will sync automatically when connected.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                }
                                AlarmActionState.ERROR -> {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = TaskzError, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(statusMessage, fontWeight = FontWeight.Bold, color = TaskzError)
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (actionState in listOf(AlarmActionState.SERVER_CONFIRMED, AlarmActionState.PENDING_CREATOR_CONFIRMATION, AlarmActionState.RESPONSE_SAVED_PENDING_SYNC)) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "DISMISS", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Acknowledge Button
                    Button(
                        onClick = onAcknowledge,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("alarm_btn_acknowledge")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "ACKNOWLEDGE ALARM", fontWeight = FontWeight.Bold)
                    }

                    // Mark Completed Button
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
}
