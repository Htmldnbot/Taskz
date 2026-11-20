package com.example.ui.tasks

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.TaskAlarmActivity
import com.example.alarm.TaskAlarmScheduler
import com.example.core.model.ResponseType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskStatus
import com.example.core.model.TaskType
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
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
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var showDeleteTaskDialog by remember { mutableStateOf(false) }

    // Join Task Inputs
    var selectedRoleIdToJoin by remember { mutableStateOf("") }
    var joinMessage by remember { mutableStateOf("") }
    var leaveReason by remember { mutableStateOf("") }
    var selectedEmployeeIdToAssign by remember { mutableStateOf("") }
    var selectedRoleIdToAssign by remember { mutableStateOf("") }
    var assignSearchQuery by remember { mutableStateOf("") }
    var completionNote by remember { mutableStateOf("") }

    // Phase 3 Response Inputs
    var responsePercentage by remember(currentTask.id, currentTask.currentProgress) {
        mutableFloatStateOf(currentTask.currentProgress.toFloat())
    }
    var selectedNextReminderMinutes by remember { mutableIntStateOf(10) }
    var responseNumeric1 by remember { mutableStateOf("") }
    var responseNumeric2 by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var responseYesNo by remember { mutableStateOf("YES") }
    var selectedLanForResponse by remember { mutableStateOf(currentTask.lanTargetList.firstOrNull() ?: "LAN 1") }

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(currentTask.id, currentTask.executionTime, currentTask.dueAt) {
        viewModel.loadTaskDetails(currentTask.id)
        viewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(currentTask.id, currentTask.executionTime, currentTask.dueAt, currentTask.startsAt) {
        currentTimeMillis = System.currentTimeMillis()
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val scheduledMillis = currentTask.scheduledExecutionMillis()
    val isLockedBeforeTime = currentTask.isBeforeScheduledExecution(currentTimeMillis)
    val unlockTimeText = scheduledMillis?.let {
        TaskItem.formatForDisplay(java.time.Instant.ofEpochMilli(it).toString())
    } ?: (currentTask.executionTime ?: currentTask.dueAt ?: "")
    val canEdit = viewModel.canEditTask(currentTask)
    val canAssign = viewModel.canAssignTask(currentTask)
    val canAcknowledge = viewModel.canAcknowledgeTask(currentTask)
    val canComplete = viewModel.canCompleteTask(currentTask)
    val isPendingSync = currentTask.localSyncState.equals("PENDING_SYNC", ignoreCase = true) ||
        currentTask.status.equals("pending_sync", ignoreCase = true)

    BackHandler(onBack = onBackClick)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    NothingDotMatrixText(
                        text = "TASK DETAILS",
                        fontSize = 19.sp
                    )
                }
                val canDelete = viewModel.canDeleteTask(currentTask)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canEdit && currentTask.status !in setOf("completed", "cancelled")) {
                        OutlinedButton(
                            onClick = { showEditTaskDialog = true },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, NothingTheme.colors.border),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("btn_edit_task")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Task", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = { showDeleteTaskDialog = true },
                            modifier = Modifier.testTag("btn_delete_task")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Task",
                                tint = NothingRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Primary Info Card with Phase 3 Status
            NothingHairlineCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                val displayStatus = currentTask.displayStatus
                val statusColor = when (displayStatus) {
                    TaskStatus.COMPLETED -> NothingGreen
                    TaskStatus.OVERDUE, TaskStatus.ACTION_REQUIRED -> NothingRed
                    TaskStatus.UPCOMING, TaskStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurface
                    TaskStatus.IN_PROGRESS, TaskStatus.PENDING_CONFIRMATION -> NothingAmber
                    TaskStatus.ACKNOWLEDGED -> NothingTheme.colors.textPrimary
                    TaskStatus.CANCELLED -> NothingTheme.colors.textSecondary
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NothingTheme.colors.surfaceElevated,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            NothingStatusDot(color = statusColor, size = 6.dp)
                            Text(
                                text = displayStatus.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    val importanceColor = when (currentTask.importance) {
                        TaskImportance.CRITICAL -> NothingRed
                        TaskImportance.HIGH -> NothingAmber
                        TaskImportance.NORMAL -> NothingTheme.colors.textPrimary
                        TaskImportance.LOW -> NothingTheme.colors.textSecondary
                    }
                    NothingTagChip(
                        text = "${currentTask.importance.label.uppercase()} PRIORITY",
                        contentColor = importanceColor,
                        borderColor = importanceColor.copy(alpha = 0.45f)
                    )

                    NothingTagChip(text = currentTask.taskType.label.uppercase())
                }

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NothingTagChip(text = "SCOPE: ${currentTask.scopeLevel.label.uppercase()}")
                    NothingTagChip(
                        text = if (isPendingSync) "PENDING SYNC" else "SYNCED",
                        contentColor = if (isPendingSync) NothingAmber else NothingGreen,
                        borderColor = (if (isPendingSync) NothingAmber else NothingGreen).copy(alpha = 0.45f)
                    )
                    currentTask.targetRoleCodes.forEach { roleCode ->
                        NothingTagChip(
                            text = com.example.core.model.TaskzRole.fromCode(roleCode).displayName.uppercase(),
                            containerColor = NothingTheme.colors.surface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = currentTask.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!currentTask.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentTask.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(10.dp))

                if (!currentTask.groupTitle.isNullOrBlank() || !currentTask.shiftName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = NothingTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = listOfNotNull(currentTask.groupTitle, currentTask.shiftName).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!currentTask.location.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = NothingTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentTask.location!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }

                val rawTime = currentTask.executionTime ?: currentTask.dueAt ?: currentTask.startsAt
                val formattedTime = rawTime?.let { TaskItem.formatForDisplay(it) ?: it }
                if (!formattedTime.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SCHEDULED: $formattedTime",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!currentTask.isAssignedToMe &&
                !currentTask.isPendingApproval &&
                currentTask.allowSelfJoin &&
                currentTask.status !in setOf("completed", "cancelled")
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                NothingHairlineCard(
                    shape = RoundedCornerShape(14.dp),
                    containerColor = NothingTheme.colors.surfaceElevated,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OPEN FOR TEAM PARTICIPATION",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (currentTask.joinRequiresApproval) {
                                    "Request to join this task (creator approval required)."
                                } else {
                                    "Join this task directly to collaborate & log updates."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                selectedRoleIdToJoin = requirements.firstOrNull()?.roleId ?: ""
                                joinMessage = ""
                                showJoinDialog = true
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingTheme.colors.accentPrimary,
                                contentColor = NothingTheme.colors.onAccentPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_join_task")
                        ) {
                            Text(
                                text = if (currentTask.joinRequiresApproval) "Request Join" else "Join Task",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (currentTask.isPendingApproval) {
                Spacer(modifier = Modifier.height(12.dp))
                NothingHairlineCard(
                    shape = RoundedCornerShape(12.dp),
                    containerColor = NothingTheme.colors.surfaceElevated,
                    borderColor = NothingAmber.copy(alpha = 0.55f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NothingStatusDot(color = NothingAmber, size = 7.dp)
                        Text(
                            text = "Your join request is awaiting approval.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================================
            // TIME-CRITICAL ALARM & ACKNOWLEDGEMENT CARD
            // PERCENTAGE tasks keep BOTH Acknowledge Alarm & Progress Response.
            // YES/NO & other non-percentage tasks use direct Task Response below.
            // ============================================================
            val isPercentageTask = currentTask.responseType == ResponseType.PERCENTAGE
            val effectiveReminderMins = (currentTask.overdueReminderIntervalMinutes
                ?: currentTask.repeatIntervalMinutes
                ?: 10).coerceAtLeast(1)

            NothingHairlineCard(
                shape = RoundedCornerShape(16.dp),
                containerColor = NothingTheme.colors.surface,
                borderColor = if (currentTask.isAcknowledged) NothingGreen.copy(alpha = 0.6f) else NothingTheme.colors.border,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (currentTask.isAcknowledged && isPercentageTask) Icons.Default.CheckCircle else Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (currentTask.isAcknowledged && isPercentageTask) NothingGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isPercentageTask && currentTask.isAcknowledged -> "ALARM ACKNOWLEDGED"
                                isPercentageTask -> "ALARM & ACKNOWLEDGEMENT"
                                else -> "ALARM & DEVICE SCHEDULE"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

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
                                putExtra(TaskAlarmScheduler.EXTRA_RESPONSE_TYPE, currentTask.responseType.name)
                                putExtra(TaskAlarmScheduler.EXTRA_REMINDER_INTERVAL, effectiveReminderMins)
                            }
                            context.startActivity(alarmIntent)
                        }
                    ) {
                        Text(
                            text = "Test Alarm",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Alarm Type: ${currentTask.alarmType.label} • Reminders: ${currentTask.advanceRemindersMinutes.joinToString(", ") { if (it == 0) "At task time" else "$it mins before" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )

                if (isPercentageTask) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!currentTask.isAcknowledged) {
                        if (isLockedBeforeTime) {
                            NothingHairlineCard(
                                shape = RoundedCornerShape(10.dp),
                                containerColor = NothingTheme.colors.surfaceElevated,
                                borderColor = NothingAmber.copy(alpha = 0.55f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .padding(bottom = 10.dp)
                                    .testTag("alarm_locked_banner")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = NothingAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Locked until scheduled time ($unlockTimeText). Acknowledge & Snooze unlock automatically at alarm time.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = NothingAmber
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.acknowledgeAlarm(currentTask.id) },
                                enabled = canAcknowledge && !isLockedBeforeTime,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingTheme.colors.accentPrimary,
                                    contentColor = NothingTheme.colors.onAccentPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_acknowledge_alarm")
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        !canAcknowledge -> "Oversight View Only"
                                        isLockedBeforeTime -> "Locked Until Time"
                                        else -> "Acknowledge Alarm"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.snoozeAlarm(currentTask.id, 5) },
                                enabled = canAcknowledge && !isLockedBeforeTime,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, NothingTheme.colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_snooze_detail")
                            ) {
                                Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Snooze 5m", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        val ackDisplay = currentTask.acknowledgedAt?.let { TaskItem.formatForDisplay(it) ?: it } ?: "device"
                        Text(
                            text = "✓ Acknowledged on $ackDisplay. Update your percentage progress below.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NothingGreen
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (currentTask.responseType == ResponseType.YES_NO) {
                            "Direct confirmation mode: Respond YES to complete, or NO to re-remind in ${effectiveReminderMins}m."
                        } else {
                            "Direct response mode: Submit your response directly in the Task Response section below."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ============================================================
            // RESPONSE SUBMISSION / COMPLETION WORKFLOW
            // ============================================================
            NothingSectionHeader(
                title = "TASK RESPONSE (${currentTask.responseType.label.uppercase()})",
                useDotMatrix = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            NothingHairlineCard(
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (!canComplete) {
                    NothingHairlineCard(
                        shape = RoundedCornerShape(10.dp),
                        containerColor = NothingTheme.colors.surfaceElevated,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("oversight_view_only_banner")
                    ) {
                        Text(
                            text = "View-Only Oversight: Your active role can monitor this scoped task, while execution responses are submitted by the assigned target role.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }

                if (isLockedBeforeTime) {
                    NothingHairlineCard(
                        shape = RoundedCornerShape(10.dp),
                        containerColor = NothingTheme.colors.surfaceElevated,
                        borderColor = NothingAmber.copy(alpha = 0.55f),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("response_locked_banner")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = NothingAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Task confirmation is locked until scheduled time ($unlockTimeText).",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = NothingAmber
                            )
                        }
                    }
                }

                val effectiveDetailResponseType = if (currentTask.taskType == TaskType.PROGRESS) {
                    ResponseType.PERCENTAGE
                } else {
                    currentTask.responseType
                }
                when (effectiveDetailResponseType) {
                    ResponseType.PERCENTAGE -> {
                        val targetPct = (currentTask.targetPercentage ?: 100).coerceAtLeast(1)
                        val pctInt = responsePercentage.toInt()
                        val needsNextReminder = pctInt < targetPct

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROGRESS",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NothingTheme.colors.textSecondary
                            )
                            NothingDotMatrixText(
                                text = "$pctInt% / $targetPct%",
                                fontSize = 18.sp,
                                color = if (pctInt >= targetPct) NothingGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (responsePercentage / targetPct).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (pctInt >= targetPct) NothingGreen else MaterialTheme.colorScheme.onSurface,
                            trackColor = NothingTheme.colors.borderSubtle
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Slider(
                            value = responsePercentage,
                            onValueChange = { responsePercentage = it },
                            enabled = canComplete && !isLockedBeforeTime,
                            valueRange = 0f..100f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = NothingTheme.colors.accentPrimary,
                                activeTrackColor = NothingTheme.colors.accentPrimary,
                                inactiveTrackColor = NothingTheme.colors.border
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(25f, 50f, 75f, 100f).forEach { p ->
                                FilterChip(
                                    selected = pctInt == p.toInt(),
                                    onClick = { responsePercentage = p },
                                    enabled = canComplete && !isLockedBeforeTime,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NothingTheme.colors.accentPrimary,
                                        selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                        containerColor = NothingTheme.colors.surfaceElevated,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = canComplete && !isLockedBeforeTime,
                                        selected = pctInt == p.toInt(),
                                        borderColor = NothingTheme.colors.border,
                                        selectedBorderColor = NothingTheme.colors.accentPrimary
                                    ),
                                    label = { Text("${p.toInt()}%", fontFamily = TechMonoFontFamily) },
                                    modifier = Modifier.weight(1f).testTag("task_detail_pct_chip_${p.toInt()}")
                                )
                            }
                        }

                        AnimatedVisibility(visible = needsNextReminder && canComplete && !isLockedBeforeTime) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "NEXT REMINDER IN:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NothingTheme.colors.textSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(5, 10, 15, 30).forEach { mins ->
                                        FilterChip(
                                            selected = selectedNextReminderMinutes == mins,
                                            onClick = { selectedNextReminderMinutes = mins },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = NothingTheme.colors.accentPrimary,
                                                selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                                containerColor = NothingTheme.colors.surfaceElevated,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selectedNextReminderMinutes == mins,
                                                borderColor = NothingTheme.colors.border,
                                                selectedBorderColor = NothingTheme.colors.accentPrimary
                                            ),
                                            label = { Text("${mins}m", fontFamily = TechMonoFontFamily) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("task_detail_next_reminder_${mins}m")
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.submitTaskResponse(
                                    taskId = currentTask.id,
                                    responseType = ResponseType.PERCENTAGE,
                                    percentage = pctInt,
                                    nextReminderMinutes = if (needsNextReminder) selectedNextReminderMinutes else null
                                )
                            },
                            enabled = canComplete && !isLockedBeforeTime,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingTheme.colors.accentPrimary,
                                contentColor = NothingTheme.colors.onAccentPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("task_detail_submit_percentage_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (needsNextReminder) {
                                    "Save $pctInt% & Remind in ${selectedNextReminderMinutes}m"
                                } else {
                                    "Complete ($pctInt%)"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ResponseType.NUMERIC_COUNT -> {
                        Text(
                            "Submit numeric count records:",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TaskzTextField(
                            value = responseNumeric1,
                            onValueChange = { if (canComplete && !isLockedBeforeTime) responseNumeric1 = it },
                            label = "Registered Candidates Count",
                            placeholder = "e.g., 250"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TaskzTextField(
                            value = responseNumeric2,
                            onValueChange = { if (canComplete && !isLockedBeforeTime) responseNumeric2 = it },
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
                            enabled = canComplete && !isLockedBeforeTime,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingTheme.colors.accentPrimary,
                                contentColor = NothingTheme.colors.onAccentPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Submit Count Update", fontWeight = FontWeight.Bold)
                        }
                    }

                    ResponseType.YES_NO -> {
                        val hasConfirmedYes = currentTask.status.equals("completed", ignoreCase = true) ||
                            taskResponses.any { it.responseValue.equals("YES", ignoreCase = true) }
                        val latestResponse = taskResponses.firstOrNull()
                        val hasRecentNo = !hasConfirmedYes &&
                            latestResponse?.responseValue.equals("NO", ignoreCase = true)

                        if (hasConfirmedYes) {
                            NothingHairlineCard(
                                shape = RoundedCornerShape(10.dp),
                                containerColor = NothingTheme.colors.surfaceElevated,
                                borderColor = NothingGreen.copy(alpha = 0.6f),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NothingGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "CONFIRMED: YES / DONE",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = TechMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = NothingGreen
                                        )
                                        Text(
                                            text = "Your confirmation has been saved and alarms for this task are stopped.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NothingTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            if (hasRecentNo) {
                                NothingHairlineCard(
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = NothingTheme.colors.surfaceElevated,
                                    borderColor = NothingAmber.copy(alpha = 0.55f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Snooze,
                                            contentDescription = null,
                                            tint = NothingAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Last response: NO — follow-up reminder scheduled in ${effectiveReminderMins}m. Tap YES once completed.",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = NothingAmber
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Confirm operational readiness (NO schedules a ${effectiveReminderMins}m reminder):",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary
                            )
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
                                    enabled = canComplete && !isLockedBeforeTime,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingGreen,
                                        contentColor = androidx.compose.ui.graphics.Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_response_yes")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("YES / DONE", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        viewModel.submitTaskResponse(
                                            taskId = currentTask.id,
                                            responseType = ResponseType.YES_NO,
                                            responseValue = "NO"
                                        )
                                    },
                                    enabled = canComplete && !isLockedBeforeTime,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingRed,
                                        contentColor = androidx.compose.ui.graphics.Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_response_no")
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("NO (${effectiveReminderMins}m)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    ResponseType.TEXT_RESPONSE -> {
                        TaskzTextField(
                            value = responseText,
                            onValueChange = { if (canComplete && !isLockedBeforeTime) responseText = it },
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
                            enabled = canComplete && !isLockedBeforeTime,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingTheme.colors.accentPrimary,
                                contentColor = NothingTheme.colors.onAccentPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Submit Report", fontWeight = FontWeight.Bold)
                        }
                    }

                    ResponseType.MULTI_CONFIRMATION -> {
                        Text(
                            "Confirm individual LAN or role readiness:",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (currentTask.lanTargetList.isNotEmpty()) {
                            Text(
                                "SELECT ASSIGNED LAN:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NothingTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentTask.lanTargetList.forEach { lan ->
                                    FilterChip(
                                        selected = selectedLanForResponse == lan,
                                        onClick = { selectedLanForResponse = lan },
                                        enabled = canComplete && !isLockedBeforeTime,
                                        shape = RoundedCornerShape(999.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NothingTheme.colors.accentPrimary,
                                            selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                            containerColor = NothingTheme.colors.surfaceElevated,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = canComplete && !isLockedBeforeTime,
                                            selected = selectedLanForResponse == lan,
                                            borderColor = NothingTheme.colors.border,
                                            selectedBorderColor = NothingTheme.colors.accentPrimary
                                        ),
                                        label = { Text(lan, fontFamily = TechMonoFontFamily) }
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
                            enabled = canComplete && !isLockedBeforeTime,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingGreen,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Confirm ${if (currentTask.lanTargetList.isNotEmpty()) selectedLanForResponse else "Readiness"}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    else -> {
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
                                enabled = canComplete && !isLockedBeforeTime,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingGreen,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.TaskAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Task Completed", fontWeight = FontWeight.Bold)
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

                NothingSectionHeader(
                    title = "LAN CONFIRMATION DASHBOARD",
                    useDotMatrix = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                NothingHairlineCard(
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OVERALL: $confirmedCount / $totalCount CONFIRMED",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        NothingDotMatrixText(
                            text = "$percentConfirmed%",
                            fontSize = 18.sp,
                            color = if (percentConfirmed == 100) NothingGreen else NothingAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { confirmedCount.toFloat() / totalCount },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (percentConfirmed == 100) NothingGreen else NothingAmber,
                        trackColor = NothingTheme.colors.borderSubtle
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    lanProgress.forEachIndexed { idx, lanItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NothingStatusDot(
                                    color = if (lanItem.isConfirmed) NothingGreen else NothingAmber,
                                    size = 8.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = lanItem.lanNumber,
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (lanItem.isConfirmed) {
                                NothingTagChip(
                                    text = "CONFIRMED • ${(lanItem.employeeName ?: "STAFF").uppercase()}",
                                    contentColor = NothingGreen,
                                    borderColor = NothingGreen.copy(alpha = 0.45f)
                                )
                            } else {
                                NothingTagChip(
                                    text = "PENDING",
                                    contentColor = NothingAmber,
                                    borderColor = NothingAmber.copy(alpha = 0.45f)
                                )
                            }
                        }
                        if (idx < lanProgress.lastIndex) {
                            NothingDivider()
                        }
                    }
                }
            }

            // ============================================================
            // PROGRESS & SUBMISSION HISTORY TIMELINE
            // ============================================================
            if (taskResponses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                NothingSectionHeader(
                    title = "SUBMISSION HISTORY (${taskResponses.size})",
                    useDotMatrix = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                NothingHairlineCard(
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        taskResponses.take(10).forEachIndexed { idx, resp ->
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
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (resp.lanNumber != null) {
                                        Text(
                                            text = "Assigned: ${resp.lanNumber}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                            color = NothingTheme.colors.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = resp.submittedAt.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = NothingTheme.colors.textSecondary
                                )
                            }
                            if (idx < taskResponses.take(10).lastIndex) {
                                NothingDivider()
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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border)
                ) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Participants (${participants.filter { it.status == "active" }.size})", maxLines = 1)
                }

                OutlinedButton(
                    onClick = { onViewJoinRequestsClick(currentTask.id) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Requests (${joinRequests.size})", maxLines = 1)
                }
            }

            if (canAssign) {
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        showAssignDialog = true
                        viewModel.searchScopedMembers("")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_add_participant"),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Participant to Task", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Completion Confirmation Dialog
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "SUBMIT TASK COMPLETION",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Please provide verification notes or proof of completion:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TaskzTextField(
                        value = completionNote,
                        onValueChange = { completionNote = it },
                        label = "Completion Notes"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitCompletion(currentTask.id, completionNote)
                        showCompleteDialog = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    )
                ) {
                    Text("Submit for Review", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Self-Join Confirmation Dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = if (currentTask.joinRequiresApproval) "REQUEST TO JOIN TASK" else "JOIN TASK",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (requirements.isNotEmpty()) {
                        Text(
                            text = "SELECT ROLE CAPACITY:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NothingTheme.colors.textSecondary
                        )
                        requirements.forEach { req ->
                            val isSelected = selectedRoleIdToJoin == req.roleId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRoleIdToJoin = req.roleId }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedRoleIdToJoin = req.roleId }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${req.roleName ?: "Team Role"} (${req.filledCount}/${req.requiredCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Confirm joining '${currentTask.title}'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                    TaskzTextField(
                        value = joinMessage,
                        onValueChange = { joinMessage = it },
                        label = "Note (Optional)",
                        placeholder = "e.g., Available from Jaipur Sector 4"
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.joinTask(
                            taskId = currentTask.id,
                            roleId = selectedRoleIdToJoin,
                            message = joinMessage.trim().takeIf { it.isNotBlank() }
                        )
                        showJoinDialog = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
                    modifier = Modifier.testTag("confirm_join_task_button")
                ) {
                    Text(
                        text = if (currentTask.joinRequiresApproval) "Send Request" else "Join Now",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Participant Dialog
    if (showAssignDialog) {
        var searchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "ADD PARTICIPANT",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Search colleagues by Name, Email, or Employee ID:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchScopedMembers(it)
                        },
                        placeholder = { Text("e.g. Rahul, rahul@gmail.com, 0123456") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (directoryEmployees.isEmpty()) {
                        Text(
                            text = "No colleagues found in this city/scope.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(directoryEmployees) { emp ->
                                val onLeave = emp.isOnLeave
                                NothingHairlineCard(
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = NothingTheme.colors.surfaceElevated,
                                    borderColor = if (onLeave) NothingAmber.copy(alpha = 0.55f) else NothingTheme.colors.border,
                                    contentPadding = PaddingValues(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = emp.fullName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (onLeave) {
                                                    Icon(
                                                        imageVector = Icons.Default.EventBusy,
                                                        contentDescription = "On Leave",
                                                        tint = NothingAmber,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "On Leave",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = NothingAmber
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "ID: ${emp.employeeId} • ${emp.resolvedActiveRole.displayName}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                                color = NothingTheme.colors.textSecondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            enabled = !onLeave,
                                            onClick = {
                                                viewModel.assignUserToTask(
                                                    taskId = currentTask.id,
                                                    userId = emp.id,
                                                    onSuccess = { showAssignDialog = false }
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NothingTheme.colors.accentPrimary,
                                                contentColor = NothingTheme.colors.onAccentPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (onLeave) "Leave" else "Add", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showEditTaskDialog) {
        EditTaskDialog(
            task = currentTask,
            onDismiss = { showEditTaskDialog = false },
            onSave = { title, desc, loc, execIso, dueIso, priority, importance, alarmType, reminderMins, targetPct ->
                viewModel.updateTaskDetails(
                    taskId = currentTask.id,
                    title = title,
                    description = desc,
                    location = loc,
                    executionTime = execIso,
                    dueAt = dueIso,
                    priority = priority,
                    importance = importance,
                    alarmType = alarmType,
                    overdueReminderIntervalMinutes = reminderMins,
                    targetPercentage = targetPct,
                    onSuccess = { showEditTaskDialog = false }
                )
            }
        )
    }

    if (showDeleteTaskDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTaskDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "DELETE TASK",
                    fontSize = 18.sp,
                    color = NothingRed
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete '${currentTask.title}'? All scheduled alarms will be cancelled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTaskDialog = false
                        viewModel.deleteTask(currentTask.id) { onBackClick() }
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_task_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTaskDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String?,
        location: String?,
        executionTime: String?,
        dueAt: String?,
        priority: String,
        importance: TaskImportance,
        alarmType: com.example.core.model.AlarmType,
        reminderMins: Int,
        targetPct: Int
    ) -> Unit
) {
    val initialMillis = remember(task.id, task.executionTime, task.dueAt) {
        task.scheduledExecutionMillis() ?: System.currentTimeMillis()
    }
    val initialLocalDateTime = remember(initialMillis) {
        java.time.Instant.ofEpochMilli(initialMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
    }

    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var location by remember(task.id) { mutableStateOf(task.location.orEmpty()) }
    var execDate by remember(task.id) { mutableStateOf(initialLocalDateTime.toLocalDate().toString()) }
    var execHour by remember(task.id) { mutableIntStateOf(initialLocalDateTime.hour) }
    var execMinute by remember(task.id) { mutableIntStateOf(initialLocalDateTime.minute) }
    var execHourText by remember(task.id) { mutableStateOf(initialLocalDateTime.hour.toString().padStart(2, '0')) }
    var execMinuteText by remember(task.id) { mutableStateOf(initialLocalDateTime.minute.toString().padStart(2, '0')) }
    var isHourFocused by remember { mutableStateOf(false) }
    var isMinuteFocused by remember { mutableStateOf(false) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var importance by remember(task.id) { mutableStateOf(task.importance) }
    var alarmType by remember(task.id) { mutableStateOf(task.alarmType) }
    var reminderMins by remember(task.id) { mutableIntStateOf((task.overdueReminderIntervalMinutes ?: 10).coerceAtLeast(1)) }
    var targetPctText by remember(task.id) { mutableStateOf((task.targetPercentage ?: 100).toString()) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = NothingTheme.colors.accentPrimary,
        selectedLabelColor = NothingTheme.colors.onAccentPrimary,
        containerColor = NothingTheme.colors.surfaceElevated,
        labelColor = MaterialTheme.colorScheme.onSurface
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            NothingDotMatrixText(
                text = "EDIT TASK",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_desc_input")
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "DATE (YYYY-MM-DD) & TIME",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                OutlinedTextField(
                    value = execDate,
                    onValueChange = { execDate = it },
                    label = { Text("Date") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_task_date_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = execHourText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            execHourText = digits
                            digits.toIntOrNull()?.let { h ->
                                execHour = h.coerceIn(0, 23)
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (isHourFocused && !focusState.isFocused) {
                                    val clamped = (execHourText.toIntOrNull() ?: execHour).coerceIn(0, 23)
                                    execHour = clamped
                                    execHourText = clamped.toString().padStart(2, '0')
                                }
                                isHourFocused = focusState.isFocused
                            }
                            .testTag("edit_task_hour_input")
                    )
                    OutlinedTextField(
                        value = execMinuteText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            execMinuteText = digits
                            digits.toIntOrNull()?.let { m ->
                                execMinute = m.coerceIn(0, 59)
                            }
                        },
                        label = { Text("Min (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (isMinuteFocused && !focusState.isFocused) {
                                    val clamped = (execMinuteText.toIntOrNull() ?: execMinute).coerceIn(0, 59)
                                    execMinute = clamped
                                    execMinuteText = clamped.toString().padStart(2, '0')
                                }
                                isMinuteFocused = focusState.isFocused
                            }
                            .testTag("edit_task_min_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(15, 30, 60).forEach { addMins ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val next = java.time.LocalTime.now().plusMinutes(addMins.toLong())
                                execDate = java.time.LocalDate.now().toString()
                                execHour = next.hour
                                execMinute = next.minute
                                execHourText = next.hour.toString().padStart(2, '0')
                                execMinuteText = next.minute.toString().padStart(2, '0')
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
                            label = { Text("+${addMins}m", fontFamily = TechMonoFontFamily) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    "IMPORTANCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskImportance.entries.forEach { imp ->
                        FilterChip(
                            selected = importance == imp,
                            onClick = {
                                importance = imp
                                priority = imp.name.lowercase()
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = importance == imp,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
                            label = { Text(imp.label) }
                        )
                    }
                }

                Text(
                    "ALARM TYPE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.example.core.model.AlarmType.entries.forEach { at ->
                        FilterChip(
                            selected = alarmType == at,
                            onClick = { alarmType = at },
                            shape = RoundedCornerShape(999.dp),
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = alarmType == at,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
                            label = { Text(at.label) }
                        )
                    }
                }

                Text(
                    "REMINDER INTERVAL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        FilterChip(
                            selected = reminderMins == mins,
                            onClick = { reminderMins = mins },
                            shape = RoundedCornerShape(999.dp),
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = reminderMins == mins,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
                            label = { Text("${mins}m", fontFamily = TechMonoFontFamily) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (task.responseType == ResponseType.PERCENTAGE || task.taskType == TaskType.PROGRESS) {
                    OutlinedTextField(
                        value = targetPctText,
                        onValueChange = { targetPctText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                onClick = {
                    val parsedDate = runCatching { java.time.LocalDate.parse(execDate.trim()) }
                        .getOrElse { initialLocalDateTime.toLocalDate() }
                    val finalHour = (execHourText.toIntOrNull() ?: execHour).coerceIn(0, 23)
                    val finalMinute = (execMinuteText.toIntOrNull() ?: execMinute).coerceIn(0, 59)
                    val zdt = java.time.ZonedDateTime.of(
                        parsedDate,
                        java.time.LocalTime.of(finalHour, finalMinute),
                        java.time.ZoneId.systemDefault()
                    )
                    val iso = zdt.toInstant().toString()
                    onSave(
                        title.trim(),
                        description.trim().takeIf { it.isNotBlank() },
                        location.trim().takeIf { it.isNotBlank() },
                        iso,
                        iso,
                        priority,
                        importance,
                        alarmType,
                        reminderMins,
                        (targetPctText.toIntOrNull() ?: 100).coerceIn(1, 100)
                    )
                },
                modifier = Modifier.testTag("confirm_edit_task_btn")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
