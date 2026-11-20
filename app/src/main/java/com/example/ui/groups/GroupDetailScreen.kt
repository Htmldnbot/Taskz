package com.example.ui.groups

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSegmentedTabs
import com.example.ui.common.NothingStatTile
import com.example.ui.common.NothingTagChip
import com.example.ui.shifts.ShiftCard
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.tasks.TaskCard
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
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
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var shiftToDelete by remember { mutableStateOf<Shift?>(null) }
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
        containerColor = MaterialTheme.colorScheme.background,
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
                    NothingDotMatrixText(
                        text = "EXAM GROUP",
                        fontSize = 20.sp
                    )
                }

                val canDeleteGroup = canManage || groupsViewModel.canDeleteGroup(activeGroup)
                if (canManage || canDeleteGroup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canManage) {
                            IconButton(
                                onClick = { onEditGroupClick(activeGroup) },
                                modifier = Modifier.testTag("btn_edit_group")
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Exam Group")
                            }
                        }
                        if (canDeleteGroup) {
                            IconButton(
                                onClick = { showDeleteGroupDialog = true },
                                modifier = Modifier.testTag("btn_delete_group_detail")
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary Card
            NothingHairlineCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            NothingTagChip(text = activeGroup.code!!)
                        }
                    }

                    if (!activeGroup.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = activeGroup.description!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTheme.colors.textSecondary
                        )
                    }

                    if (!activeGroup.startDate.isNullOrBlank() || !activeGroup.endDate.isNullOrBlank()) {
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
                                text = "${activeGroup.startDate ?: "Ongoing"} - ${activeGroup.endDate ?: "Present"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NothingStatTile(
                            label = "MEMBERS",
                            value = activeGroup.memberCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        NothingStatTile(
                            label = "SHIFTS",
                            value = groupShifts.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        NothingStatTile(
                            label = "TASKS",
                            value = groupTasks.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if ((canSelfJoin || authContext.isOnLeave) && activeGroup.allowSelfJoin && !activeGroup.isCurrentUserPending) {
                Button(
                    onClick = { groupsViewModel.joinGroupTasksBulk(activeGroup.id) },
                    enabled = !authContext.isOnLeave,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
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
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Members", maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                }

                if (canManage) {
                    OutlinedButton(
                        onClick = { onViewJoinRequestsClick(activeGroup.id) },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Requests (${groupJoinRequests.size})", maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
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
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingTheme.colors.accentPrimary,
                            contentColor = NothingTheme.colors.onAccentPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Shift", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onCreateTaskClick(activeGroup.id) },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Task", maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showBatchAssignDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
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
                    Text("Assign Multiple Tasks", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs: Shifts vs Tasks
            NothingSegmentedTabs(
                options = listOf("Shifts (${groupShifts.size})", "Tasks (${groupTasks.size})"),
                selectedIndex = selectedTabIndex,
                onSelect = { selectedTabIndex = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTabIndex == 0) {
                if (groupShifts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NothingEmptyState(
                                icon = Icons.Default.Schedule,
                                title = "NO SHIFTS YET",
                                subtitle = "Add shifts to schedule exam tasks."
                            )
                            if (canManage) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onAddShiftClick(activeGroup.id) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingTheme.colors.accentPrimary,
                                        contentColor = NothingTheme.colors.onAccentPrimary
                                    )
                                ) {
                                    Text("Add First Shift")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(groupShifts, key = { it.id }) { shift ->
                            ShiftCard(
                                shift = shift,
                                onClick = { onShiftClick(shift) },
                                onJoinAllTasksClick = if (canSelfJoin && activeGroup.allowSelfJoin) {
                                    { shiftsViewModel.joinShiftTasksBulk(shift.id) }
                                } else null,
                                isOnLeave = authContext.isOnLeave,
                                onDeleteClick = if (canManage || shiftsViewModel.canDeleteShift(shift)) {
                                    { shiftToDelete = shift }
                                } else null
                            )
                        }
                    }
                }
            } else {
                if (groupTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NothingEmptyState(
                                icon = Icons.Default.Task,
                                title = "NO TASKS YET",
                                subtitle = "Create tasks under this exam group."
                            )
                            if (canManage) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onCreateTaskClick(activeGroup.id) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingTheme.colors.accentPrimary,
                                        contentColor = NothingTheme.colors.onAccentPrimary
                                    )
                                ) {
                                    Text("Create First Task")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
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
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "ASSIGN TASKS",
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = NothingTheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Every active member in '${activeGroup.title}' will automatically be assigned all selected tasks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
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
                            Text(
                                text = "SELECT / DESELECT ALL (${assignableTasks.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NothingTheme.colors.textPrimary
                            )
                        }
                    }
                    if (assignableTasks.isEmpty()) {
                        Text(
                            text = "No open tasks available to assign.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(assignableTasks, key = { it.id }) { task ->
                                val isChecked = task.id in selectedTaskIds
                                NothingHairlineCard(
                                    onClick = {
                                        selectedTaskIds = if (isChecked) selectedTaskIds - task.id else selectedTaskIds + task.id
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = NothingTheme.colors.surfaceElevated,
                                    borderColor = if (isChecked) NothingTheme.colors.textPrimary else NothingTheme.colors.border,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 40.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                            },
                                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                                checkedColor = NothingTheme.colors.accentPrimary,
                                                checkmarkColor = NothingTheme.colors.onAccentPrimary,
                                                uncheckedColor = NothingTheme.colors.textSecondary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = NothingTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${task.taskType.name} • ${task.scopeLevel.label}" +
                                                    (task.groupTitle?.let { " • $it" } ?: ""),
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                                color = NothingTheme.colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_batch_assign_tasks")
                ) {
                    Text("Assign (${selectedTaskIds.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchAssignDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = NothingTheme.colors.textSecondary)
                }
            },
            modifier = Modifier.testTag("dialog_batch_assign_tasks")
        )
    }

    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "DELETE EXAM GROUP",
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = NothingTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Delete '${activeGroup.title}' along with all shifts and tasks inside it? This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupDialog = false
                        groupsViewModel.deleteGroup(activeGroup.id) { onBackClick() }
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_group_detail_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteGroupDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = NothingTheme.colors.textSecondary)
                }
            }
        )
    }

    if (shiftToDelete != null) {
        AlertDialog(
            onDismissRequest = { shiftToDelete = null },
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "DELETE SHIFT",
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = NothingTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Delete '${shiftToDelete?.name}' and all tasks scheduled in it?",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = shiftToDelete?.id
                        shiftToDelete = null
                        if (id != null) shiftsViewModel.deleteShift(id)
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_shift_from_group_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { shiftToDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = NothingTheme.colors.textSecondary)
                }
            }
        )
    }
}
