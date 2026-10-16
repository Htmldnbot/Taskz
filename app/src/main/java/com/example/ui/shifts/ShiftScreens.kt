package com.example.ui.shifts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Shift
import com.example.core.model.TaskItem
import com.example.ui.common.TaskzTextField
import com.example.ui.tasks.TaskCard
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ShiftCard(
    shift: Shift,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("shift_card_${shift.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shift.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (shift.shiftNumber != null) {
                        Text(
                            text = "Shift #${shift.shiftNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (shift.status) {
                        "active" -> TaskzSuccess.copy(alpha = 0.15f)
                        "completed" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = shift.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (shift.status) {
                                "active" -> TaskzSuccess
                                "completed" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${shift.startsAt}  →  ${shift.endsAt}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!shift.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = shift.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Manpower Breakdown Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${shift.taskCount} Tasks",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${shift.requiredManpower} Required",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TaskzSuccess.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${shift.assignedManpower} Assigned",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzSuccess),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (shift.vacantManpower > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TaskzError.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${shift.vacantManpower} Vacant",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TaskzError),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateShiftScreen(
    groupId: String,
    viewModel: ShiftsViewModel,
    onSuccess: (Shift) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var shiftNumberStr by rememberSaveable { mutableStateOf("1") }
    var startsAt by rememberSaveable { mutableStateOf("2026-10-16T08:00") }
    var endsAt by rememberSaveable { mutableStateOf("2026-10-16T16:00") }
    var notes by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    BackHandler(onBack = onBackClick)

    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                Text(
                    text = "Create Shift",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TaskzTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = null
                },
                label = "Shift Name *",
                placeholder = "e.g., Morning Shift - Hall A",
                leadingIcon = Icons.Default.Schedule,
                testTag = "input_shift_name"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = shiftNumberStr,
                onValueChange = { shiftNumberStr = it.filter { char -> char.isDigit() } },
                label = "Shift Number",
                placeholder = "1",
                leadingIcon = Icons.Default.Work,
                testTag = "input_shift_number"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = startsAt,
                onValueChange = {
                    startsAt = it
                    errorMessage = null
                },
                label = "Start Date/Time (ISO format) *",
                placeholder = "YYYY-MM-DDTHH:MM",
                leadingIcon = Icons.Default.DateRange,
                testTag = "input_shift_starts"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = endsAt,
                onValueChange = {
                    endsAt = it
                    errorMessage = null
                },
                label = "End Date/Time (ISO format) *",
                placeholder = "YYYY-MM-DDTHH:MM",
                leadingIcon = Icons.Default.DateRange,
                testTag = "input_shift_ends"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Shift Notes",
                placeholder = "Operational guidelines, dress code, instructions...",
                leadingIcon = Icons.Default.Notes,
                singleLine = false,
                testTag = "input_shift_notes"
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Shift name is required."
                        return@Button
                    }
                    if (endsAt <= startsAt) {
                        errorMessage = "Validation Error: End date/time must be strictly after start date/time."
                        return@Button
                    }
                    viewModel.createShift(
                        groupId = groupId,
                        name = name,
                        shiftNumber = shiftNumberStr.toIntOrNull(),
                        startsAt = startsAt,
                        endsAt = endsAt,
                        notes = notes,
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_create_shift")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Create Shift")
                }
            }
        }
    }
}

@Composable
fun EditShiftScreen(
    shift: Shift,
    viewModel: ShiftsViewModel,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf(shift.name) }
    var shiftNumberStr by rememberSaveable { mutableStateOf(shift.shiftNumber?.toString() ?: "") }
    var startsAt by rememberSaveable { mutableStateOf(shift.startsAt) }
    var endsAt by rememberSaveable { mutableStateOf(shift.endsAt) }
    var notes by rememberSaveable { mutableStateOf(shift.notes.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(shift.status) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    BackHandler(onBack = onBackClick)

    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                Text(
                    text = "Edit Shift",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TaskzTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = "Shift Name *",
                placeholder = "Shift name",
                leadingIcon = Icons.Default.Schedule
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = shiftNumberStr,
                onValueChange = { shiftNumberStr = it.filter { c -> c.isDigit() } },
                label = "Shift Number",
                placeholder = "Number",
                leadingIcon = Icons.Default.Work
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = startsAt,
                onValueChange = { startsAt = it; errorMessage = null },
                label = "Start Date/Time *",
                placeholder = "YYYY-MM-DDTHH:MM",
                leadingIcon = Icons.Default.DateRange
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = endsAt,
                onValueChange = { endsAt = it; errorMessage = null },
                label = "End Date/Time *",
                placeholder = "YYYY-MM-DDTHH:MM",
                leadingIcon = Icons.Default.DateRange
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes",
                placeholder = "Notes...",
                leadingIcon = Icons.Default.Notes,
                singleLine = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Status", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("scheduled", "active", "completed", "cancelled").forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = s },
                        label = { Text(s.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Shift name is required."
                        return@Button
                    }
                    if (endsAt <= startsAt) {
                        errorMessage = "Validation Error: End date/time must be after start date/time."
                        return@Button
                    }
                    viewModel.updateShift(
                        shiftId = shift.id,
                        name = name,
                        shiftNumber = shiftNumberStr.toIntOrNull(),
                        startsAt = startsAt,
                        endsAt = endsAt,
                        notes = notes,
                        status = status,
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Shift Changes")
                }
            }
        }
    }
}

@Composable
fun ShiftDetailScreen(
    shift: Shift,
    shiftsViewModel: ShiftsViewModel,
    tasksViewModel: TasksViewModel,
    onEditShiftClick: (Shift) -> Unit,
    onCopyShiftClick: (Shift) -> Unit,
    onAddTaskClick: (String, String) -> Unit, // (groupId, shiftId)
    onTaskClick: (TaskItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedShift by shiftsViewModel.selectedShift.collectAsStateWithLifecycle()
    val activeShift = selectedShift ?: shift

    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val shiftTasks = remember(allTasks, activeShift.id) {
        allTasks.filter { it.shiftId == activeShift.id }
    }

    LaunchedEffect(activeShift.id) {
        shiftsViewModel.loadShiftDetails(activeShift.id)
    }

    BackHandler(onBack = onBackClick)

    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
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
                        text = "Shift Details",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row {
                    IconButton(onClick = { onEditShiftClick(activeShift) }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Shift")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shift Card Overview
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
                            text = activeShift.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = activeShift.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${activeShift.startsAt}  →  ${activeShift.endsAt}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (!activeShift.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeShift.notes!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Manpower Overview Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = shiftTasks.size.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(text = "Tasks", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = activeShift.requiredManpower.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                            Text(text = "Required", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = activeShift.assignedManpower.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TaskzSuccess)
                            )
                            Text(text = "Assigned", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = activeShift.vacantManpower.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeShift.vacantManpower > 0) TaskzError else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(text = "Vacancies", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyShiftClick(activeShift) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Shift")
                }

                Button(
                    onClick = { onAddTaskClick(activeShift.groupId, activeShift.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Task")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Shift Tasks (${shiftTasks.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (shiftTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "No tasks assigned to this shift yet", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onAddTaskClick(activeShift.groupId, activeShift.id) }) {
                            Text("Create Shift Task")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shiftTasks, key = { it.id }) { task ->
                        TaskCard(task = task, onClick = { onTaskClick(task) })
                    }
                }
            }
        }
    }
}

@Composable
fun CopyShiftScreen(
    sourceShift: Shift,
    shiftsViewModel: ShiftsViewModel,
    tasksViewModel: TasksViewModel,
    onSuccess: (Shift) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newShiftName by rememberSaveable { mutableStateOf("${sourceShift.name} (Copy)") }
    var newStartsAt by rememberSaveable { mutableStateOf("2026-10-17T08:00") }

    var copyRequirements by rememberSaveable { mutableStateOf(true) } // Default ON
    var copyAssignments by rememberSaveable { mutableStateOf(false) } // Default OFF

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by shiftsViewModel.isLoading.collectAsStateWithLifecycle()

    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val sourceTasks = remember(allTasks, sourceShift.id) {
        allTasks.filter { it.shiftId == sourceShift.id }
    }

    val previewList = remember(sourceShift, sourceTasks, newStartsAt) {
        shiftsViewModel.generateCopyPreview(sourceShift, sourceTasks, newStartsAt)
    }

    BackHandler(onBack = onBackClick)

    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                    Text(
                        text = "Copy Shift",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Clone shift structure with relative task time offsets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Source Details
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Source Shift: ${sourceShift.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Starts At: ${sourceShift.startsAt}", style = MaterialTheme.typography.bodySmall)
                    Text("Contains: ${sourceTasks.size} tasks with manpower rules", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TaskzTextField(
                value = newShiftName,
                onValueChange = { newShiftName = it; errorMessage = null },
                label = "New Shift Name *",
                placeholder = "e.g., Afternoon Shift - Hall A",
                leadingIcon = Icons.Default.Schedule,
                testTag = "input_copy_shift_name"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = newStartsAt,
                onValueChange = { newStartsAt = it; errorMessage = null },
                label = "New Start Date/Time (ISO) *",
                placeholder = "YYYY-MM-DDTHH:MM",
                leadingIcon = Icons.Default.DateRange,
                testTag = "input_copy_shift_start"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Copy Options", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copy Manpower Requirements", fontWeight = FontWeight.SemiBold)
                            Text("Clone role quotas and required headcounts (Default: ON)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = copyRequirements, onCheckedChange = { copyRequirements = it })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copy Assigned Employees", fontWeight = FontWeight.SemiBold)
                            Text("Clone already-assigned staff into new shift (Default: OFF)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = copyAssignments, onCheckedChange = { copyAssignments = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Relative Time Offset Preview
            Text(
                text = "Task Timing Preview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Relative time gaps are preserved automatically",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (previewList.isEmpty()) {
                        Text(
                            text = "Source shift contains no tasks. A new empty shift will be created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        previewList.forEach { preview ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preview.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${preview.sourceTime} → ${preview.newTime}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (newShiftName.isBlank()) {
                        errorMessage = "New shift name is required."
                        return@Button
                    }
                    shiftsViewModel.copyShift(
                        sourceShiftId = sourceShift.id,
                        newShiftName = newShiftName,
                        newStartsAt = newStartsAt,
                        copyRequirements = copyRequirements,
                        copyAssignments = copyAssignments,
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_confirm_copy_shift")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Confirm Copy Shift")
                }
            }
        }
    }
}
