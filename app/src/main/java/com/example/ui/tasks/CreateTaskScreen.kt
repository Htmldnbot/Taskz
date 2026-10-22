package com.example.ui.tasks

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.core.model.AlarmType
import com.example.core.model.CompletionPolicy
import com.example.core.model.ResponseFormField
import com.example.core.model.ResponseType
import com.example.core.model.TaskGroup
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.ui.tasks.create.StepAlarmConfig
import com.example.ui.tasks.create.StepAssignment
import com.example.ui.tasks.create.StepBasicInfo
import com.example.ui.tasks.create.StepImportance
import com.example.ui.tasks.create.StepResponseForm
import com.example.ui.tasks.create.StepReviewCreate
import com.example.ui.tasks.create.StepTaskType
import com.example.ui.tasks.create.StepTiming
import com.example.ui.tasks.create.StepVisibility
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    tasksViewModel: TasksViewModel,
    groupsViewModel: com.example.ui.groups.GroupsViewModel? = null,
    shiftsViewModel: com.example.ui.shifts.ShiftsViewModel? = null,
    preselectedGroupId: String? = null,
    preselectedShiftId: String? = null,
    onSuccess: (TaskItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by tasksViewModel.taskGroupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by tasksViewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tasksViewModel.actionEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    val totalSteps = 9

    // Step 1: Basic Info
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<TaskGroup?>(null) }

    // Step 2: Task Type
    var taskType by rememberSaveable { mutableStateOf(TaskType.SIMPLE) }
    var targetPercentage by rememberSaveable { mutableIntStateOf(100) }

    // Step 3: Timing
    var urgencyType by rememberSaveable { mutableStateOf(TimeUrgency.BEFORE_DEADLINE) }
    var executionTime by rememberSaveable { mutableStateOf("") }
    var deadlineTime by rememberSaveable { mutableStateOf("") }
    var repeatIntervalMinutes by rememberSaveable { mutableStateOf<Int?>(15) }

    // Step 4: Importance
    var importance by rememberSaveable { mutableStateOf(TaskImportance.NORMAL) }

    // Step 5: Response Form
    var responseType by rememberSaveable { mutableStateOf(ResponseType.COMPLETED_NOT_COMPLETED) }
    val customFormFields = remember { mutableStateListOf<ResponseFormField>() }

    // Step 6: Assignment & Completion Policy
    var lanListText by rememberSaveable { mutableStateOf("") }
    var completionPolicy by rememberSaveable { mutableStateOf(CompletionPolicy.SINGLE_ASSIGNEE) }

    // Step 7: Alarm Config
    var alarmType by rememberSaveable { mutableStateOf(AlarmType.SOUND_ALERT) }
    val advanceReminders = remember { mutableStateListOf(0, 5) }
    var remindUntilCompleted by rememberSaveable { mutableStateOf(false) }
    var overdueIntervalMinutes by rememberSaveable { mutableIntStateOf(5) }

    // Step 8: Visibility & Join Settings
    var visibility by rememberSaveable { mutableStateOf("everyone") }
    var allowSelfJoin by rememberSaveable { mutableStateOf(false) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(true) }
    var requiresCreatorConfirmation by rememberSaveable { mutableStateOf(false) }

    // Initial group selection
    remember(groups, preselectedGroupId) {
        if (selectedGroup == null && preselectedGroupId != null) {
            selectedGroup = groups.firstOrNull { it.id == preselectedGroupId }
        }
    }

    // Step navigation back handler
    BackHandler {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onBackClick()
        }
    }

    fun parseLanList(): List<String> {
        if (lanListText.isBlank()) return emptyList()
        return lanListText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // Validate inputs
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (title.isBlank()) errors.add("Task Title cannot be empty.")
        if (taskType == TaskType.PROGRESS && (targetPercentage <= 0 || targetPercentage > 100)) {
            errors.add("Target Percentage must be between 1 and 100.")
        }
        if (taskType == TaskType.MULTI_CONFIRMATION && parseLanList().isEmpty()) {
            errors.add("Multi-Confirmation task requires at least one LAN number (e.g. LAN1, LAN2).")
        }
        if (taskType != TaskType.IMMEDIATE && executionTime.isNotBlank()) {
            val timingErr = TaskItem.validateExecutionTime(executionTime)
            if (timingErr != null) {
                errors.add(timingErr)
            }
        }
        return errors
    }

    fun submitTask() {
        val validationErrors = validate()
        if (validationErrors.isNotEmpty()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(validationErrors.first())
            }
            return
        }
        if (isLoading) return

        // Serialize dynamic form fields
        val schemaArray = JSONArray()
        customFormFields.forEach { f ->
            schemaArray.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("label", f.label)
                    put("type", f.fieldType.name)
                    put("required", f.isRequired)
                }
            )
        }

        val effectiveExecTime = executionTime.trim().takeIf { it.isNotBlank() }
            ?: if (taskType == TaskType.IMMEDIATE) java.time.Instant.now().toString() else null

        val newTask = TaskItem(
            groupId = selectedGroup?.id ?: "",
            groupTitle = selectedGroup?.title,
            shiftId = preselectedShiftId,
            title = title.trim(),
            description = description.trim().takeIf { it.isNotBlank() },
            location = location.trim().takeIf { it.isNotBlank() },
            startsAt = effectiveExecTime,
            dueAt = deadlineTime.trim().takeIf { it.isNotBlank() },
            priority = importance.name.lowercase(),
            visibility = visibility,
            allowSelfJoin = allowSelfJoin,
            joinRequiresApproval = joinRequiresApproval,
            requiresCompletionConfirmation = requiresCreatorConfirmation,
            taskType = taskType,
            importance = importance,
            urgencyType = urgencyType,
            executionTime = effectiveExecTime,
            targetPercentage = targetPercentage,
            repeatIntervalMinutes = repeatIntervalMinutes,
            alarmType = alarmType,
            advanceRemindersMinutes = advanceReminders.toList(),
            remindUntilCompleted = remindUntilCompleted,
            overdueReminderIntervalMinutes = overdueIntervalMinutes,
            responseType = responseType,
            lanTargetList = parseLanList(),
            completionPolicy = completionPolicy,
            responseFormSchemaJson = if (schemaArray.length() > 0) schemaArray.toString() else null
        )

        tasksViewModel.createTask(newTask) { createdTask ->
            onSuccess(createdTask)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create Task ($currentStep of $totalSteps)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep -= 1 else onBackClick()
                        }
                    ) {
                        Icon(
                            if (currentStep > 1) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep == 1) {
                        OutlinedButton(
                            onClick = { currentStep = 2 },
                            enabled = title.isNotBlank() && !isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_next_step")
                        ) {
                            Text("Customize")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                        Button(
                            onClick = { submitTask() },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("btn_quick_create_task")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Task Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (currentStep < totalSteps) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                if (currentStep == 3 && taskType != TaskType.IMMEDIATE && executionTime.isNotBlank()) {
                                    val timingErr = TaskItem.validateExecutionTime(executionTime)
                                    if (timingErr != null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(timingErr)
                                        }
                                        return@Button
                                    }
                                }
                                currentStep += 1
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_next_step")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    } else {
                        // Final step: Create Task
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }
                        Button(
                            onClick = { submitTask() },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("btn_submit_create_task")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create & Schedule Task", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (currentStep) {
                1 -> StepBasicInfo(
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    location = location,
                    onLocationChange = { location = it },
                    selectedGroup = selectedGroup,
                    onGroupSelected = { selectedGroup = it },
                    availableGroups = groups
                )
                2 -> StepTaskType(
                    selectedType = taskType,
                    onTypeSelected = { taskType = it },
                    targetPercentage = targetPercentage,
                    onTargetPercentageChange = { targetPercentage = it }
                )
                3 -> StepTiming(
                    taskType = taskType,
                    urgencyType = urgencyType,
                    onUrgencySelected = {
                        if (taskType == TaskType.IMMEDIATE) {
                            taskType = TaskType.SIMPLE
                        }
                        urgencyType = it
                    },
                    executionTime = executionTime,
                    onExecutionTimeChange = { newTime ->
                        if (taskType == TaskType.IMMEDIATE && newTime.isNotBlank()) {
                            taskType = TaskType.SIMPLE
                        }
                        executionTime = newTime
                    },
                    deadlineTime = deadlineTime,
                    onDeadlineTimeChange = { deadlineTime = it },
                    repeatIntervalMinutes = repeatIntervalMinutes,
                    onRepeatIntervalChange = { repeatIntervalMinutes = it },
                    timingErrorMessage = if (taskType == TaskType.IMMEDIATE) null else TaskItem.validateExecutionTime(executionTime),
                    onExecuteNowSelected = {
                        taskType = TaskType.IMMEDIATE
                        urgencyType = TimeUrgency.IMMEDIATELY
                        executionTime = ""
                    },
                    onScheduleForLaterSelected = {
                        if (taskType == TaskType.IMMEDIATE) {
                            taskType = TaskType.SIMPLE
                            urgencyType = TimeUrgency.BEFORE_DEADLINE
                        }
                    }
                )
                4 -> StepImportance(
                    selectedImportance = importance,
                    onImportanceSelected = { importance = it }
                )
                5 -> StepResponseForm(
                    responseType = responseType,
                    onResponseTypeChange = { responseType = it },
                    customFields = customFormFields,
                    onAddField = { customFormFields.add(it) },
                    onRemoveField = { id -> customFormFields.removeAll { it.id == id } }
                )
                6 -> StepAssignment(
                    lanListText = lanListText,
                    onLanListChange = { lanListText = it },
                    completionPolicy = completionPolicy,
                    onCompletionPolicyChange = { completionPolicy = it }
                )
                7 -> StepAlarmConfig(
                    alarmType = alarmType,
                    onAlarmTypeChange = { alarmType = it },
                    advanceReminders = advanceReminders,
                    onToggleReminder = { mins ->
                        if (advanceReminders.contains(mins)) advanceReminders.remove(mins) else advanceReminders.add(mins)
                    },
                    remindUntilCompleted = remindUntilCompleted,
                    onRemindUntilCompletedChange = { remindUntilCompleted = it },
                    overdueIntervalMinutes = overdueIntervalMinutes,
                    onOverdueIntervalChange = { overdueIntervalMinutes = it }
                )
                8 -> StepVisibility(
                    visibility = visibility,
                    onVisibilityChange = { visibility = it },
                    allowSelfJoin = allowSelfJoin,
                    onAllowSelfJoinChange = { allowSelfJoin = it },
                    joinRequiresApproval = joinRequiresApproval,
                    onJoinRequiresApprovalChange = { joinRequiresApproval = it },
                    requiresCreatorConfirmation = requiresCreatorConfirmation,
                    onRequiresCreatorConfirmationChange = { requiresCreatorConfirmation = it }
                )
                9 -> StepReviewCreate(
                    title = title,
                    taskType = taskType,
                    importance = importance,
                    executionTime = executionTime,
                    deadlineTime = deadlineTime,
                    responseType = responseType,
                    lanList = parseLanList(),
                    completionPolicy = completionPolicy,
                    alarmType = alarmType,
                    advanceReminders = advanceReminders,
                    validationErrors = validate(),
                    onNavigateToScheduling = { currentStep = 3 }
                )
            }
        }
    }
}
