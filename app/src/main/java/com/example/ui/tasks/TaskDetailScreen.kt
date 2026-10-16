package com.example.ui.tasks

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.TaskAlarmActivity
import com.example.alarm.TaskAlarmScheduler
import com.example.core.model.LanProgressItem
import com.example.core.model.ResponseType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskResponseItem
import com.example.core.model.TaskRoleRequirement
import com.example.core.model.TaskStatus
import com.example.core.model.TaskType
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    task: TaskItem,
    viewModel: TasksViewModel,
    onViewParticipantsClick: (String) -> Unit,
    onViewJoinRequestsClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val requirements by viewModel.taskRequirements.collectAsStateWithLifecycle()
    val participants by viewModel.taskParticipants.collectAsStateWithLifecycle()
    val joinRequests by viewModel.taskJoinRequests.collectAsStateWithLifecycle()
    val activityLogs by viewModel.taskActivityLog.collectAsStateWithLifecycle()
    val taskResponses by viewModel.taskResponses.collectAsStateWithLifecycle()
    val lanProgress by viewModel.lanProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val directoryEmployees by viewModel.directoryEmployees.collectAsStateWithLifecycle()

    val currentTask = selectedTask ?: task
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialogs state
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    // Join Task Inputs
    var selectedRoleIdToJoin by remember { mutableStateOf("") }
    var joinMessage by remember { mutableStateOf("") }
    var leaveReason by remember { mutableStateOf("") }
    var selectedEmployeeIdToAssign by remember { mutableStateOf("") }
    var selectedRoleIdToAssign by remember { mutableStateOf("") }
    var assignSearchQuery by remember { mutableStateOf("") }
    var completionNote by remember { mutableStateOf("") }

    // Phase 3 Response Inputs
    var responsePercentage by remember { mutableFloatStateOf(currentTask.currentProgress.toFloat()) }
    var responseNumeric1 by remember { mutableStateOf("") }
    var responseNumeric2 by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var responseYesNo by remember { mutableStateOf("YES") }
    var selectedLanForResponse by remember { mutableStateOf(currentTask.lanTargetList.firstOrNull() ?: "LAN 1") }

    LaunchedEffect(currentTask.id) {
        viewModel.loadTaskDetails(currentTask.id)
        viewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Task Operations",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Primary Info Card with Phase 3 Status
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Status, Importance, and Type Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display Status Badge
                        val displayStatus = currentTask.displayStatus
                        val statusColor = when (displayStatus) {
                            TaskStatus.COMPLETED -> TaskzSuccess
                            TaskStatus.OVERDUE, TaskStatus.ACTION_REQUIRED -> TaskzError
                            TaskStatus.UPCOMING, TaskStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                            TaskStatus.IN_PROGRESS -> Color(0xFFF57C00)
                            TaskStatus.ACKNOWLEDGED -> Color(0xFF0288D1)
                            TaskStatus.PENDING_CONFIRMATION -> TaskzWarning
                            TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = displayStatus.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = statusColor),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Importance Badge
                        val importanceColor = when (currentTask.importance) {
                            TaskImportance.CRITICAL -> TaskzError
                            TaskImportance.HIGH -> TaskzWarning
                            TaskImportance.NORMAL -> MaterialTheme.colorScheme.primary
                            TaskImportance.LOW -> MaterialTheme.colorScheme.outline
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = importanceColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${currentTask.importance.label} Priority",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = importanceColor),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Task Type Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = currentTask.taskType.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentTask.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    if (!currentTask.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentTask.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!currentTask.groupTitle.isNullOrBlank() || !currentTask.shiftName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = listOfNotNull(currentTask.groupTitle, currentTask.shiftName).joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }

                    if (!currentTask.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = currentTask.location!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Execution or Start / Due Time
                    val timeStr = currentTask.executionTime ?: "${currentTask.startsAt ?: ""} → ${currentTask.dueAt ?: ""}".trim()
                    if (timeStr.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Execution / Schedule: $timeStr",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================================
            // TIME-CRITICAL ALARM & ACKNOWLEDGEMENT CARD
            // ============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentTask.isAcknowledged) TaskzSuccess.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentTask.isAcknowledged) TaskzSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (currentTask.isAcknowledged) Icons.Default.CheckCircle else Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (currentTask.isAcknowledged) TaskzSuccess else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentTask.isAcknowledged) "Alarm Acknowledged" else "Alarm & Device Schedule",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Test Alarm Trigger Button (Allows verifying full-screen intent / sound alert)
                        TextButton(
                            onClick = {
                                val alarmIntent = Intent(context, TaskAlarmActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, currentTask.id)
                                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, currentTask.title)
                                    putExtra(TaskAlarmScheduler.EXTRA_IMPORTANCE, currentTask.importance.name)
                                    putExtra(TaskAlarmScheduler.EXTRA_ALARM_TYPE, currentTask.alarmType.name)
                                    putExtra(TaskAlarmScheduler.EXTRA_REMINDER_LABEL, "Manual Alarm Test")
                                    putExtra(TaskAlarmScheduler.EXTRA_SCHEDULED_TIME, currentTask.executionTime ?: currentTask.dueAt ?: "")
                                    putExtra(TaskAlarmScheduler.EXTRA_DESCRIPTION, currentTask.description ?: "")
                                    putExtra(TaskAlarmScheduler.EXTRA_LOCATION, currentTask.location ?: "")
                                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TYPE, currentTask.taskType.name)
                                }
                                context.startActivity(alarmIntent)
                            }
                        ) {
                            Text("Test Alarm", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Alarm Type: ${currentTask.alarmType.label} • Reminders: ${currentTask.advanceRemindersMinutes.joinToString(", ") { if (it == 0) "At task time" else "$it mins before" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!currentTask.isAcknowledged) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.acknowledgeAlarm(currentTask.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("btn_acknowledge_alarm")
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Acknowledge Alarm")
                            }

                            OutlinedButton(
                                onClick = { viewModel.snoozeAlarm(currentTask.id, 5) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_snooze_detail")
                            ) {
                                Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Snooze 5m")
                            }
                        }
                    } else {
                        Text(
                            text = "✓ Acknowledged on ${currentTask.acknowledgedAt ?: "device"}. Task completion remains separate.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TaskzSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================================
            // RESPONSE SUBMISSION / COMPLETION WORKFLOW
            // ============================================================
            Text(
                text = "Task Response (${currentTask.responseType.label})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (currentTask.responseType) {
                        ResponseType.PERCENTAGE -> {
                            Text(
                                text = "Current Progress: ${responsePercentage.toInt()}% / ${currentTask.targetPercentage ?: 100}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (responsePercentage / (currentTask.targetPercentage ?: 100)).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = TaskzSuccess
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Slider(
                                value = responsePercentage,
                                onValueChange = { responsePercentage = it },
                                valueRange = 0f..100f,
                                steps = 19
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(25f, 50f, 75f, 100f).forEach { p ->
                                    FilterChip(
                                        selected = responsePercentage.toInt() == p.toInt(),
                                        onClick = { responsePercentage = p },
                                        label = { Text("${p.toInt()}%") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.submitTaskResponse(
                                        taskId = currentTask.id,
                                        responseType = ResponseType.PERCENTAGE,
                                        percentage = responsePercentage.toInt()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Progress to ${responsePercentage.toInt()}%")
                            }
                        }

                        ResponseType.NUMERIC_COUNT -> {
                            Text("Submit numeric count records:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            TaskzTextField(
                                value = responseNumeric1,
                                onValueChange = { responseNumeric1 = it },
                                label = "Registered Candidates Count",
                                placeholder = "e.g., 250"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TaskzTextField(
                                value = responseNumeric2,
                                onValueChange = { responseNumeric2 = it },
                                label = "Pending / Absent Candidates Count",
                                placeholder = "e.g., 12"
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.submitTaskResponse(
                                        taskId = currentTask.id,
                                        responseType = ResponseType.NUMERIC_COUNT,
                                        numeric1 = responseNumeric1.toDoubleOrNull(),
                                        numeric2 = responseNumeric2.toDoubleOrNull()
                                    )
                                    responseNumeric1 = ""
                                    responseNumeric2 = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Submit Count Update")
                            }
                        }

                        ResponseType.YES_NO -> {
                            Text("Confirm operational readiness:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.submitTaskResponse(
                                            taskId = currentTask.id,
                                            responseType = ResponseType.YES_NO,
                                            responseValue = "YES"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("YES / CONFIRMED")
                                }
                                Button(
                                    onClick = {
                                        viewModel.submitTaskResponse(
                                            taskId = currentTask.id,
                                            responseType = ResponseType.YES_NO,
                                            responseValue = "NO"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TaskzError),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("NO / ISSUE")
                                }
                            }
                        }

                        ResponseType.TEXT_RESPONSE -> {
                            TaskzTextField(
                                value = responseText,
                                onValueChange = { responseText = it },
                                label = "Operational Status Report",
                                placeholder = "Enter brief status report..."
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.submitTaskResponse(
                                        taskId = currentTask.id,
                                        responseType = ResponseType.TEXT_RESPONSE,
                                        textValue = responseText
                                    )
                                    responseText = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Submit Report")
                            }
                        }

                        ResponseType.MULTI_CONFIRMATION -> {
                            Text("Confirm individual LAN or role readiness:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (currentTask.lanTargetList.isNotEmpty()) {
                                Text("Select your assigned LAN:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    currentTask.lanTargetList.forEach { lan ->
                                        FilterChip(
                                            selected = selectedLanForResponse == lan,
                                            onClick = { selectedLanForResponse = lan },
                                            label = { Text(lan) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Button(
                                onClick = {
                                    viewModel.submitTaskResponse(
                                        taskId = currentTask.id,
                                        responseType = ResponseType.MULTI_CONFIRMATION,
                                        responseValue = "CONFIRMED",
                                        lanNumber = if (currentTask.lanTargetList.isNotEmpty()) selectedLanForResponse else null
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirm ${if (currentTask.lanTargetList.isNotEmpty()) selectedLanForResponse else "Readiness"}")
                            }
                        }

                        else -> {
                            // Completed / Not Completed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (currentTask.requiresCompletionConfirmation) {
                                            showCompleteDialog = true
                                        } else {
                                            viewModel.submitTaskResponse(
                                                taskId = currentTask.id,
                                                responseType = ResponseType.COMPLETED_NOT_COMPLETED,
                                                responseValue = "COMPLETED"
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.TaskAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mark Task Completed")
                                }
                            }
                        }
                    }
                }
            }

            // ============================================================
            // MULTI-CONFIRMATION & LAN-WISE DASHBOARD
            // ============================================================
            if (currentTask.lanTargetList.isNotEmpty() || currentTask.taskType == TaskType.MULTI_CONFIRMATION) {
                Spacer(modifier = Modifier.height(16.dp))
                val confirmedCount = lanProgress.count { it.isConfirmed }
                val totalCount = currentTask.lanTargetList.size.coerceAtLeast(1)
                val percentConfirmed = ((confirmedCount.toFloat() / totalCount) * 100).toInt()

                Text(
                    text = "LAN Confirmation Dashboard",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Overall Status: $confirmedCount / $totalCount Confirmed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$percentConfirmed%",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (percentConfirmed == 100) TaskzSuccess else Color(0xFFF57C00)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { confirmedCount.toFloat() / totalCount },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (percentConfirmed == 100) TaskzSuccess else Color(0xFFF57C00)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Individual LAN rows
                        lanProgress.forEach { lanItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (lanItem.isConfirmed) Icons.Default.CheckCircle else Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = if (lanItem.isConfirmed) TaskzSuccess else Color(0xFFF57C00),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = lanItem.lanNumber,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (lanItem.isConfirmed) {
                                    Text(
                                        text = "Confirmed • ${lanItem.employeeName ?: "Staff"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TaskzSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = TaskzWarning.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Pending",
                                            style = MaterialTheme.typography.labelSmall.copy(color = TaskzWarning, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ============================================================
            // PROGRESS & SUBMISSION HISTORY TIMELINE
            // ============================================================
            if (taskResponses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Submission History (${taskResponses.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        taskResponses.take(10).forEach { resp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val detail = when {
                                        resp.percentageValue != null -> "${resp.percentageValue}% completed"
                                        resp.numericValue1 != null -> "Count: ${resp.numericValue1?.toInt() ?: 0} (Pending: ${resp.numericValue2?.toInt() ?: 0})"
                                        !resp.textValue.isNullOrBlank() -> resp.textValue
                                        else -> resp.responseValue ?: "Confirmed"
                                    }
                                    Text(
                                        text = "${resp.employeeName ?: resp.employeeId}: $detail",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (resp.lanNumber != null) {
                                        Text(
                                            text = "Assigned: ${resp.lanNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = resp.submittedAt.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Center (Participants and Requests)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onViewParticipantsClick(currentTask.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Participants (${participants.filter { it.status == "active" }.size})", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { onViewJoinRequestsClick(currentTask.id) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Requests (${joinRequests.size})", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Completion Confirmation Dialog
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Submit Task Completion") },
            text = {
                Column {
                    Text("Please provide verification notes or proof of completion:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = completionNote,
                        onValueChange = { completionNote = it },
                        label = { Text("Completion Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitCompletion(currentTask.id, completionNote)
                        showCompleteDialog = false
                    }
                ) {
                    Text("Submit for Review")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
