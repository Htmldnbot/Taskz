package com.example.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AlarmType
import com.example.core.model.ResponseType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.core.model.UserProfile
import com.example.ui.common.TaskzTextField
import com.example.ui.groups.GroupsViewModel
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateTaskScreen(
    preselectedGroupId: String? = null,
    preselectedShiftId: String? = null,
    tasksViewModel: TasksViewModel,
    groupsViewModel: GroupsViewModel,
    shiftsViewModel: ShiftsViewModel,
    onSuccess: (TaskItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by groupsViewModel.groupsFlow.collectAsStateWithLifecycle()
    val activeRoles by tasksViewModel.activeWorkRolesFlow.collectAsStateWithLifecycle()
    val directoryEmployees by tasksViewModel.directoryEmployees.collectAsStateWithLifecycle()
    val isLoading by tasksViewModel.isLoading.collectAsStateWithLifecycle()

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val totalSteps = 9

    // Step 1: Basic Information
    var selectedGroupId by rememberSaveable { mutableStateOf(preselectedGroupId ?: groups.firstOrNull()?.id ?: "") }
    var selectedShiftId by rememberSaveable { mutableStateOf(preselectedShiftId) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }

    // Step 2: Task Type
    var taskType by rememberSaveable { mutableStateOf(TaskType.TIME_SPECIFIC) }

    // Step 3: Timing / Deadline
    var urgencyType by rememberSaveable { mutableStateOf(TimeUrgency.SPECIFIC_TIME) }
    val nowFormatted = remember { LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
    val nowPlusTwoHours = remember { LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
    var executionTimeText by rememberSaveable { mutableStateOf(nowFormatted) }
    var startTimeText by rememberSaveable { mutableStateOf(nowFormatted) }
    var dueTimeText by rememberSaveable { mutableStateOf(nowPlusTwoHours) }
    var targetPercentage by rememberSaveable { mutableIntStateOf(100) }
    var repeatIntervalMinutes by rememberSaveable { mutableIntStateOf(15) }

    // Step 4: Task Importance
    var importance by rememberSaveable { mutableStateOf(TaskImportance.HIGH) }

    // Step 5: Response Type
    var responseType by rememberSaveable { mutableStateOf(ResponseType.COMPLETED_NOT_COMPLETED) }

    // Step 6: Assigned Employees / Roles / LANs
    val selectedLans = remember { mutableStateListOf<String>() }
    val selectedEmployeeIds = remember { mutableStateListOf<String>() }
    val manpowerRequirements = remember { mutableStateListOf<Pair<String, Int>>() }

    // Step 7: Reminder & Alarm Settings
    var alarmType by rememberSaveable { mutableStateOf(AlarmType.SOUND_ALERT) }
    val selectedAdvanceReminders = remember { mutableStateListOf(0, 5, 10) }
    var remindUntilCompleted by rememberSaveable { mutableStateOf(true) }
    var overdueReminderInterval by rememberSaveable { mutableIntStateOf(5) }
    var maxOverdueReminders by rememberSaveable { mutableIntStateOf(3) }

    // Step 8: Visibility & Join Settings
    var visibility by rememberSaveable { mutableStateOf("everyone") }
    var allowSelfJoin by rememberSaveable { mutableStateOf(true) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(true) }
    var notifyCreatorOnJoin by rememberSaveable { mutableStateOf(true) }
    var requiresCompletionConfirmation by rememberSaveable { mutableStateOf(false) }

    // Validation / Error
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Back handling within wizard
    BackHandler {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onBackClick()
        }
    }

    // Default alarm type sync with importance
    fun updateDefaultAlarmType(newImportance: TaskImportance) {
        importance = newImportance
        alarmType = when (newImportance) {
            TaskImportance.CRITICAL -> AlarmType.FULL_SCREEN_ALARM
            TaskImportance.HIGH -> AlarmType.SOUND_ALERT
            TaskImportance.NORMAL -> AlarmType.NORMAL_NOTIFICATION
            TaskImportance.LOW -> AlarmType.SILENT
        }
    }

    // Default response type sync with task type
    fun updateDefaultTaskType(newType: TaskType) {
        taskType = newType
        when (newType) {
            TaskType.SIMPLE -> {
                urgencyType = TimeUrgency.BEFORE_DEADLINE
                responseType = ResponseType.COMPLETED_NOT_COMPLETED
            }
            TaskType.TIME_SPECIFIC -> {
                urgencyType = TimeUrgency.SPECIFIC_TIME
                responseType = ResponseType.COMPLETED_NOT_COMPLETED
            }
            TaskType.DEADLINE -> {
                urgencyType = TimeUrgency.BEFORE_DEADLINE
                responseType = ResponseType.COMPLETED_NOT_COMPLETED
            }
            TaskType.IMMEDIATE -> {
                urgencyType = TimeUrgency.IMMEDIATELY
                importance = TaskImportance.CRITICAL
                alarmType = AlarmType.FULL_SCREEN_ALARM
                responseType = ResponseType.ACKNOWLEDGE_ONLY
            }
            TaskType.PROGRESS -> {
                urgencyType = TimeUrgency.BEFORE_DEADLINE
                responseType = ResponseType.PERCENTAGE
            }
            TaskType.MULTI_CONFIRMATION -> {
                urgencyType = TimeUrgency.SPECIFIC_TIME
                responseType = ResponseType.MULTI_CONFIRMATION
            }
            TaskType.RECURRING -> {
                urgencyType = TimeUrgency.RECURRING_INTERVAL
                responseType = ResponseType.YES_NO
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep -= 1 else onBackClick()
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Step $currentStep of $totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = getStepTitle(currentStep),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${((currentStep.toFloat() / totalSteps) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    if (currentStep < totalSteps) {
                        Button(
                            onClick = {
                                if (validateStep(currentStep, title, selectedGroupId)) {
                                    errorMessage = null
                                    currentStep += 1
                                } else {
                                    errorMessage = getValidationErrorMessage(currentStep, title, selectedGroupId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_wizard_next")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (validateStep(1, title, selectedGroupId)) {
                                    errorMessage = null
                                    val execIso = parseToIso(executionTimeText)
                                    val startIso = parseToIso(startTimeText)
                                    val dueIso = parseToIso(dueTimeText)

                                    tasksViewModel.createTask(
                                        groupId = selectedGroupId,
                                        shiftId = selectedShiftId,
                                        title = title,
                                        description = description,
                                        location = location,
                                        startsAt = if (taskType == TaskType.IMMEDIATE) null else startIso,
                                        dueAt = if (taskType == TaskType.IMMEDIATE) null else dueIso,
                                        priority = importance.name.lowercase(),
                                        visibility = visibility,
                                        selectedEmployeeIds = selectedEmployeeIds.toList(),
                                        allowSelfJoin = allowSelfJoin,
                                        joinRequiresApproval = joinRequiresApproval,
                                        notifyCreatorOnJoin = notifyCreatorOnJoin,
                                        requiresCompletionConfirmation = requiresCompletionConfirmation,
                                        maxParticipants = null,
                                        requirements = manpowerRequirements.toList(),
                                        taskType = taskType,
                                        importance = importance,
                                        urgencyType = urgencyType,
                                        executionTime = if (taskType == TaskType.IMMEDIATE) null else execIso,
                                        targetPercentage = targetPercentage,
                                        repeatIntervalMinutes = repeatIntervalMinutes,
                                        alarmType = alarmType,
                                        advanceRemindersMinutes = selectedAdvanceReminders.toList(),
                                        remindUntilCompleted = remindUntilCompleted,
                                        overdueReminderIntervalMinutes = overdueReminderInterval,
                                        maxOverdueReminders = maxOverdueReminders,
                                        responseType = responseType,
                                        lanTargetList = selectedLans.toList(),
                                        onSuccess = onSuccess
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_create_task_final"),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Task & Alarms", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when (currentStep) {
                1 -> Step1BasicInformation(
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    location = location,
                    onLocationChange = { location = it },
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onGroupIdChange = { selectedGroupId = it },
                    shiftsViewModel = shiftsViewModel,
                    selectedShiftId = selectedShiftId,
                    onShiftIdChange = { selectedShiftId = it }
                )
                2 -> Step2TaskType(
                    selectedType = taskType,
                    onTypeSelected = { updateDefaultTaskType(it) }
                )
                3 -> Step3TimingAndDeadline(
                    taskType = taskType,
                    urgencyType = urgencyType,
                    onUrgencyChange = { urgencyType = it },
                    executionTime = executionTimeText,
                    onExecutionTimeChange = { executionTimeText = it },
                    startTime = startTimeText,
                    onStartTimeChange = { startTimeText = it },
                    dueTime = dueTimeText,
                    onDueTimeChange = { dueTimeText = it },
                    targetPercentage = targetPercentage,
                    onTargetPercentageChange = { targetPercentage = it },
                    repeatIntervalMinutes = repeatIntervalMinutes,
                    onRepeatIntervalChange = { repeatIntervalMinutes = it }
                )
                4 -> Step4Importance(
                    selectedImportance = importance,
                    onImportanceSelected = { updateDefaultAlarmType(it) }
                )
                5 -> Step5ResponseType(
                    selectedResponse = responseType,
                    onResponseSelected = { responseType = it }
                )
                6 -> Step6AssignedRolesAndLans(
                    selectedLans = selectedLans,
                    activeRoles = activeRoles,
                    manpowerRequirements = manpowerRequirements,
                    directoryEmployees = directoryEmployees,
                    selectedEmployeeIds = selectedEmployeeIds,
                    onSearchEmployees = { tasksViewModel.searchEmployees(it) }
                )
                7 -> Step7ReminderAndAlarmSettings(
                    alarmType = alarmType,
                    onAlarmTypeChange = { alarmType = it },
                    selectedAdvanceReminders = selectedAdvanceReminders,
                    remindUntilCompleted = remindUntilCompleted,
                    onRemindUntilCompletedChange = { remindUntilCompleted = it },
                    overdueReminderInterval = overdueReminderInterval,
                    onOverdueIntervalChange = { overdueReminderInterval = it },
                    maxOverdueReminders = maxOverdueReminders,
                    onMaxOverdueChange = { maxOverdueReminders = it }
                )
                8 -> Step8VisibilityAndJoinSettings(
                    visibility = visibility,
                    onVisibilityChange = { visibility = it },
                    allowSelfJoin = allowSelfJoin,
                    onAllowSelfJoinChange = { allowSelfJoin = it },
                    joinRequiresApproval = joinRequiresApproval,
                    onJoinRequiresApprovalChange = { joinRequiresApproval = it },
                    notifyCreatorOnJoin = notifyCreatorOnJoin,
                    onNotifyCreatorChange = { notifyCreatorOnJoin = it },
                    requiresCompletionConfirmation = requiresCompletionConfirmation,
                    onRequiresCompletionConfirmationChange = { requiresCompletionConfirmation = it }
                )
                9 -> Step9ReviewAndAlarmPreview(
                    title = title,
                    taskType = taskType,
                    importance = importance,
                    urgencyType = urgencyType,
                    executionTimeText = executionTimeText,
                    startTimeText = startTimeText,
                    dueTimeText = dueTimeText,
                    alarmType = alarmType,
                    advanceReminders = selectedAdvanceReminders,
                    remindUntilCompleted = remindUntilCompleted,
                    overdueInterval = overdueReminderInterval,
                    maxOverdue = maxOverdueReminders,
                    selectedLans = selectedLans,
                    responseType = responseType
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 1: Basic Information
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step1BasicInformation(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    groups: List<com.example.core.model.TaskGroup>,
    selectedGroupId: String,
    onGroupIdChange: (String) -> Unit,
    shiftsViewModel: ShiftsViewModel,
    selectedShiftId: String?,
    onShiftIdChange: (String?) -> Unit
) {
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }
    val groupShifts by remember(selectedGroupId) { shiftsViewModel.getShiftsForGroup(selectedGroupId) }.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Enter the primary details for this operational task.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskzTextField(
            value = title,
            onValueChange = onTitleChange,
            label = "Task Title *",
            placeholder = "e.g., Download Examination Drive",
            leadingIcon = Icons.Default.EventNote,
            testTag = "input_task_title"
        )

        // Group selector
        ExposedDropdownMenuBox(
            expanded = groupDropdownExpanded,
            onExpandedChange = { groupDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = groups.find { it.id == selectedGroupId }?.title ?: "Select Group",
                onValueChange = {},
                readOnly = true,
                label = { Text("Belongs to Group *") },
                leadingIcon = { Icon(imageVector = Icons.Default.Groups, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = groupDropdownExpanded,
                onDismissRequest = { groupDropdownExpanded = false }
            ) {
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.title) },
                        onClick = {
                            onGroupIdChange(group.id)
                            onShiftIdChange(null)
                            groupDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Shift selector (optional)
        ExposedDropdownMenuBox(
            expanded = shiftDropdownExpanded,
            onExpandedChange = { shiftDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = groupShifts.find { it.id == selectedShiftId }?.name ?: "No specific shift (Optional)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Belongs to Shift (Optional)") },
                leadingIcon = { Icon(imageVector = Icons.Default.Schedule, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shiftDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = shiftDropdownExpanded,
                onDismissRequest = { shiftDropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("None (Full Group Task)") },
                    onClick = {
                        onShiftIdChange(null)
                        shiftDropdownExpanded = false
                    }
                )
                groupShifts.forEach { shift ->
                    DropdownMenuItem(
                        text = { Text("${shift.name} (${shift.startsAt.ifBlank { "N/A" }})") },
                        onClick = {
                            onShiftIdChange(shift.id)
                            shiftDropdownExpanded = false
                        }
                    )
                }
            }
        }

        TaskzTextField(
            value = location,
            onValueChange = onLocationChange,
            label = "Location / Station",
            placeholder = "e.g., Server Room 1 / Gate 2 / Lab 4",
            leadingIcon = Icons.Default.Place,
            testTag = "input_task_location"
        )

        TaskzTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = "Task Instructions / Description",
            placeholder = "Detailed instructions for executing this task...",
            leadingIcon = Icons.Default.Description,
            singleLine = false,
            testTag = "input_task_description"
        )
    }
}

// -------------------------------------------------------------
// STEP 2: Task Type
// -------------------------------------------------------------
@Composable
private fun Step2TaskType(
    selectedType: TaskType,
    onTypeSelected: (TaskType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "WHAT TYPE OF TASK IS THIS?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Taskz provides specialized execution models tailored for time-critical center operations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskType.values().forEach { type ->
            val isSelected = selectedType == type
            val icon = getTaskTypeIcon(type)

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTypeSelected(type) }
                    .testTag("task_type_${type.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = type.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 3: Timing / Deadline
// -------------------------------------------------------------
@Composable
private fun Step3TimingAndDeadline(
    taskType: TaskType,
    urgencyType: TimeUrgency,
    onUrgencyChange: (TimeUrgency) -> Unit,
    executionTime: String,
    onExecutionTimeChange: (String) -> Unit,
    startTime: String,
    onStartTimeChange: (String) -> Unit,
    dueTime: String,
    onDueTimeChange: (String) -> Unit,
    targetPercentage: Int,
    onTargetPercentageChange: (Int) -> Unit,
    repeatIntervalMinutes: Int,
    onRepeatIntervalChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "WHEN DOES THIS TASK NEED ACTION?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        when (taskType) {
            TaskType.IMMEDIATE -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TaskzError.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, tint = TaskzError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IMMEDIATE EXECUTION REQUIRED", fontWeight = FontWeight.Bold, color = TaskzError)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "This task triggers an instant emergency alert upon creation. No future date or schedule is required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            TaskType.TIME_SPECIFIC -> {
                Text("Specify the exact execution date and time for this operational action:", style = MaterialTheme.typography.bodySmall)
                TaskzTextField(
                    value = executionTime,
                    onValueChange = onExecutionTimeChange,
                    label = "Execution Time (YYYY-MM-DD HH:MM) *",
                    placeholder = "2026-10-16 06:00",
                    leadingIcon = Icons.Default.AccessTime,
                    testTag = "input_execution_time"
                )
            }
            TaskType.DEADLINE -> {
                Text("Specify the allowed execution window and strict deadline:", style = MaterialTheme.typography.bodySmall)
                TaskzTextField(
                    value = startTime,
                    onValueChange = onStartTimeChange,
                    label = "Start Window Time",
                    placeholder = "2026-10-16 05:30",
                    leadingIcon = Icons.Default.DateRange
                )
                TaskzTextField(
                    value = dueTime,
                    onValueChange = onDueTimeChange,
                    label = "Strict Deadline (YYYY-MM-DD HH:MM) *",
                    placeholder = "2026-10-16 07:00",
                    leadingIcon = Icons.Default.AccessTime,
                    testTag = "input_deadline_time"
                )
            }
            TaskType.PROGRESS -> {
                Text("Target deadline and completion percentage target:", style = MaterialTheme.typography.bodySmall)
                TaskzTextField(
                    value = dueTime,
                    onValueChange = onDueTimeChange,
                    label = "Completion Deadline *",
                    placeholder = "2026-10-16 07:00",
                    leadingIcon = Icons.Default.AccessTime
                )
                TaskzTextField(
                    value = targetPercentage.toString(),
                    onValueChange = { onTargetPercentageChange(it.toIntOrNull() ?: 100) },
                    label = "Target Percentage (0-100%)",
                    placeholder = "100",
                    leadingIcon = Icons.Default.TrendingUp
                )
            }
            TaskType.RECURRING -> {
                Text("Interval and operational schedule:", style = MaterialTheme.typography.bodySmall)
                TaskzTextField(
                    value = startTime,
                    onValueChange = onStartTimeChange,
                    label = "Start Time *",
                    placeholder = "2026-10-16 06:00",
                    leadingIcon = Icons.Default.DateRange
                )
                TaskzTextField(
                    value = dueTime,
                    onValueChange = onDueTimeChange,
                    label = "End Time",
                    placeholder = "2026-10-16 18:00",
                    leadingIcon = Icons.Default.AccessTime
                )
                Text("Repeat Interval:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = repeatIntervalMinutes == mins,
                            onClick = { onRepeatIntervalChange(mins) },
                            label = { Text("Every $mins mins") }
                        )
                    }
                }
            }
            else -> {
                // Simple Task
                TaskzTextField(
                    value = dueTime,
                    onValueChange = onDueTimeChange,
                    label = "Optional Due Time (YYYY-MM-DD HH:MM)",
                    placeholder = "2026-10-16 12:00",
                    leadingIcon = Icons.Default.AccessTime
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 4: Task Importance
// -------------------------------------------------------------
@Composable
private fun Step4Importance(
    selectedImportance: TaskImportance,
    onImportanceSelected: (TaskImportance) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "HOW IMPORTANT IS THIS TASK?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Importance sets the alarm intensity, visual priority, and escalation policies without modifying scheduled timestamps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskImportance.values().forEach { imp ->
            val isSelected = selectedImportance == imp
            val color = when (imp) {
                TaskImportance.CRITICAL -> TaskzError
                TaskImportance.HIGH -> TaskzWarning
                TaskImportance.NORMAL -> MaterialTheme.colorScheme.primary
                TaskImportance.LOW -> MaterialTheme.colorScheme.outline
            }
            val desc = when (imp) {
                TaskImportance.CRITICAL -> "Full-Screen alarm on lock screen, loud sirens, immediate acknowledgment required"
                TaskImportance.HIGH -> "High priority sound alerts, vibration, repeat reminders"
                TaskImportance.NORMAL -> "Standard notification and task list prominence"
                TaskImportance.LOW -> "Silent notification, passive task tracking"
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onImportanceSelected(imp) }
                    .testTag("importance_${imp.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (imp == TaskImportance.CRITICAL) Icons.Default.PriorityHigh else Icons.Default.Shield,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = imp.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = color
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = color)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 5: Response Type
// -------------------------------------------------------------
@Composable
private fun Step5ResponseType(
    selectedResponse: ResponseType,
    onResponseSelected: (ResponseType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "HOW SHOULD EMPLOYEES RESPOND?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Choose the confirmation data format expected from assigned center staff.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ResponseType.values().forEach { resp ->
            val isSelected = selectedResponse == resp
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResponseSelected(resp) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resp.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (isSelected) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 6: Assigned Roles, Employees, and LANs
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step6AssignedRolesAndLans(
    selectedLans: MutableList<String>,
    activeRoles: List<com.example.core.model.WorkRole>,
    manpowerRequirements: MutableList<Pair<String, Int>>,
    directoryEmployees: List<UserProfile>,
    selectedEmployeeIds: MutableList<String>,
    onSearchEmployees: (String) -> Unit
) {
    var employeeSearchQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "ASSIGNED TEAMS, LANs & ROLES",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        // LAN 1 to LAN 15 selector chips
        Text(
            text = "Select Applicable Examination LANs (LAN 1 to LAN 15):",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..15).forEach { num ->
                val lanName = "LAN $num"
                val isSelected = selectedLans.contains(lanName)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedLans.remove(lanName) else selectedLans.add(lanName)
                    },
                    label = { Text(lanName, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Role requirements
        Text(
            text = "Manpower Role Requirements:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        activeRoles.forEach { role ->
            val existing = manpowerRequirements.find { it.first == role.id }
            val count = existing?.second ?: 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(role.name, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (count > 0) {
                                manpowerRequirements.removeAll { it.first == role.id }
                                if (count > 1) manpowerRequirements.add(Pair(role.id, count - 1))
                            }
                        },
                        enabled = count > 0
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            manpowerRequirements.removeAll { it.first == role.id }
                            manpowerRequirements.add(Pair(role.id, count + 1))
                        }
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 7: Reminder & Alarm Settings
// -------------------------------------------------------------
@Composable
private fun Step7ReminderAndAlarmSettings(
    alarmType: AlarmType,
    onAlarmTypeChange: (AlarmType) -> Unit,
    selectedAdvanceReminders: MutableList<Int>,
    remindUntilCompleted: Boolean,
    onRemindUntilCompletedChange: (Boolean) -> Unit,
    overdueReminderInterval: Int,
    onOverdueIntervalChange: (Int) -> Unit,
    maxOverdueReminders: Int,
    onMaxOverdueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "ALARM & ADVANCE REMINDERS",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        // Alarm Type selector
        Text("Device Alarm Presentation:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        AlarmType.values().forEach { type ->
            val isSelected = alarmType == type
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlarmTypeChange(type) }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(type.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (isSelected) Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advance Reminders Multi-select
        Text("Trigger Advance Reminders Before Task:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        listOf(
            Pair(0, "At Task Time"),
            Pair(1, "1 min before"),
            Pair(2, "2 mins before"),
            Pair(5, "5 mins before"),
            Pair(10, "10 mins before"),
            Pair(15, "15 mins before"),
            Pair(30, "30 mins before")
        ).forEach { (minutes, label) ->
            val isChecked = selectedAdvanceReminders.contains(minutes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isChecked) selectedAdvanceReminders.remove(minutes) else selectedAdvanceReminders.add(minutes)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isChecked,
                    onCheckedChange = {
                        if (it) selectedAdvanceReminders.add(minutes) else selectedAdvanceReminders.remove(minutes)
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Remind until completed
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Remind Until Completed?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("Repeats alerts after deadline if task remains unfinished", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = remindUntilCompleted, onCheckedChange = onRemindUntilCompletedChange)
        }

        if (remindUntilCompleted) {
            Text("Repeat Alarm Every:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 15).forEach { mins ->
                    FilterChip(
                        selected = overdueReminderInterval == mins,
                        onClick = { onOverdueIntervalChange(mins) },
                        label = { Text("$mins mins") }
                    )
                }
            }

            Text("Maximum Overdue Repeat Count: $maxOverdueReminders times", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 5).forEach { count ->
                    FilterChip(
                        selected = maxOverdueReminders == count,
                        onClick = { onMaxOverdueChange(count) },
                        label = { Text("$count times") }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 8: Visibility & Join Settings
// -------------------------------------------------------------
@Composable
private fun Step8VisibilityAndJoinSettings(
    visibility: String,
    onVisibilityChange: (String) -> Unit,
    allowSelfJoin: Boolean,
    onAllowSelfJoinChange: (Boolean) -> Unit,
    joinRequiresApproval: Boolean,
    onJoinRequiresApprovalChange: (Boolean) -> Unit,
    notifyCreatorOnJoin: Boolean,
    onNotifyCreatorChange: (Boolean) -> Unit,
    requiresCompletionConfirmation: Boolean,
    onRequiresCompletionConfirmationChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "TASK VISIBILITY & ACCESS POLICY",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Text("Who can see this task?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = visibility == "everyone",
                onClick = { onVisibilityChange("everyone") },
                label = { Text("Everyone in Group") }
            )
            FilterChip(
                selected = visibility == "restricted",
                onClick = { onVisibilityChange("restricted") },
                label = { Text("Selected Staff Only") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingToggleRow(
            title = "Allow Employees to Self-Join",
            subtitle = "Staff can volunteer or pick up vacant positions",
            checked = allowSelfJoin,
            onCheckedChange = onAllowSelfJoinChange
        )

        if (allowSelfJoin) {
            SettingToggleRow(
                title = "Require Creator Approval to Join",
                subtitle = "Submissions remain pending until approved",
                checked = joinRequiresApproval,
                onCheckedChange = onJoinRequiresApprovalChange
            )
        }

        SettingToggleRow(
            title = "Notify Creator on Join",
            subtitle = "Sends push notification when staff joins",
            checked = notifyCreatorOnJoin,
            onCheckedChange = onNotifyCreatorChange
        )

        SettingToggleRow(
            title = "Require Final Completion Confirmation",
            subtitle = "Task stays in Pending Confirmation until verified by creator",
            checked = requiresCompletionConfirmation,
            onCheckedChange = onRequiresCompletionConfirmationChange
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// -------------------------------------------------------------
// STEP 9: Review & Alarm Preview
// -------------------------------------------------------------
@Composable
private fun Step9ReviewAndAlarmPreview(
    title: String,
    taskType: TaskType,
    importance: TaskImportance,
    urgencyType: TimeUrgency,
    executionTimeText: String,
    startTimeText: String,
    dueTimeText: String,
    alarmType: AlarmType,
    advanceReminders: List<Int>,
    remindUntilCompleted: Boolean,
    overdueInterval: Int,
    maxOverdue: Int,
    selectedLans: List<String>,
    responseType: ResponseType
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "ALARM & TASK SCHEDULE PREVIEW",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (importance) {
                    TaskImportance.CRITICAL -> TaskzError.copy(alpha = 0.12f)
                    TaskImportance.HIGH -> TaskzWarning.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (importance == TaskImportance.CRITICAL) Icons.Default.NotificationsActive else Icons.Default.Alarm,
                        contentDescription = null,
                        tint = when (importance) {
                            TaskImportance.CRITICAL -> TaskzError
                            TaskImportance.HIGH -> TaskzWarning
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TASK: $title".uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                PreviewRow(label = "TYPE", value = taskType.label)
                PreviewRow(label = "IMPORTANCE", value = importance.label)
                PreviewRow(label = "EXECUTION", value = if (taskType == TaskType.IMMEDIATE) "IMMEDIATELY" else executionTimeText.ifBlank { dueTimeText })
                
                val calculatedReminders = advanceReminders.sortedDescending().map { m ->
                    if (m == 0) "At task time" else "$m mins before"
                }
                PreviewRow(label = "REMINDERS", value = calculatedReminders.joinToString(", "))
                PreviewRow(label = "MAIN ALARM", value = "${alarmType.label} at task time")

                if (remindUntilCompleted) {
                    PreviewRow(label = "REPEAT", value = "Every $overdueInterval mins until completion (Max $maxOverdue times)")
                }

                if (selectedLans.isNotEmpty()) {
                    PreviewRow(label = "ASSIGNED TO", value = selectedLans.joinToString(", "))
                }

                PreviewRow(label = "RESPONSE TYPE", value = responseType.label)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = TaskzSuccess)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Upon creation, exact alarms will be registered natively in Android AlarmManager. Alarms will fire even without internet access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

private fun getStepTitle(step: Int): String = when (step) {
    1 -> "Basic Information"
    2 -> "Select Task Type"
    3 -> "Timing & Deadlines"
    4 -> "Task Importance"
    5 -> "Response Method"
    6 -> "Teams, LANs & Roles"
    7 -> "Alarm & Reminders"
    8 -> "Visibility & Access"
    9 -> "Review & Alarm Preview"
    else -> "Task Setup"
}

private fun validateStep(step: Int, title: String, groupId: String): Boolean = when (step) {
    1 -> title.trim().isNotBlank() && groupId.isNotBlank()
    else -> true
}

private fun getValidationErrorMessage(step: Int, title: String, groupId: String): String = when (step) {
    1 -> if (title.trim().isBlank()) "Please enter a Task Title" else "Please select a Group"
    else -> "Please complete required fields"
}

private fun getTaskTypeIcon(type: TaskType): ImageVector = when (type) {
    TaskType.SIMPLE -> Icons.Default.Check
    TaskType.TIME_SPECIFIC -> Icons.Default.Schedule
    TaskType.DEADLINE -> Icons.Default.HourglassTop
    TaskType.IMMEDIATE -> Icons.Default.FlashOn
    TaskType.PROGRESS -> Icons.Default.TrendingUp
    TaskType.MULTI_CONFIRMATION -> Icons.Default.Groups
    TaskType.RECURRING -> Icons.Default.Repeat
}

private fun parseToIso(dateStr: String): String? {
    if (dateStr.isBlank()) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val date = sdf.parse(dateStr)
        val outSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        outSdf.format(date ?: Date())
    } catch (e: Exception) {
        null
    }
}
