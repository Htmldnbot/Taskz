package com.example.ui.groups

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.ui.shifts.ShiftCard
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.tasks.TaskCard
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GroupDetailScreen(
    group: TaskGroup,
    groupsViewModel: GroupsViewModel,
    shiftsViewModel: ShiftsViewModel,
    tasksViewModel: TasksViewModel,
    onEditGroupClick: (TaskGroup) -> Unit,
    onAddShiftClick: (String) -> Unit,
    onViewMembersClick: (String) -> Unit,
    onViewJoinRequestsClick: (String) -> Unit,
    onShiftClick: (Shift) -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onCreateTaskClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedGroup by groupsViewModel.selectedGroup.collectAsStateWithLifecycle()
    val groupJoinRequests by groupsViewModel.groupJoinRequests.collectAsStateWithLifecycle()
    val authContext by groupsViewModel.authContextFlow.collectAsStateWithLifecycle()
    val activeGroup = selectedGroup ?: group
    val canManage = groupsViewModel.canManageGroups()
    val canSelfJoin = groupsViewModel.canSelfJoinGroups()

    val groupShifts by shiftsViewModel.getShiftsForGroup(activeGroup.id).collectAsStateWithLifecycle()
    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val groupTasks = remember(allTasks, activeGroup.id) {
        allTasks.filter { it.groupId == activeGroup.id }
    }
    val assignableTasks = remember(allTasks) {
        allTasks.filter {
            !it.status.equals("completed", ignoreCase = true) &&
                !it.status.equals("cancelled", ignoreCase = true)
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showBatchAssignDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(activeGroup.id) {
        groupsViewModel.loadGroupDetails(activeGroup.id)
    }

    LaunchedEffect(Unit) {
        groupsViewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        shiftsViewModel.actionEvent.collectLatest { msg ->
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
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Exam Group",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (canManage) {
                    Row {
                        IconButton(
                            onClick = { onEditGroupClick(activeGroup) },
                            modifier = Modifier.testTag("btn_edit_group")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Exam Group")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeGroup.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        if (!activeGroup.code.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = activeGroup.code!!,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (!activeGroup.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = activeGroup.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!activeGroup.startDate.isNullOrBlank() || !activeGroup.endDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${activeGroup.startDate ?: "Ongoing"} - ${activeGroup.endDate ?: "Present"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = activeGroup.memberCount.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(text = "Members", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = groupShifts.size.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(text = "Shifts", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = groupTasks.size.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(text = "Tasks", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if ((canSelfJoin || authContext.isOnLeave) && activeGroup.allowSelfJoin && !activeGroup.isCurrentUserPending) {
                Button(
                    onClick = { groupsViewModel.joinGroupTasksBulk(activeGroup.id) },
                    enabled = !authContext.isOnLeave,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_join_all_group_tasks")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (authContext.isOnLeave) "On Leave — Joining Disabled" else "Join All Tasks (${groupTasks.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onViewMembersClick(activeGroup.id) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Members", maxLines = 1)
                }

                if (canManage) {
                    OutlinedButton(
                        onClick = { onViewJoinRequestsClick(activeGroup.id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Requests (${groupJoinRequests.size})", maxLines = 1)
                    }
                }
            }

            if (canManage) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAddShiftClick(activeGroup.id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Shift", maxLines = 1)
                    }

                    Button(
                        onClick = { onCreateTaskClick(activeGroup.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Task", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showBatchAssignDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_assign_tasks_to_group")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCheck,
                        contentDescription = "Batch Assign Tasks",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Assign Multiple Tasks")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: Shifts vs Tasks
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Shifts (${groupShifts.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Tasks (${groupTasks.size})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTabIndex == 0) {
                if (groupShifts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "No shifts yet", style = MaterialTheme.typography.bodyMedium)
                            if (canManage) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { onAddShiftClick(activeGroup.id) }) {
                                    Text("Add First Shift")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groupShifts, key = { it.id }) { shift ->
                            ShiftCard(
                                shift = shift,
                                onClick = { onShiftClick(shift) },
                                onJoinAllTasksClick = if (canSelfJoin && activeGroup.allowSelfJoin) {
                                    { shiftsViewModel.joinShiftTasksBulk(shift.id) }
                                } else null,
                                isOnLeave = authContext.isOnLeave
                            )
                        }
                    }
                }
            } else {
                if (groupTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "No tasks yet", style = MaterialTheme.typography.bodyMedium)
                            if (canManage) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { onCreateTaskClick(activeGroup.id) }) {
                                    Text("Create First Task")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groupTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onClick = { onTaskClick(task) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBatchAssignDialog) {
        var selectedTaskIds by remember(groupTasks) {
            mutableStateOf(groupTasks.map { it.id }.toSet())
        }
        AlertDialog(
            onDismissRequest = { showBatchAssignDialog = false },
            title = { Text("Assign Tasks to Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Every active member in '${activeGroup.title}' will automatically be assigned all selected tasks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (assignableTasks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val allIds = assignableTasks.map { it.id }.toSet()
                                selectedTaskIds = if (allIds.all { it in selectedTaskIds }) emptySet() else allIds
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_select_all_batch_tasks")
                        ) {
                            Text("Select / Deselect All (${assignableTasks.size})")
                        }
                    }
                    if (assignableTasks.isEmpty()) {
                        Text(
                            text = "No open tasks available to assign.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(assignableTasks, key = { it.id }) { task ->
                                val isChecked = task.id in selectedTaskIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            selectedTaskIds = if (isChecked) selectedTaskIds - task.id else selectedTaskIds + task.id
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${task.taskType.name} • ${task.scopeLevel.label}" +
                                                (task.groupTitle?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                        groupsViewModel.assignTasksToGroupAndSync(activeGroup.id, selectedTaskIds.toList()) {
                            showBatchAssignDialog = false
                        }
                    },
                    enabled = selectedTaskIds.isNotEmpty(),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_batch_assign_tasks")
                ) {
                    Text("Assign (${selectedTaskIds.size})")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchAssignDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("dialog_batch_assign_tasks")
        )
    }
}
