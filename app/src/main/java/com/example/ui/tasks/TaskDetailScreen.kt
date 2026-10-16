package com.example.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskItem
import com.example.core.model.TaskRoleRequirement
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TaskDetailScreen(
    task: TaskItem,
    viewModel: TasksViewModel,
    onViewParticipantsClick: (String) -> Unit,
    onViewJoinRequestsClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val requirements by viewModel.taskRequirements.collectAsStateWithLifecycle()
    val participants by viewModel.taskParticipants.collectAsStateWithLifecycle()
    val joinRequests by viewModel.taskJoinRequests.collectAsStateWithLifecycle()
    val activityLogs by viewModel.taskActivityLog.collectAsStateWithLifecycle()
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

    // Leave Task Inputs
    var leaveReason by remember { mutableStateOf("") }

    // Assign Employee Inputs
    var selectedEmployeeIdToAssign by remember { mutableStateOf("") }
    var selectedRoleIdToAssign by remember { mutableStateOf("") }
    var assignSearchQuery by remember { mutableStateOf("") }

    // Complete Task Inputs
    var completionNote by remember { mutableStateOf("") }

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
                    text = "Task Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Primary Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = currentTask.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )

                        val priorityColor = when (currentTask.priority.lowercase()) {
                            "urgent" -> TaskzError
                            "high" -> Color(0xFFF57C00)
                            "low" -> Color(0xFF388E3C)
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priorityColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = currentTask.priority.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = priorityColor),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (!currentTask.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
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

                    if (!currentTask.startsAt.isNullOrBlank() || !currentTask.dueAt.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentTask.startsAt ?: ""} → ${currentTask.dueAt ?: ""}".trim(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Rules badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (currentTask.allowSelfJoin) "Self-Join Enabled" else "Manual Assign Only",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (currentTask.joinRequiresApproval) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TaskzWarning.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Requires Approval",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TaskzWarning),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User Participation Status Banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        currentTask.isAssignedToMe -> TaskzSuccess.copy(alpha = 0.12f)
                        currentTask.isPendingApproval -> TaskzWarning.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Participation Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                currentTask.isAssignedToMe -> "Active Participant: ${currentTask.currentUserRoleName ?: "Joined"}"
                                currentTask.isPendingApproval -> "Join Request Pending Approval"
                                currentTask.currentUserParticipantStatus == "left" -> "Left Task"
                                currentTask.currentUserParticipantStatus == "removed" -> "Removed from Task"
                                else -> "Not Joined"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    when {
                        currentTask.isAssignedToMe -> {
                            Button(
                                onClick = { showLeaveDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TaskzError),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_leave_task")
                            ) {
                                Text("Leave Task", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        currentTask.isPendingApproval -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = TaskzWarning.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Pending",
                                    fontWeight = FontWeight.Bold,
                                    color = TaskzWarning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        currentTask.allowSelfJoin && currentTask.status == "open" -> {
                            Button(
                                onClick = {
                                    selectedRoleIdToJoin = requirements.firstOrNull { !it.isFull }?.roleId.orEmpty()
                                    showJoinDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_join_task")
                            ) {
                                Text("Join Task", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manpower Requirements Section
            Text(
                text = "Manpower Requirements",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (requirements.isEmpty()) {
                        Text(
                            text = "No role quotas specified for this task",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        requirements.forEach { req ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.roleName ?: "Role",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "${req.filledCount} / ${req.requiredCount} Filled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (req.vacantCount > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = TaskzError.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${req.vacantCount} Vacant",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzError),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        TextButton(
                                            onClick = {
                                                selectedRoleIdToAssign = req.roleId
                                                showAssignDialog = true
                                            }
                                        ) {
                                            Text("Assign Replacement", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = TaskzSuccess.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Filled",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzSuccess),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Center (Management Actions)
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
                    Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Requests (${joinRequests.size})", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        selectedRoleIdToAssign = requirements.firstOrNull()?.roleId.orEmpty()
                        showAssignDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Assign Employee", maxLines = 1)
                }

                if (currentTask.status != "completed") {
                    Button(
                        onClick = { showCompleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complete Task", maxLines = 1)
                    }
                }
            }

            // Pending Completion Manager Confirmation Banner
            if (currentTask.isPendingCompletionConfirmation) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Completion Request Awaiting Review", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "A participant has marked this task completed. Creator confirmation is required before status becomes final.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.reviewCompletion(currentTask.id, false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Needs More Work")
                            }
                            Button(
                                onClick = { viewModel.reviewCompletion(currentTask.id, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Completed")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Task Activity Log
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Task Activity History",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (activityLogs.isEmpty()) {
                        Text(
                            text = "No activity recorded yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activityLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.message ?: log.eventType,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "By: ${log.actorName ?: "System"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = log.createdAt.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Join Task Dialog ("Join As" role selector)
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Task") },
            text = {
                Column {
                    Text("Select the role you wish to join as:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    requirements.forEach { req ->
                        val isFull = req.isFull
                        val isSelected = selectedRoleIdToJoin == req.roleId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(enabled = !isFull) { selectedRoleIdToJoin = req.roleId }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRoleIdToJoin = req.roleId },
                                enabled = !isFull
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.roleName ?: "Role",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFull) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isFull) "${req.filledCount}/${req.requiredCount} — Full" else "${req.filledCount}/${req.requiredCount} Filled (${req.vacantCount} Vacant)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFull) TaskzError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (currentTask.joinRequiresApproval) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = joinMessage,
                            onValueChange = { joinMessage = it },
                            label = { Text("Optional message to creator") },
                            placeholder = { Text("e.g., Available for full shift") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedRoleIdToJoin.isNotBlank()) {
                            viewModel.joinTask(currentTask.id, selectedRoleIdToJoin, joinMessage)
                            showJoinDialog = false
                        }
                    },
                    enabled = selectedRoleIdToJoin.isNotBlank()
                ) {
                    Text("Confirm Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Leave Task Dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave Task") },
            text = {
                Column {
                    Text("Are you sure you want to leave this task? Your role slot will become vacant for replacement.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = leaveReason,
                        onValueChange = { leaveReason = it },
                        label = { Text("Reason (Optional)") },
                        placeholder = { Text("e.g., Emergency re-assignment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.leaveTask(currentTask.id, leaveReason)
                        showLeaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TaskzError)
                ) {
                    Text("Confirm Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Assign Employee Dialog (Creator/Admin)
    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Employee") },
            text = {
                Column {
                    Text("1. Select Role:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    requirements.forEach { req ->
                        val isSelected = selectedRoleIdToAssign == req.roleId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedRoleIdToAssign = req.roleId }
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedRoleIdToAssign = req.roleId })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${req.roleName} (${req.filledCount}/${req.requiredCount})")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("2. Search & Select Employee:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = assignSearchQuery,
                        onValueChange = {
                            assignSearchQuery = it
                            viewModel.searchEmployees(it)
                        },
                        placeholder = { Text("Search Name or EMP ID...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        directoryEmployees.take(4).forEach { emp ->
                            val isSelected = selectedEmployeeIdToAssign == emp.id
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEmployeeIdToAssign = emp.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${emp.fullName} (${emp.employeeId})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedEmployeeIdToAssign.isNotBlank() && selectedRoleIdToAssign.isNotBlank()) {
                            viewModel.assignEmployee(currentTask.id, selectedEmployeeIdToAssign, selectedRoleIdToAssign)
                            showAssignDialog = false
                        }
                    },
                    enabled = selectedEmployeeIdToAssign.isNotBlank() && selectedRoleIdToAssign.isNotBlank()
                ) {
                    Text("Confirm Assignment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Complete Task Dialog
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Complete Task") },
            text = {
                Column {
                    Text(
                        text = if (currentTask.requiresCompletionConfirmation)
                            "This task requires creator confirmation. Submitting will flag it for manager review."
                        else
                            "Mark this task as completed?",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = completionNote,
                        onValueChange = { completionNote = it },
                        label = { Text("Completion Note (Optional)") },
                        placeholder = { Text("e.g., All rooms locked and verified") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeTask(currentTask.id, completionNote)
                        showCompleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess)
                ) {
                    Text("Submit Completion")
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
