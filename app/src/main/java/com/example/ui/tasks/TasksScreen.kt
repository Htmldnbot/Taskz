package com.example.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingFilterToggleButton
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSearchInput
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.OrderedCascadingFilterBar
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
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
    val availableExams by viewModel.availableExams.collectAsStateWithLifecycle()
    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var taskToDelete by remember { mutableStateOf<TaskItem?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        filterState.stateId?.takeIf { it.isNotBlank() },
        filterState.cityId?.takeIf { it.isNotBlank() },
        filterState.centreId?.takeIf { it.isNotBlank() },
        filterState.examName?.takeIf { it.isNotBlank() },
        filterState.lan?.takeIf { it.isNotBlank() },
        filterState.shift?.takeIf { it.isNotBlank() },
        filterState.dateFilter.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
    ).size + if (selectedSubFilter != TaskSubFilter.ALL) 1 else 0

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
        containerColor = NothingTheme.colors.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 84.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Header with + Task Pill & Refresh (no overlapping FAB)
            item(key = "tasks_header_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NothingDotMatrixText(
                            text = "QUEUE",
                            fontSize = 18.sp,
                            letterSpacing = 1.4.sp
                        )
                        NothingTagChip(text = "${tasks.size} ITEMS")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.refreshTasks() },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("btn_refresh_tasks")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Tasks",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onCreateTaskClick,
                            shape = RoundedCornerShape(999.dp),
                            color = NothingTheme.colors.accentPrimary,
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("fab_create_task")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Task",
                                    tint = NothingTheme.colors.onAccentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Task",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = NothingTheme.colors.onAccentPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Main Segmented Tabs preserving exact testTags
            item(key = "header_tabs") {
                val mainTabs = listOf(
                    Triple(TaskTabFilter.MY_TASKS, "My Tasks", "tab_my_tasks"),
                    Triple(TaskTabFilter.AVAILABLE, "Available", "tab_available_tasks"),
                    Triple(TaskTabFilter.CREATED_BY_ME, "Created", "tab_created_tasks")
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = NothingTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        mainTabs.forEach { (tabEnum, label, tag) ->
                            val isSelected = selectedTab == tabEnum
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSelected) NothingTheme.colors.accentPrimary else Color.Transparent
                                    )
                                    .clickable { viewModel.setTab(tabEnum) }
                                    .testTag(tag),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Quick Operations Bar: Repeat Shift 1 -> 2 & Exam Groups
            item(key = "quick_ops_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onRepeatShiftClick,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        color = NothingTheme.colors.surface,
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .testTag("btn_repeat_shift_1_to_2")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Repeat Shift 1 tasks to Shift 2",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Repeat Shift 1 → 2",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = onManageGroupsClick,
                        shape = RoundedCornerShape(10.dp),
                        color = NothingTheme.colors.surface,
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("btn_manage_groups_shortcut")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Manage Task Groups",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Exam Groups",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Compact Search Input + On-Demand Filter Trigger
            item(key = "search_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingSearchInput(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = "Search tasks, exams, locations...",
                        testTag = "tasks_search_field",
                        modifier = Modifier.weight(1f)
                    )
                    NothingFilterToggleButton(
                        activeCount = activeFilterCount,
                        isExpanded = showFilters,
                        onClick = { showFilters = !showFilters },
                        testTag = "filter_toggle_button"
                    )
                }
            }

            // Ordered Cascading Dropdown Filters + Sub-filter chips (shown only when user taps Filters)
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
                    selectedExam = filterState.examName,
                    examOptions = availableExams,
                    onExamSelected = { viewModel.setExamFilter(it) },
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
                    expanded = showFilters,
                    onToggleExpanded = { showFilters = !showFilters },
                    extraActiveCount = if (selectedSubFilter != TaskSubFilter.ALL) 1 else 0,
                    extraContent = {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(TaskSubFilter.values().toList()) { filter ->
                                val selected = selectedSubFilter == filter
                                Surface(
                                    onClick = { viewModel.setSubFilter(filter) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) NothingTheme.colors.accentPrimary else NothingTheme.colors.border
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("subfilter_${filter.name.lowercase()}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = filter.name.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (isLoading && tasks.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (tasks.isEmpty()) {
                item(key = "empty_state") {
                    NothingEmptyState(
                        title = when (selectedTab) {
                            TaskTabFilter.MY_TASKS -> "NO ASSIGNED TASKS"
                            TaskTabFilter.AVAILABLE -> "NO OPEN TASKS"
                            TaskTabFilter.CREATED_BY_ME -> "NO CREATED TASKS"
                        },
                        subtitle = "Tap + Task above or explore Exam Groups.",
                        icon = Icons.Default.Assignment
                    )
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
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            containerColor = NothingTheme.colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = NothingRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Task", fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("Delete", color = Color.White)
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
    val priorityDotColor = when (task.priority.lowercase()) {
        "urgent" -> NothingRed
        "high" -> NothingAmber
        "low" -> NothingGreen
        else -> NothingTheme.colors.textSecondary
    }
    val rawScheduleTime = task.executionTime ?: task.dueAt ?: task.startsAt
    val formattedTime = rawScheduleTime?.let { TaskItem.formatForDisplay(it) ?: it }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NothingTheme.colors.surface,
        border = BorderStroke(1.dp, NothingTheme.colors.border),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Line 1: Priority dot + Title + Priority chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    NothingStatusDot(color = priorityDotColor, size = 7.dp)
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                NothingTagChip(
                    text = task.priority.uppercase(),
                    containerColor = if (task.priority.equals("urgent", ignoreCase = true)) {
                        NothingRed.copy(alpha = 0.14f)
                    } else NothingTheme.colors.surfaceElevated,
                    contentColor = if (task.priority.equals("urgent", ignoreCase = true)) NothingRed else NothingTheme.colors.textSecondary,
                    borderColor = if (task.priority.equals("urgent", ignoreCase = true)) NothingRed.copy(alpha = 0.4f) else NothingTheme.colors.border
                )
            }

            // Line 2: Group/Shift & Location/Time compact metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val metaText = buildList {
                    if (!task.groupTitle.isNullOrBlank()) add(task.groupTitle)
                    if (!task.shiftName.isNullOrBlank()) add(task.shiftName)
                    if (!task.location.isNullOrBlank()) add(task.location)
                }.joinToString(" · ")

                if (metaText.isNotBlank()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (!task.groupTitle.isNullOrBlank()) Icons.Default.Groups else Icons.Default.Place,
                            contentDescription = null,
                            tint = NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (!formattedTime.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (task.taskType == TaskType.PROGRESS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (task.currentProgress.coerceIn(0, 100)) / 100f },
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = NothingTheme.colors.border,
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "${task.currentProgress}%/${task.targetPercentage ?: 100}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Line 3: Compact Scope, Roles, Filled/Vacant & Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingTagChip(text = task.scopeLevel.label)
                    if (task.targetRoleCodes.isNotEmpty()) {
                        NothingTagChip(text = task.targetRoleCodes.joinToString(","))
                    }
                    NothingTagChip(text = "${task.totalAssigned}/${task.totalRequired}")
                    if (task.totalVacant > 0) {
                        NothingTagChip(
                            text = "${task.totalVacant} VAC",
                            containerColor = NothingRed.copy(alpha = 0.14f),
                            contentColor = NothingRed,
                            borderColor = NothingRed.copy(alpha = 0.4f)
                        )
                    }
                }

                val isPendingSync = task.localSyncState.equals("PENDING_SYNC", ignoreCase = true) ||
                    task.status.equals("pending_sync", ignoreCase = true)
                when {
                    isPendingSync -> NothingTagChip(
                        text = "PENDING SYNC",
                        containerColor = NothingAmber.copy(alpha = 0.14f),
                        contentColor = NothingAmber,
                        borderColor = NothingAmber.copy(alpha = 0.4f)
                    )
                    task.status == "completed" -> NothingTagChip(
                        text = "DONE",
                        containerColor = NothingGreen.copy(alpha = 0.14f),
                        contentColor = NothingGreen,
                        borderColor = NothingGreen.copy(alpha = 0.4f)
                    )
                    task.isPendingCompletionConfirmation -> NothingTagChip(
                        text = "CONFIRM",
                        containerColor = NothingAmber.copy(alpha = 0.14f),
                        contentColor = NothingAmber,
                        borderColor = NothingAmber.copy(alpha = 0.4f)
                    )
                    task.isAssignedToMe -> NothingTagChip(
                        text = if (!task.currentUserRoleName.isNullOrBlank()) "JOINED·${task.currentUserRoleName}" else "JOINED",
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary,
                        borderColor = NothingTheme.colors.accentPrimary
                    )
                    task.isPendingApproval -> NothingTagChip(
                        text = "PENDING",
                        containerColor = NothingAmber.copy(alpha = 0.14f),
                        contentColor = NothingAmber,
                        borderColor = NothingAmber.copy(alpha = 0.4f)
                    )
                    else -> NothingTagChip(text = task.displayStatus.label.uppercase())
                }
            }

            if (onJoinClick != null) {
                Surface(
                    onClick = onJoinClick,
                    shape = RoundedCornerShape(999.dp),
                    color = NothingTheme.colors.accentPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .padding(top = 2.dp)
                        .testTag("btn_quick_join_${task.id}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Join Task",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NothingTheme.colors.onAccentPrimary
                        )
                    }
                }
            }
        }
    }
}
