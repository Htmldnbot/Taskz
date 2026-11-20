package com.example.ui.shifts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.TaskzApplication
import com.example.core.model.OrgCentre
import com.example.core.model.Shift
import com.example.core.model.TaskItem
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingTagChip
import com.example.ui.common.TaskzTextField
import com.example.ui.tasks.TaskCard
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
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
    isOnLeave: Boolean = false,
    onDeleteClick: (() -> Unit)? = null
) {
    NothingHairlineCard(
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = modifier.testTag("shift_card_${shift.id}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shift.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (shift.shiftNumber != null) {
                        Text(
                            text = "SHIFT #${shift.shiftNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (shift.status) {
                        "active" -> NothingGreen
                        "completed" -> NothingTheme.colors.textSecondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    NothingTagChip(
                        text = shift.status.uppercase(),
                        contentColor = statusColor,
                        containerColor = if (shift.status == "active") NothingGreen.copy(alpha = 0.14f) else NothingTheme.colors.surfaceElevated,
                        borderColor = if (shift.status == "active") NothingGreen.copy(alpha = 0.45f) else NothingTheme.colors.border
                    )
                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.testTag("btn_delete_shift_${shift.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Shift",
                                tint = NothingRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatShiftRange(shift.startsAt, shift.endsAt),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!shift.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = shift.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary,
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
                NothingTagChip(text = "${shift.taskCount} TASKS")
                NothingTagChip(text = "${shift.requiredManpower} REQ")
                NothingTagChip(
                    text = "${shift.assignedManpower} ASGN",
                    contentColor = NothingGreen,
                    containerColor = NothingGreen.copy(alpha = 0.14f),
                    borderColor = NothingGreen.copy(alpha = 0.45f)
                )

                if (shift.vacantManpower > 0) {
                    NothingTagChip(
                        text = "${shift.vacantManpower} VAC",
                        contentColor = NothingRed,
                        containerColor = NothingRed.copy(alpha = 0.14f),
                        borderColor = NothingRed.copy(alpha = 0.45f)
                    )
                }
            }

            if (onJoinAllTasksClick != null && shift.taskCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onJoinAllTasksClick,
                    enabled = !isOnLeave,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
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

    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by TaskzApplication.instance.taskzSyncManager.orgHierarchy.collectAsStateWithLifecycle()
    val quickLans = remember(orgHierarchy.centres, authContext.scope.centreId, authContext.scope.centreCode) {
        val matched = orgHierarchy.centres.firstOrNull {
            (authContext.scope.centreId != null && it.id == authContext.scope.centreId) ||
                (!authContext.scope.centreCode.isNullOrBlank() && it.code == authContext.scope.centreCode)
        }
        matched?.resolvedLans()
            ?: authContext.scope.centreCode?.let { OrgCentre.defaultLansForCode(it) }
            ?: (1..11).map { "LAN $it" }
    }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
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
                NothingDotMatrixText(
                    text = "CREATE SHIFT",
                    fontSize = 20.sp
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

            Spacer(modifier = Modifier.height(14.dp))

            NothingSectionHeader(title = "LANS (OPTIONAL)")
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
                        label = {
                            Text(
                                text = lan,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NothingTheme.colors.accentPrimary,
                            selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                            containerColor = NothingTheme.colors.surfaceElevated,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = checked,
                            borderColor = NothingTheme.colors.border,
                            selectedBorderColor = NothingTheme.colors.accentPrimary
                        ),
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
            NothingHairlineCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exam Shift Checklist",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Auto-create ${STANDARD_EXAM_CHECKLIST.size} exam tasks from T0",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary
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
                        NothingDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Drive Download Anchor (T0)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = NothingTheme.colors.textSecondary
                                )
                                Text(
                                    text = formatPickerTime(checklistAnchorMillis),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    showTimePicker(context, checklistAnchorMillis) { picked ->
                                        checklistAnchorMillis = picked
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, NothingTheme.colors.border),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set T0", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selectedChecklistKeys.clear()
                                selectedChecklistKeys.addAll(STANDARD_EXAM_CHECKLIST.map { it.key })
                            }) { Text("All (${STANDARD_EXAM_CHECKLIST.size})") }
                            TextButton(onClick = {
                                selectedChecklistKeys.clear()
                                selectedChecklistKeys.addAll(STANDARD_EXAM_CHECKLIST.filter { !it.shift1Only }.map { it.key })
                            }) { Text("Shift 2+ (${STANDARD_EXAM_CHECKLIST.count { !it.shift1Only }})") }
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
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                            color = NothingAmber
                                        )
                                    }
                                }
                                Text(
                                    text = itemTime,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
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
                    color = NothingRed,
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
                    }.joinToString(" · ").takeIf { it.isNotBlank() }

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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_create_shift")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NothingTheme.colors.onAccentPrimary
                    )
                } else {
                    Text(
                        text = if (loadStandardChecklist && selectedChecklistKeys.isNotEmpty()) {
                            "Create Shift & ${selectedChecklistKeys.size} Tasks"
                        } else {
                            "Create Shift"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    Scaffold(containerColor = NothingTheme.colors.background) { innerPadding ->
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NothingTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                NothingDotMatrixText(
                    text = "EDIT SHIFT",
                    fontSize = 18.sp,
                    letterSpacing = 1.2.sp,
                    color = NothingTheme.colors.textPrimary
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

            NothingSectionHeader(title = "STATUS")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("scheduled", "active", "completed", "cancelled").forEach { s ->
                    val selected = status == s
                    FilterChip(
                        selected = selected,
                        onClick = { status = s },
                        label = {
                            Text(
                                text = s.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily)
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = NothingTheme.colors.surface,
                            labelColor = NothingTheme.colors.textSecondary,
                            selectedContainerColor = NothingTheme.colors.accentPrimary,
                            selectedLabelColor = NothingTheme.colors.onAccentPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = NothingTheme.colors.border,
                            selectedBorderColor = NothingTheme.colors.accentPrimary
                        )
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = NothingRed,
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NothingTheme.colors.onAccentPrimary
                    )
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
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
    var showDeleteShiftDialog by remember { mutableStateOf(false) }
    val orgHierarchy by TaskzApplication.instance.taskzSyncManager.orgHierarchy.collectAsStateWithLifecycle()

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
        containerColor = NothingTheme.colors.background,
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NothingTheme.colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    NothingDotMatrixText(
                        text = "SHIFT DETAILS",
                        fontSize = 18.sp,
                        letterSpacing = 1.2.sp,
                        color = NothingTheme.colors.textPrimary
                    )
                }

                val canDeleteShift = canManage || shiftsViewModel.canDeleteShift(activeShift)
                if (canManage || canDeleteShift) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canManage) {
                            IconButton(
                                onClick = { onEditShiftClick(activeShift) },
                                modifier = Modifier.testTag("btn_edit_shift")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Shift",
                                    tint = NothingTheme.colors.textPrimary
                                )
                            }
                        }
                        if (canDeleteShift) {
                            IconButton(
                                onClick = { showDeleteShiftDialog = true },
                                modifier = Modifier.testTag("btn_delete_shift_detail")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Shift",
                                    tint = NothingRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            NothingHairlineCard(
                shape = RoundedCornerShape(14.dp),
                containerColor = NothingTheme.colors.surface,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeShift.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = NothingTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NothingTagChip(
                        text = activeShift.status.uppercase(),
                        containerColor = if (activeShift.status == "active") NothingGreen.copy(alpha = 0.14f) else NothingTheme.colors.surfaceElevated,
                        contentColor = if (activeShift.status == "active") NothingGreen else NothingTheme.colors.textPrimary,
                        borderColor = if (activeShift.status == "active") NothingGreen.copy(alpha = 0.45f) else NothingTheme.colors.border
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatShiftRange(activeShift.startsAt, activeShift.endsAt),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NothingTheme.colors.textPrimary
                    )
                }

                if (!activeShift.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeShift.notes!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingDotMatrixText(
                            text = shiftTasks.size.toString(),
                            fontSize = 20.sp,
                            color = NothingTheme.colors.textPrimary
                        )
                        Text(
                            text = "TASKS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingDotMatrixText(
                            text = activeShift.requiredManpower.toString(),
                            fontSize = 20.sp,
                            color = NothingTheme.colors.textPrimary
                        )
                        Text(
                            text = "REQUIRED",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingDotMatrixText(
                            text = activeShift.assignedManpower.toString(),
                            fontSize = 20.sp,
                            color = NothingGreen
                        )
                        Text(
                            text = "ASSIGNED",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingDotMatrixText(
                            text = activeShift.vacantManpower.toString(),
                            fontSize = 20.sp,
                            color = if (activeShift.vacantManpower > 0) NothingRed else NothingTheme.colors.textPrimary
                        )
                        Text(
                            text = "VACANCIES",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if ((canSelfJoin || authContext.isOnLeave) && shiftTasks.isNotEmpty()) {
                Button(
                    onClick = { shiftsViewModel.joinShiftTasksBulk(activeShift.id) },
                    enabled = !authContext.isOnLeave,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
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
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
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
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingTheme.colors.accentPrimary,
                            contentColor = NothingTheme.colors.onAccentPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_add_shift_task")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Task", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showChecklistDialog = true },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
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
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
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

            NothingSectionHeader(
                title = "SHIFT TASKS (${shiftTasks.size})",
                useDotMatrix = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (shiftTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NothingEmptyState(
                            icon = Icons.Default.Checklist,
                            title = "NO SHIFT TASKS",
                            subtitle = "Load a standard checklist or create tasks for this shift."
                        )
                        if (canManage) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showChecklistDialog = true },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingTheme.colors.accentPrimary,
                                    contentColor = NothingTheme.colors.onAccentPrimary
                                )
                            ) {
                                Text("Load ${STANDARD_EXAM_CHECKLIST.size} Exam Tasks", fontWeight = FontWeight.Bold)
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
        val quickLans = remember(orgHierarchy.centres, authContext.scope.centreId, authContext.scope.centreCode) {
            val matched = orgHierarchy.centres.firstOrNull {
                (authContext.scope.centreId != null && it.id == authContext.scope.centreId) ||
                    (!authContext.scope.centreCode.isNullOrBlank() && it.code == authContext.scope.centreCode)
            }
            matched?.resolvedLans()
                ?: authContext.scope.centreCode?.let { OrgCentre.defaultLansForCode(it) }
                ?: (1..11).map { "LAN $it" }
        }
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
            containerColor = NothingTheme.colors.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                NothingDotMatrixText(
                    text = "EXAM CHECKLIST",
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = NothingTheme.colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DRIVE DOWNLOAD (T0)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textSecondary
                            )
                            Text(
                                text = formatPickerTime(anchorMillis),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NothingTheme.colors.textPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                showTimePicker(context, anchorMillis) { picked -> anchorMillis = picked }
                            },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, NothingTheme.colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary)
                        ) {
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
                                label = {
                                    Text(
                                        text = lan,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily)
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = NothingTheme.colors.surfaceElevated,
                                    labelColor = NothingTheme.colors.textSecondary,
                                    selectedContainerColor = NothingTheme.colors.accentPrimary,
                                    selectedLabelColor = NothingTheme.colors.onAccentPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = NothingTheme.colors.border,
                                    selectedBorderColor = NothingTheme.colors.accentPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            selectedKeys.clear()
                            selectedKeys.addAll(STANDARD_EXAM_CHECKLIST.map { it.key })
                        }) {
                            Text(
                                "ALL",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textPrimary
                            )
                        }
                        TextButton(onClick = {
                            selectedKeys.clear()
                            selectedKeys.addAll(STANDARD_EXAM_CHECKLIST.filter { !it.shift1Only }.map { it.key })
                        }) {
                            Text(
                                "SHIFT 2+",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textPrimary
                            )
                        }
                        TextButton(onClick = { selectedKeys.clear() }) {
                            Text(
                                "CLEAR",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textSecondary
                            )
                        }
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
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = NothingTheme.colors.accentPrimary,
                                    checkmarkColor = NothingTheme.colors.onAccentPrimary,
                                    uncheckedColor = NothingTheme.colors.textSecondary
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = NothingTheme.colors.textPrimary
                                )
                                if (item.shift1Only) {
                                    Text(
                                        text = "Shift 1 only",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                        color = NothingAmber
                                    )
                                }
                            }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NothingTheme.colors.textPrimary
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
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_load_exam_checklist")
                ) {
                    Text("Add ${selectedKeys.size} Tasks", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChecklistDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = NothingTheme.colors.textSecondary)
                }
            }
        )
    }

    if (showDeleteShiftDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteShiftDialog = false },
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
                    text = "Delete '${activeShift.name}' and all ${shiftTasks.size} tasks scheduled under it? Scheduled alarms will be cancelled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteShiftDialog = false
                        shiftsViewModel.deleteShift(activeShift.id) { onBackClick() }
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_shift_detail_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteShiftDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = NothingTheme.colors.textSecondary)
                }
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

    Scaffold(containerColor = NothingTheme.colors.background) { innerPadding ->
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NothingTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                NothingDotMatrixText(
                    text = "COPY SHIFT & TASKS",
                    fontSize = 18.sp,
                    letterSpacing = 1.2.sp,
                    color = NothingTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            NothingHairlineCard(
                shape = RoundedCornerShape(14.dp),
                containerColor = NothingTheme.colors.surface,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Source: ${sourceShift.name}",
                    fontWeight = FontWeight.Bold,
                    color = NothingTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatShiftRange(sourceShift.startsAt, sourceShift.endsAt),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textPrimary
                )
                Text(
                    text = "${sourceTasks.size} tasks available",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
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

            NothingSectionHeader(title = "COPY OPTIONS", useDotMatrix = true)
            Spacer(modifier = Modifier.height(8.dp))

            NothingHairlineCard(
                shape = RoundedCornerShape(14.dp),
                containerColor = NothingTheme.colors.surface,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Copy Manpower Quotas",
                        fontWeight = FontWeight.SemiBold,
                        color = NothingTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = copyRequirements, onCheckedChange = { copyRequirements = it })
                }

                Spacer(modifier = Modifier.height(10.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Copy Assigned Staff",
                        fontWeight = FontWeight.SemiBold,
                        color = NothingTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = copyAssignments, onCheckedChange = { copyAssignments = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            NothingSectionHeader(
                title = "SELECT TASKS TO COPY (${selectedTaskIds.size}/${previewList.size})",
                useDotMatrix = true
            )

            if (previewList.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selectedTaskIds.clear()
                        selectedTaskIds.addAll(previewList.map { it.taskId })
                    }) {
                        Text(
                            "ALL",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textPrimary
                        )
                    }
                    TextButton(onClick = {
                        selectedTaskIds.clear()
                        selectedTaskIds.addAll(previewList.filter { it.defaultSelected }.map { it.taskId })
                    }) {
                        Text(
                            "SHIFT 2 DEFAULT",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textPrimary
                        )
                    }
                    TextButton(onClick = { selectedTaskIds.clear() }) {
                        Text(
                            "CLEAR",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }
            }

            NothingHairlineCard(
                shape = RoundedCornerShape(14.dp),
                containerColor = NothingTheme.colors.surface,
                contentPadding = PaddingValues(12.dp)
            ) {
                if (previewList.isEmpty()) {
                    Text(
                        text = "No tasks in source shift. An empty shift will be created.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
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
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = NothingTheme.colors.accentPrimary,
                                    checkmarkColor = NothingTheme.colors.onAccentPrimary,
                                    uncheckedColor = NothingTheme.colors.textSecondary
                                ),
                                modifier = Modifier.testTag("checkbox_copy_task_${preview.taskId}")
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preview.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (isChecked) NothingTheme.colors.textPrimary else NothingTheme.colors.textSecondary
                                )
                                if (preview.isShift1Only) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    NothingTagChip(
                                        text = "SHIFT 1 ONLY",
                                        containerColor = NothingAmber.copy(alpha = 0.14f),
                                        contentColor = NothingAmber,
                                        borderColor = NothingAmber.copy(alpha = 0.45f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${preview.sourceTime} → ${preview.newTime}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isChecked) NothingTheme.colors.textPrimary else NothingTheme.colors.textSecondary
                            )
                        }
                        if (idx < previewList.lastIndex) {
                            NothingDivider()
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = NothingRed,
                    style = MaterialTheme.typography.bodySmall
                )
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_confirm_copy_shift")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NothingTheme.colors.onAccentPrimary
                    )
                } else {
                    Text(
                        text = if (previewList.isEmpty()) "Create Shift"
                        else "Copy ${selectedTaskIds.size} Task(s)",
                        fontWeight = FontWeight.Bold
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
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = TechMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NothingTheme.colors.textSecondary
            )
            if (!badgeText.isNullOrBlank()) {
                NothingTagChip(text = badgeText.uppercase())
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickDate,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NothingTheme.colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(dateButtonTag)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatPickerDate(millis),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onPickTime,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NothingTheme.colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(timeButtonTag)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatPickerTime(millis),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
