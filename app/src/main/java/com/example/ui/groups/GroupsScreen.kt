package com.example.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskGroup
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingTagChip
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onCreateGroupClick: () -> Unit,
    onGroupClick: (TaskGroup) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groupsFlow.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val canManage = viewModel.canManageGroups()
    val canSelfJoin = viewModel.canSelfJoinGroups()
    var groupToDelete by remember { mutableStateOf<TaskGroup?>(null) }

    LaunchedEffect(Unit) {
        viewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(
                    onClick = onCreateGroupClick,
                    shape = CircleShape,
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary,
                    modifier = Modifier.testTag("fab_create_group")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Exam Group")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    NothingDotMatrixText(
                        text = "EXAM GROUPS",
                        fontSize = 22.sp
                    )
                    Text(
                        text = "EXAMS · SHIFTS · TASKS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            letterSpacing = 1.sp
                        ),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading && groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        icon = Icons.Default.Groups,
                        title = "NO EXAM GROUPS",
                        subtitle = "Create an exam group to organize shifts and checklists."
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            canSelfJoin = canSelfJoin,
                            isOnLeave = authContext.isOnLeave,
                            onClick = { onGroupClick(group) },
                            onJoinClick = { viewModel.joinGroupTasksBulk(group.id) },
                            onDeleteClick = if (viewModel.canDeleteGroup(group)) {
                                { groupToDelete = group }
                            } else null
                        )
                    }
                }
            }
        }
    }

    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = { NothingDotMatrixText(text = "DELETE EXAM GROUP", fontSize = 18.sp) },
            text = {
                Text("Delete '${groupToDelete?.title}' along with its shifts and tasks? This cancels scheduled alarms and cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = groupToDelete?.id
                        groupToDelete = null
                        if (id != null) viewModel.deleteGroup(id)
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_group_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupCard(
    group: TaskGroup,
    onClick: () -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
    canSelfJoin: Boolean = true,
    isOnLeave: Boolean = false,
    onDeleteClick: (() -> Unit)? = null
) {
    NothingHairlineCard(
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = modifier.testTag("group_card_${group.id}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!group.code.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        NothingTagChip(text = group.code)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    NothingTagChip(
                        text = group.status.uppercase(),
                        contentColor = if (group.status == "active") NothingGreen else NothingTheme.colors.textSecondary,
                        containerColor = if (group.status == "active") NothingGreen.copy(alpha = 0.14f) else NothingTheme.colors.surfaceElevated,
                        borderColor = if (group.status == "active") NothingGreen.copy(alpha = 0.45f) else NothingTheme.colors.border
                    )
                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.testTag("btn_delete_group_${group.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Exam Group",
                                tint = NothingRed
                            )
                        }
                    }
                }
            }

            if (!group.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${group.memberCount} members",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${group.shiftCount} shifts",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Task,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${group.taskCount} tasks",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            if (!group.startDate.isNullOrBlank() || !group.endDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${group.startDate ?: ""} - ${group.endDate ?: ""}".trim(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            // Status / Action Bar
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    group.isCurrentUserPending -> {
                        NothingTagChip(
                            text = "APPROVAL PENDING",
                            contentColor = NothingAmber,
                            containerColor = NothingAmber.copy(alpha = 0.14f),
                            borderColor = NothingAmber.copy(alpha = 0.45f)
                        )
                    }
                    group.isCurrentUserMember -> {
                        NothingTagChip(
                            text = "JOINED",
                            contentColor = NothingGreen,
                            containerColor = NothingGreen.copy(alpha = 0.14f),
                            borderColor = NothingGreen.copy(alpha = 0.45f)
                        )
                        if ((canSelfJoin || isOnLeave) && group.allowSelfJoin && group.taskCount > 0) {
                            OutlinedButton(
                                onClick = onJoinClick,
                                enabled = !isOnLeave,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, NothingTheme.colors.border),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("btn_join_all_group_tasks")
                            ) {
                                Text(
                                    text = if (isOnLeave) "On Leave" else "Join All Tasks",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily)
                                )
                            }
                        }
                    }
                    (canSelfJoin || isOnLeave) && group.allowSelfJoin -> {
                        Button(
                            onClick = onJoinClick,
                            enabled = !isOnLeave,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingTheme.colors.accentPrimary,
                                contentColor = NothingTheme.colors.onAccentPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("btn_join_all_group_tasks")
                        ) {
                            Text(
                                text = if (isOnLeave) "On Leave" else "Join All Tasks",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily)
                            )
                        }
                    }
                }
            }
        }
    }
}
