package com.example.ui.tasks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.ui.common.OrderedCascadingFilterBar
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onCreateTaskClick: () -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onManageGroupsClick: () -> Unit,
    onRepeatShiftClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedSubFilter by viewModel.selectedSubFilter.collectAsStateWithLifecycle()
    val filterState by viewModel.tasksFilterState.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentres.collectAsStateWithLifecycle()
    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var taskToDelete by remember { mutableStateOf<TaskItem?>(null) }

    LaunchedEffect(Unit) {
        if (orgHierarchy.cities.isEmpty()) {
            viewModel.refreshOrgHierarchy()
        }
        viewModel.refreshTasks()
        viewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTaskClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_create_task")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Filter Tabs
            item(key = "header_tabs") {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == TaskTabFilter.MY_TASKS,
                        onClick = { viewModel.setTab(TaskTabFilter.MY_TASKS) },
                        text = { Text("My Tasks", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_my_tasks")
                    )
                    Tab(
                        selected = selectedTab == TaskTabFilter.AVAILABLE,
                        onClick = { viewModel.setTab(TaskTabFilter.AVAILABLE) },
                        text = { Text("Available", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_available_tasks")
                    )
                    Tab(
                        selected = selectedTab == TaskTabFilter.CREATED_BY_ME,
                        onClick = { viewModel.setTab(TaskTabFilter.CREATED_BY_ME) },
                        text = { Text("Created By Me", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("tab_created_tasks")
                    )
                }
            }

            // Sub-filter chips (All, Today, Upcoming, Completed)
            item(key = "subfilter_chips") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TaskSubFilter.values().toList()) { filter ->
                        FilterChip(
                            selected = selectedSubFilter == filter,
                            onClick = { viewModel.setSubFilter(filter) },
                            label = {
                                Text(
                                    text = filter.name.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("subfilter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Quick Operations Bar: Repeat Shift 1 -> 2 & Groups
            item(key = "quick_ops_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRepeatShiftClick,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_repeat_shift_1_to_2")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Repeat Shift 1 tasks to Shift 2",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Repeat Shift 1 → 2", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onManageGroupsClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_manage_groups_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Manage Task Groups",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exam Groups", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Ordered Cascading Dropdown Filters: 1. State -> 2. City -> 3. Test Centre -> 4. LAN -> Shift -> Date
            item(key = "cascading_filters") {
                val selectedStateName = availableStates.firstOrNull { it.id == filterState.stateId }?.name
                val selectedCityName = availableCities.firstOrNull { it.id == filterState.cityId }?.name
                val selectedCentreLabel = availableCentres.firstOrNull { it.id == filterState.centreId }?.let { "${it.code} · ${it.name}" }
                val centreLabelOptions = remember(availableCentres) {
                    availableCentres.map { "${it.code} · ${it.name}" }
                }

                OrderedCascadingFilterBar(
                    selectedState = selectedStateName,
                    stateOptions = availableStates.map { it.name },
                    onStateSelected = { name ->
                        viewModel.setStateFilter(availableStates.firstOrNull { it.name == name }?.id)
                    },
                    selectedCity = selectedCityName,
                    cityOptions = availableCities.map { it.name },
                    onCitySelected = { name ->
                        viewModel.setCityFilter(availableCities.firstOrNull { it.name == name }?.id)
                    },
                    selectedCentre = selectedCentreLabel,
                    centreOptions = centreLabelOptions,
                    onCentreSelected = { label ->
                        val centre = availableCentres.firstOrNull { "${it.code} · ${it.name}" == label }
                        viewModel.setCentreFilter(centre?.id)
                    },
                    selectedLan = filterState.lan,
                    lanOptions = availableLans,
                    onLanSelected = { viewModel.setLanFilter(it) },
                    selectedShift = filterState.shift,
                    shiftOptions = availableShifts,
                    onShiftSelected = { viewModel.setShiftFilter(it) },
                    selectedDate = filterState.dateFilter,
                    dateOptions = viewModel.availableDateOptions,
                    onDateSelected = { viewModel.setDateFilter(it) },
                    onClearAll = { viewModel.clearAllFilters() },
                    initiallyExpanded = false
                )
            }

            // Search Bar & Refresh Action
            item(key = "search_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search tasks, groups, locations...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tasks_search_field")
                    )

                    IconButton(
                        onClick = { viewModel.refreshTasks() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_refresh_tasks")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Tasks",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (isLoading && tasks.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (tasks.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = when (selectedTab) {
                                    TaskTabFilter.MY_TASKS -> "No assigned tasks found"
                                    TaskTabFilter.AVAILABLE -> "No available open tasks to join"
                                    TaskTabFilter.CREATED_BY_ME -> "You haven't created any tasks yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the + button to create a new task or explore Groups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onClick = { onTaskClick(task) },
                        onLongClick = if (viewModel.canDeleteTask(task)) {
                            { taskToDelete = task }
                        } else null,
                        onJoinClick = if (
                            !authContext.isOnLeave &&
                            selectedTab == TaskTabFilter.AVAILABLE &&
                            task.allowSelfJoin &&
                            !task.isAssignedToMe &&
                            !task.isPendingApproval
                        ) {
                            { viewModel.joinTask(task.id, "", null) }
                        } else null
                    )
                }
            }

            item(key = "fab_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Task")
                }
            },
            text = {
                Text("Are you sure you want to delete '${taskToDelete?.title}'? This will cancel all alarms and remove the task for all participants.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = taskToDelete?.id
                        if (id != null) {
                            viewModel.deleteTask(id)
                        }
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onJoinClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val priorityColor = when (task.priority.lowercase()) {
        "urgent" -> TaskzError
        "high" -> Color(0xFFF57C00)
        "low" -> Color(0xFF388E3C)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("task_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!task.groupTitle.isNullOrBlank() || !task.shiftName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = listOfNotNull(task.groupTitle, task.shiftName).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = priorityColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = task.priority.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = priorityColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details row: Location, Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!task.location.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = task.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val rawScheduleTime = task.executionTime ?: task.dueAt ?: task.startsAt
                if (!rawScheduleTime.isNullOrBlank()) {
                    val formattedTime = TaskItem.formatForDisplay(rawScheduleTime) ?: rawScheduleTime
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (task.taskType == TaskType.PROGRESS) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progressive Completion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${task.currentProgress}% / ${task.targetPercentage ?: 100}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (task.currentProgress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Manpower & User Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Manpower & Scope breakdown
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = task.scopeLevel.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (task.targetRoleCodes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = task.targetRoleCodes.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${task.totalAssigned}/${task.totalRequired} Filled",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (task.totalVacant > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TaskzError.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${task.totalVacant} Vacant",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzError),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Current user participation & sync tag
                val isPendingSync = task.localSyncState.equals("PENDING_SYNC", ignoreCase = true) ||
                    task.status.equals("pending_sync", ignoreCase = true)
                when {
                    isPendingSync -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = TaskzWarning.copy(alpha = 0.2f)) {
                            Text(
                                text = "PENDING SYNC",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzWarning),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    task.status == "completed" -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = TaskzSuccess.copy(alpha = 0.15f)) {
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzSuccess),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    task.isPendingCompletionConfirmation -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = TaskzWarning.copy(alpha = 0.15f)) {
                            Text(
                                text = "Pending Confirmation",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzWarning),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    task.isAssignedToMe -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = if (!task.currentUserRoleName.isNullOrBlank()) "Joined: ${task.currentUserRoleName}" else "Joined",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    task.isPendingApproval -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = TaskzWarning.copy(alpha = 0.15f)) {
                            Text(
                                text = "Approval Pending",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzWarning),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                text = task.displayStatus.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (onJoinClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onJoinClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_quick_join_${task.id}")
                ) {
                    Text("Join Task", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
