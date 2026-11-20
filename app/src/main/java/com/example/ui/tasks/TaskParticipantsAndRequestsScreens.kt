package com.example.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskParticipant
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

@Composable
fun TaskParticipantsScreen(
    taskId: String,
    viewModel: TasksViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val participants by viewModel.taskParticipants.collectAsStateWithLifecycle()
    val directoryEmployees by viewModel.directoryEmployees.collectAsStateWithLifecycle()
    val activeParts = participants.filter { it.status == "active" }
    val historicalParts = participants.filter { it.status != "active" }

    var participantToRemove by remember { mutableStateOf<TaskParticipant?>(null) }
    var removeReason by remember { mutableStateOf("") }
    var showAddParticipantDialog by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetails(taskId)
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    NothingDotMatrixText(
                        text = "PARTICIPANTS",
                        fontSize = 19.sp
                    )
                    Text(
                        text = "${activeParts.size} ACTIVE MEMBERS",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }
                Button(
                    onClick = {
                        showAddParticipantDialog = true
                        viewModel.searchEmployees("")
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (participants.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        title = "No participants yet",
                        subtitle = "Assign personnel or allow self-join for this task.",
                        icon = Icons.Default.Groups
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        NothingSectionHeader(
                            title = "ACTIVE PARTICIPANTS (${activeParts.size})",
                            useDotMatrix = true
                        )
                    }

                    items(activeParts, key = { it.id }) { participant ->
                        ParticipantCard(
                            participant = participant,
                            onRemoveClick = { participantToRemove = participant }
                        )
                    }

                    if (historicalParts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            NothingSectionHeader(
                                title = "HISTORY (${historicalParts.size})",
                                useDotMatrix = true
                            )
                        }

                        items(historicalParts, key = { it.id }) { participant ->
                            ParticipantCard(
                                participant = participant,
                                onRemoveClick = null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddParticipantDialog) {
        var searchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddParticipantDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = { NothingDotMatrixText("ADD PARTICIPANT", fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Search by Name, Email, or Employee ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchEmployees(it)
                        },
                        placeholder = { Text("e.g. Rahul, 0123456") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (directoryEmployees.isEmpty()) {
                        Text(
                            text = "No employees found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(directoryEmployees) { emp ->
                                val onLeave = emp.isOnLeave
                                NothingHairlineCard(
                                    containerColor = if (onLeave) NothingAmber.copy(alpha = 0.08f) else NothingTheme.colors.surfaceElevated,
                                    borderColor = if (onLeave) NothingAmber.copy(alpha = 0.4f) else NothingTheme.colors.border,
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
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (onLeave) {
                                                    Icon(
                                                        imageVector = Icons.Default.EventBusy,
                                                        contentDescription = "On Leave",
                                                        tint = NothingAmber,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "ON LEAVE",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = TechMonoFontFamily,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = NothingAmber
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "ID ${emp.employeeId} · ${emp.resolvedActiveRole.displayName}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                                color = NothingTheme.colors.textSecondary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            enabled = !onLeave,
                                            onClick = {
                                                viewModel.assignUserToTask(
                                                    taskId = taskId,
                                                    userId = emp.id,
                                                    onSuccess = { showAddParticipantDialog = false }
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NothingTheme.colors.accentPrimary,
                                                contentColor = NothingTheme.colors.onAccentPrimary
                                            )
                                        ) {
                                            Text(if (onLeave) "Leave" else "Add")
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
                TextButton(onClick = { showAddParticipantDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (participantToRemove != null) {
        AlertDialog(
            onDismissRequest = { participantToRemove = null },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = { NothingDotMatrixText("REMOVE PARTICIPANT", fontSize = 17.sp) },
            text = {
                Column {
                    Text(
                        text = "Remove ${participantToRemove!!.userName} from this task?",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = removeReason,
                        onValueChange = { removeReason = it },
                        label = { Text("Reason (Optional)") },
                        placeholder = { Text("e.g., Re-assigned") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val part = participantToRemove!!
                        viewModel.removeParticipant(part.id, removeReason, taskId)
                        participantToRemove = null
                        removeReason = ""
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("Confirm Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { participantToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ParticipantCard(
    participant: TaskParticipant,
    onRemoveClick: (() -> Unit)?
) {
    val isActive = participant.status == "active"
    NothingHairlineCard(
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NothingTheme.colors.surfaceElevated)
                    .border(1.dp, NothingTheme.colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.userName?.take(2)?.uppercase() ?: "EM",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NothingStatusDot(color = if (isActive) NothingGreen else NothingRed, size = 7.dp)
                    Text(
                        text = participant.userName ?: "Employee",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NothingTagChip(text = (participant.roleName ?: "Role").uppercase())
                    Text(
                        text = participant.source.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily, fontSize = 10.sp),
                        color = NothingTheme.colors.textSecondary
                    )
                }

                if (!isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STATUS: ${participant.status.uppercase()}${participant.leftReason?.let { " ($it)" } ?: ""}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingRed
                    )
                }
            }

            if (onRemoveClick != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Participant",
                        tint = NothingRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskJoinRequestsScreen(
    taskId: String,
    viewModel: TasksViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requests by viewModel.taskJoinRequests.collectAsStateWithLifecycle()

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetails(taskId)
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    NothingDotMatrixText(
                        text = "JOIN REQUESTS",
                        fontSize = 19.sp
                    )
                    Text(
                        text = "${requests.size} PENDING REQUESTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        title = "No pending requests",
                        subtitle = "Team join requests for this task will appear here.",
                        icon = Icons.Default.Work
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(requests, key = { it.id }) { req ->
                        NothingHairlineCard(contentPadding = PaddingValues(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(NothingTheme.colors.surfaceElevated)
                                        .border(1.dp, NothingTheme.colors.border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = req.userName?.take(2)?.uppercase() ?: "AP",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = TechMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.userName ?: "Applicant",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    NothingTagChip(text = "ROLE: ${(req.roleName ?: "General").uppercase()}")
                                }
                            }

                            if (!req.message.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "\"${req.message}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTheme.colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.reviewJoinRequest(req.id, false, taskId) },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, NothingRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingRed),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reject")
                                }

                                Button(
                                    onClick = { viewModel.reviewJoinRequest(req.id, true, taskId) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NothingGreen),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
