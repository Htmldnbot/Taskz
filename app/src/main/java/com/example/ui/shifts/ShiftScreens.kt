package com.example.ui.shifts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatShiftRange(startsAt: String, endsAt: String): String {
    val startMs = TaskItem.parseIsoToMillis(startsAt)
    val endMs = TaskItem.parseIsoToMillis(endsAt)
    if (startMs == null || endMs == null) return "$startsAt  →  $endsAt"
    val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return "${dateFmt.format(Date(startMs))} · ${timeFmt.format(Date(startMs))} → ${timeFmt.format(Date(endMs))}"
}

private fun formatPickerDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun formatPickerTime(millis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))

private fun defaultLocalTimeMillis(hour: Int, minute: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun showDatePicker(
    context: android.content.Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            onPicked(cal.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun showTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.timeInMillis)
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        false
    ).show()
}

@Composable
fun ShiftCard(
    shift: Shift,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onJoinAllTasksClick: (() -> Unit)? = null,
    isOnLeave: Boolean = false
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatShiftRange(shift.startsAt, shift.endsAt),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            if (onJoinAllTasksClick != null && shift.taskCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onJoinAllTasksClick,
                    enabled = !isOnLeave,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_join_all_shift_tasks_${shift.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnLeave) "On Leave" else "Join All Shift Tasks (${shift.taskCount})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateShiftScreen(
    groupId: String,
    viewModel: ShiftsViewModel,
    onSuccess: (Shift) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var shiftNumberStr by rememberSaveable { mutableStateOf("1") }
    var startMillis by rememberSaveable { mutableLongStateOf(defaultLocalTimeMillis(6, 0)) }
    var endMillis by rememberSaveable { mutableLongStateOf(defaultLocalTimeMillis(10, 30)) }
    var notes by rememberSaveable { mutableStateOf("") }

    val quickLans = remember { listOf("LAN 1", "LAN 2", "LAN 3", "LAN 4") }
    val selectedLans = remember { mutableStateListOf<String>() }

    var loadStandardChecklist by rememberSaveable { mutableStateOf(false) }
    var checklistAnchorMillis by rememberSaveable { mutableLongStateOf(startMillis) }
    val selectedChecklistKeys = remember {
        mutableStateListOf<String>().apply {
            addAll(STANDARD_EXAM_CHECKLIST.map { it.key })
        }
    }

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
                placeholder = "e.g., Shift 1",
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

            // Native Start & End Date/Time Pickers
            ShiftDateTimePickerRow(
                label = "Start Time *",
                millis = startMillis,
                onPickDate = {
                    showDatePicker(context, startMillis) { picked ->
                        val diff = picked - startMillis
                        startMillis = picked
                        endMillis += diff
                        checklistAnchorMillis = picked
                        errorMessage = null
                    }
                },
                onPickTime = {
                    showTimePicker(context, startMillis) { picked ->
                        startMillis = picked
                        if (endMillis <= startMillis) {
                            endMillis = startMillis + (4L * 3_600_000L) + (30L * 60_000L)
                        }
                        checklistAnchorMillis = picked
                        errorMessage = null
                    }
                },
                dateButtonTag = "btn_create_shift_start_date",
                timeButtonTag = "btn_create_shift_start_time"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ShiftDateTimePickerRow(
                label = "End Time *",
                millis = endMillis,
                onPickDate = {
                    showDatePicker(context, endMillis) { picked ->
                        endMillis = picked
                        errorMessage = null
                    }
                },
                onPickTime = {
                    showTimePicker(context, endMillis) { picked ->
                        endMillis = picked
                        errorMessage = null
                    }
                },
                dateButtonTag = "btn_create_shift_end_date",
                timeButtonTag = "btn_create_shift_end_time"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "LANs (Optional)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickLans.forEach { lan ->
                    val checked = lan in selectedLans
                    FilterChip(
                        selected = checked,
                        onClick = {
                            if (checked) selectedLans.remove(lan) else selectedLans.add(lan)
                        },
                        label = { Text(lan) },
                        modifier = Modifier.testTag("chip_shift_${lan.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Notes (Optional)",
                placeholder = "Instructions or floor notes...",
                leadingIcon = Icons.Default.Notes,
                singleLine = false,
                testTag = "input_shift_notes"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Optional 11-Milestone Standard Exam Checklist
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
                            Text("Exam Shift Checklist", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Auto-create 11 exam tasks from T0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = loadStandardChecklist,
                            onCheckedChange = { enabled ->
                                loadStandardChecklist = enabled
                                if (enabled) {
                                    val isShift1 = (shiftNumberStr.toIntOrNull() == 1) || name.contains("1")
                                    selectedChecklistKeys.clear()
                                    selectedChecklistKeys.addAll(
                                        STANDARD_EXAM_CHECKLIST
                                            .filter { isShift1 || !it.shift1Only }
                                            .map { it.key }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("switch_load_exam_checklist")
                        )
                    }

                    if (loadStandardChecklist) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Drive Download Anchor (T0)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = formatPickerTime(checklistAnchorMillis),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    showTimePicker(context, checklistAnchorMillis) { picked ->
                                        checklistAnchorMillis = picked
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set T0")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selectedChecklistKeys.clear()
                                selectedChecklistKeys.addAll(STANDARD_EXAM_CHECKLIST.map { it.key })
                            }) { Text("All (11)") }
                            TextButton(onClick = {
                                selectedChecklistKeys.clear()
                                selectedChecklistKeys.addAll(STANDARD_EXAM_CHECKLIST.filter { !it.shift1Only }.map { it.key })
                            }) { Text("Shift 2+ (7)") }
                            TextButton(onClick = { selectedChecklistKeys.clear() }) { Text("Clear") }
                        }

                        STANDARD_EXAM_CHECKLIST.forEach { item ->
                            val isChecked = item.key in selectedChecklistKeys
                            val itemTime = ShiftsViewModel.formatTimeOnly(checklistAnchorMillis + item.offsetMinutes * 60_000L)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isChecked) selectedChecklistKeys.remove(item.key)
                                        else selectedChecklistKeys.add(item.key)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked && !isChecked) selectedChecklistKeys.add(item.key)
                                        else if (!checked) selectedChecklistKeys.remove(item.key)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (item.shift1Only) {
                                        Text(
                                            text = "Shift 1 only",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TaskzWarning
                                        )
                                    }
                                }
                                Text(
                                    text = itemTime,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

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
                    if (endMillis <= startMillis) {
                        errorMessage = "End time must be after start time."
                        return@Button
                    }
                    val combinedNotes = buildList {
                        if (selectedLans.isNotEmpty()) add("LAN: ${selectedLans.joinToString(", ")}")
                        if (notes.isNotBlank()) add(notes.trim())
                    }.joinToString(" • ").takeIf { it.isNotBlank() }

                    val startsIso = Instant.ofEpochMilli(startMillis).toString()
                    val endsIso = Instant.ofEpochMilli(endMillis).toString()

                    viewModel.createShift(
                        groupId = groupId,
                        name = name,
                        shiftNumber = shiftNumberStr.toIntOrNull(),
                        startsAt = startsIso,
                        endsAt = endsIso,
                        notes = combinedNotes,
                        onSuccess = { createdShift ->
                            val templates = STANDARD_EXAM_CHECKLIST.filter { it.key in selectedChecklistKeys }
                            if (loadStandardChecklist && templates.isNotEmpty()) {
                                viewModel.createChecklistTasksForShift(
                                    groupId = groupId,
                                    shiftId = createdShift.id,
                                    anchorTimeMillis = checklistAnchorMillis,
                                    lanTags = selectedLans.toList(),
                                    selectedTemplates = templates
                                ) { _, _ ->
                                    onSuccess(createdShift)
                                }
                            } else {
                                onSuccess(createdShift)
                            }
                        }
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
                    Text(
                        if (loadStandardChecklist && selectedChecklistKeys.isNotEmpty()) {
                            "Create Shift & ${selectedChecklistKeys.size} Tasks"
                        } else {
                            "Create Shift"
                        }
                    )
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
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(shift.name) }
    var shiftNumberStr by rememberSaveable { mutableStateOf(shift.shiftNumber?.toString() ?: "") }
    var startMillis by rememberSaveable {
        mutableLongStateOf(TaskItem.parseIsoToMillis(shift.startsAt) ?: defaultLocalTimeMillis(6, 0))
    }
    var endMillis by rememberSaveable {
        mutableLongStateOf(TaskItem.parseIsoToMillis(shift.endsAt) ?: defaultLocalTimeMillis(10, 30))
    }
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

            ShiftDateTimePickerRow(
                label = "Start Time *",
                millis = startMillis,
                onPickDate = {
                    showDatePicker(context, startMillis) { picked ->
                        startMillis = picked
                        errorMessage = null
                    }
                },
                onPickTime = {
                    showTimePicker(context, startMillis) { picked ->
                        startMillis = picked
                        errorMessage = null
                    }
                },
                dateButtonTag = "btn_edit_shift_start_date",
                timeButtonTag = "btn_edit_shift_start_time"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ShiftDateTimePickerRow(
                label = "End Time *",
                millis = endMillis,
                onPickDate = {
                    showDatePicker(context, endMillis) { picked ->
                        endMillis = picked
                        errorMessage = null
                    }
                },
                onPickTime = {
                    showTimePicker(context, endMillis) { picked ->
                        endMillis = picked
                        errorMessage = null
                    }
                },
                dateButtonTag = "btn_edit_shift_end_date",
                timeButtonTag = "btn_edit_shift_end_time"
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
                    if (endMillis <= startMillis) {
                        errorMessage = "End time must be after start time."
                        return@Button
                    }
                    viewModel.updateShift(
                        shiftId = shift.id,
                        name = name,
                        shiftNumber = shiftNumberStr.toIntOrNull(),
                        startsAt = Instant.ofEpochMilli(startMillis).toString(),
                        endsAt = Instant.ofEpochMilli(endMillis).toString(),
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
                    Text("Save Changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftDetailScreen(
    shift: Shift,
    shiftsViewModel: ShiftsViewModel,
    tasksViewModel: TasksViewModel,
    onEditShiftClick: (Shift) -> Unit,
    onCopyShiftClick: (Shift) -> Unit,
    onAddTaskClick: (String, String) -> Unit,
    onTaskClick: (TaskItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedShift by shiftsViewModel.selectedShift.collectAsStateWithLifecycle()
    val authContext by shiftsViewModel.authContextFlow.collectAsStateWithLifecycle()
    val isLoading by shiftsViewModel.isLoading.collectAsStateWithLifecycle()
    val activeShift = selectedShift ?: shift
    val canManage = shiftsViewModel.canManageShifts()
    val canSelfJoin = shiftsViewModel.canSelfJoinShifts()

    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val shiftTasks = remember(allTasks, activeShift.id) {
        allTasks.filter { it.shiftId == activeShift.id }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showChecklistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeShift.id) {
        shiftsViewModel.loadShiftDetails(activeShift.id)
    }

    LaunchedEffect(Unit) {
        shiftsViewModel.actionEvent.collect { msg ->
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

                if (canManage) {
                    IconButton(
                        onClick = { onEditShiftClick(activeShift) },
                        modifier = Modifier.testTag("btn_edit_shift")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Shift")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            text = formatShiftRange(activeShift.startsAt, activeShift.endsAt),
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

            if ((canSelfJoin || authContext.isOnLeave) && shiftTasks.isNotEmpty()) {
                Button(
                    onClick = { shiftsViewModel.joinShiftTasksBulk(activeShift.id) },
                    enabled = !authContext.isOnLeave,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_join_all_shift_tasks")
                ) {
                    Icon(imageVector = Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (authContext.isOnLeave) "On Leave — Joining Disabled" else "Join All Shift Tasks (${shiftTasks.size})",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (canManage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCopyShiftClick(activeShift) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_copy_shift")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Shift", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Shift")
                    }

                    Button(
                        onClick = { onAddTaskClick(activeShift.groupId, activeShift.id) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_add_shift_task")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Task")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showChecklistDialog = true },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_load_exam_checklist")
                ) {
                    Icon(imageVector = Icons.Default.Checklist, contentDescription = "Exam Checklist", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Standard Exam Checklist")
                }

                if (activeShift.shiftNumber == 1 || activeShift.name.contains("1")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onCopyShiftClick(activeShift) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_shift_repeat_to_2")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Repeat Shift 1 to Shift 2", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Repeat Shift 1 → Shift 2")
                    }
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
                        Text(text = "No tasks in this shift yet", style = MaterialTheme.typography.bodyMedium)
                        if (canManage) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { showChecklistDialog = true }) {
                                Text("Load 11 Exam Tasks")
                            }
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

    if (showChecklistDialog) {
        val defaultAnchor = remember(activeShift.startsAt) {
            TaskItem.parseIsoToMillis(activeShift.startsAt) ?: defaultLocalTimeMillis(6, 0)
        }
        var anchorMillis by remember { mutableLongStateOf(defaultAnchor) }
        val isShift1 = activeShift.shiftNumber == 1 || activeShift.name.contains("1")
        val quickLans = remember { listOf("LAN 1", "LAN 2", "LAN 3", "LAN 4") }
        val selectedLans = remember {
            mutableStateListOf<String>().apply {
                quickLans.filter { activeShift.notes?.contains(it, ignoreCase = true) == true }
                    .forEach { add(it) }
            }
        }
        val selectedKeys = remember {
            mutableStateListOf<String>().apply {
                addAll(STANDARD_EXAM_CHECKLIST.filter { isShift1 || !it.shift1Only }.map { it.key })
            }
        }

        AlertDialog(
            onDismissRequest = { showChecklistDialog = false },
            title = { Text("Exam Shift Checklist", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Drive Download (T0)", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = formatPickerTime(anchorMillis),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(onClick = {
                            showTimePicker(context, anchorMillis) { picked -> anchorMillis = picked }
                        }) {
                            Text("Change")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickLans.forEach { lan ->
                            val selected = lan in selectedLans
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedLans.remove(lan) else selectedLans.add(lan)
                                },
                                label = { Text(lan) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            selectedKeys.clear()
                            selectedKeys.addAll(STANDARD_EXAM_CHECKLIST.map { it.key })
                        }) { Text("All") }
                        TextButton(onClick = {
                            selectedKeys.clear()
                            selectedKeys.addAll(STANDARD_EXAM_CHECKLIST.filter { !it.shift1Only }.map { it.key })
                        }) { Text("Shift 2+") }
                        TextButton(onClick = { selectedKeys.clear() }) { Text("Clear") }
                    }

                    STANDARD_EXAM_CHECKLIST.forEach { item ->
                        val checked = item.key in selectedKeys
                        val timeText = ShiftsViewModel.formatTimeOnly(anchorMillis + item.offsetMinutes * 60_000L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedKeys.remove(item.key) else selectedKeys.add(item.key)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !checked) selectedKeys.add(item.key)
                                    else if (!isChecked) selectedKeys.remove(item.key)
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                if (item.shift1Only) {
                                    Text("Shift 1 only", style = MaterialTheme.typography.labelSmall, color = TaskzWarning)
                                }
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedKeys.isNotEmpty() && !isLoading,
                    onClick = {
                        val templates = STANDARD_EXAM_CHECKLIST.filter { it.key in selectedKeys }
                        showChecklistDialog = false
                        shiftsViewModel.createChecklistTasksForShift(
                            groupId = activeShift.groupId,
                            shiftId = activeShift.id,
                            anchorTimeMillis = anchorMillis,
                            lanTags = selectedLans.toList(),
                            selectedTemplates = templates
                        )
                    },
                    modifier = Modifier.testTag("confirm_load_exam_checklist")
                ) {
                    Text("Add ${selectedKeys.size} Tasks")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChecklistDialog = false }) { Text("Cancel") }
            }
        )
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
    val context = LocalContext.current
    val defaultNewName = remember(sourceShift.name) {
        when {
            sourceShift.name.contains("1") -> sourceShift.name.replace("1", "2")
            else -> "${sourceShift.name} - Shift 2"
        }
    }
    val defaultNewStartMillis = remember(sourceShift.startsAt) {
        val srcMs = TaskItem.parseIsoToMillis(sourceShift.startsAt) ?: defaultLocalTimeMillis(6, 0)
        val cal = Calendar.getInstance().apply { timeInMillis = srcMs }
        if (cal.get(Calendar.HOUR_OF_DAY) < 10) {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 45)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else {
            srcMs + (4L * 3_600_000L)
        }
    }

    var newShiftName by rememberSaveable { mutableStateOf(defaultNewName) }
    var newStartMillis by rememberSaveable { mutableLongStateOf(defaultNewStartMillis) }
    val newStartsAtIso = remember(newStartMillis) { Instant.ofEpochMilli(newStartMillis).toString() }

    var copyRequirements by rememberSaveable { mutableStateOf(true) }
    var copyAssignments by rememberSaveable { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by shiftsViewModel.isLoading.collectAsStateWithLifecycle()

    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val sourceTasks = remember(allTasks, sourceShift.id) {
        allTasks.filter { it.shiftId == sourceShift.id }
    }

    val previewList = remember(sourceShift.startsAt, sourceTasks, newStartsAtIso) {
        shiftsViewModel.generateCopyPreview(sourceShift, sourceTasks, newStartsAtIso)
    }

    val selectedTaskIds = remember { mutableStateListOf<String>() }
    var selectionInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(previewList.map { it.taskId }) {
        if (!selectionInitialized && previewList.isNotEmpty()) {
            selectedTaskIds.clear()
            selectedTaskIds.addAll(previewList.filter { it.defaultSelected }.map { it.taskId })
            selectionInitialized = true
        }
    }

    val offsetLabel = remember(sourceShift.startsAt, newStartsAtIso) {
        ShiftsViewModel.computeOffsetSummary(sourceShift.startsAt, newStartsAtIso)
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
                Text(
                    text = "Copy Shift & Tasks",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Source: ${sourceShift.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatShiftRange(sourceShift.startsAt, sourceShift.endsAt),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${sourceTasks.size} tasks available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TaskzTextField(
                value = newShiftName,
                onValueChange = { newShiftName = it; errorMessage = null },
                label = "New Shift Name *",
                placeholder = "e.g., Shift 2",
                leadingIcon = Icons.Default.Schedule,
                testTag = "input_copy_shift_name"
            )

            Spacer(modifier = Modifier.height(12.dp))

            ShiftDateTimePickerRow(
                label = "New Shift Start Time *",
                millis = newStartMillis,
                badgeText = offsetLabel.takeIf { it.isNotBlank() },
                onPickDate = {
                    showDatePicker(context, newStartMillis) { picked ->
                        newStartMillis = picked
                        errorMessage = null
                    }
                },
                onPickTime = {
                    showTimePicker(context, newStartMillis) { picked ->
                        newStartMillis = picked
                        errorMessage = null
                    }
                },
                dateButtonTag = "btn_copy_shift_pick_date",
                timeButtonTag = "btn_copy_shift_pick_time"
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
                        Text("Copy Manpower Quotas", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = copyRequirements, onCheckedChange = { copyRequirements = it })
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Copy Assigned Staff", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = copyAssignments, onCheckedChange = { copyAssignments = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Tasks to Copy (${selectedTaskIds.size}/${previewList.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (previewList.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selectedTaskIds.clear()
                        selectedTaskIds.addAll(previewList.map { it.taskId })
                    }) { Text("All") }
                    TextButton(onClick = {
                        selectedTaskIds.clear()
                        selectedTaskIds.addAll(previewList.filter { it.defaultSelected }.map { it.taskId })
                    }) { Text("Shift 2 Default") }
                    TextButton(onClick = { selectedTaskIds.clear() }) { Text("Clear") }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (previewList.isEmpty()) {
                        Text(
                            text = "No tasks in source shift. An empty shift will be created.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        previewList.forEachIndexed { idx, preview ->
                            val isChecked = preview.taskId in selectedTaskIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isChecked) selectedTaskIds.remove(preview.taskId)
                                        else selectedTaskIds.add(preview.taskId)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked && !isChecked) selectedTaskIds.add(preview.taskId)
                                        else if (!checked) selectedTaskIds.remove(preview.taskId)
                                    },
                                    modifier = Modifier.testTag("checkbox_copy_task_${preview.taskId}")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preview.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (preview.isShift1Only) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = TaskzWarning.copy(alpha = 0.16f),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = "Shift 1 only",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TaskzWarning,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${preview.sourceTime} → ${preview.newTime}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx < previewList.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
                        newStartsAt = newStartsAtIso,
                        copyRequirements = copyRequirements,
                        copyAssignments = copyAssignments,
                        selectedTaskIds = selectedTaskIds.toList(),
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
                    Text(
                        if (previewList.isEmpty()) "Create Shift"
                        else "Copy ${selectedTaskIds.size} Task(s)"
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftDateTimePickerRow(
    label: String,
    millis: Long,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    dateButtonTag: String,
    timeButtonTag: String,
    badgeText: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickDate,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(dateButtonTag)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(formatPickerDate(millis), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onPickTime,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(timeButtonTag)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(formatPickerTime(millis), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
