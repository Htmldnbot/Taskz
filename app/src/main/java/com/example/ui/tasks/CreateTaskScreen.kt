package com.example.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AlarmType
import com.example.core.model.CompletionPolicy
import com.example.core.model.ResponseType
import com.example.core.model.TaskGroup
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.core.model.UserProfile
import kotlinx.coroutines.launch

enum class TaskScopeLevel(val label: String, val icon: ImageVector) {
    LAN("LAN / Room", Icons.Default.Router),
    TEST_CENTRE("Test Centre", Icons.Default.Business),
    CITY("City", Icons.Default.LocationCity),
    STATE("State", Icons.Default.LocationOn),
    NATIONAL("National", Icons.Default.Public)
}

enum class SimpleTaskPriority(
    val title: String,
    val subtitle: String,
    val importance: TaskImportance,
    val alarmType: AlarmType,
    val priorityString: String,
    val icon: ImageVector
) {
    VERY_HIGH(
        "Very High",
        "Full screen notification with sound",
        TaskImportance.CRITICAL,
        AlarmType.FULL_SCREEN_ALARM,
        "urgent",
        Icons.Default.Alarm
    ),
    HIGH(
        "High",
        "Full screen notification, no sound (vibrate)",
        TaskImportance.HIGH,
        AlarmType.FULL_SCREEN_ALARM,
        "high",
        Icons.Default.NotificationsActive
    ),
    MEDIUM(
        "Medium",
        "Notification with sound",
        TaskImportance.NORMAL,
        AlarmType.SOUND_ALERT,
        "normal",
        Icons.Default.Notifications
    ),
    LOW(
        "Low",
        "Notification only (silent)",
        TaskImportance.LOW,
        AlarmType.SILENT,
        "low",
        Icons.Default.NotificationsOff
    )
}

enum class SimpleTaskType(val title: String, val subtitle: String) {
    YES_NO("Yes / No", "Assignee marks Done or Not Done"),
    PROGRESSIVE("Progressive (% Done)", "Assignee updates % completion progress")
}

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
    val searchResults by tasksViewModel.directoryEmployees.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by tasksViewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tasksViewModel.actionEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 1. Basic Info
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    // 2. Scope Level (LAN, Test Centre, City, State, National)
    var selectedScope by rememberSaveable { mutableStateOf(TaskScopeLevel.TEST_CENTRE) }
    var lanText by rememberSaveable { mutableStateOf("LAN 1") }
    var cityName by rememberSaveable { mutableStateOf("Jaipur") }
    var stateName by rememberSaveable { mutableStateOf("Rajasthan") }
    var selectedGroup by remember { mutableStateOf<TaskGroup?>(null) }
    var centreDropdownExpanded by remember { mutableStateOf(false) }

    // 3. Task Priority
    var selectedPriority by rememberSaveable { mutableStateOf(SimpleTaskPriority.MEDIUM) }

    // 4. Task Type
    var selectedTaskType by rememberSaveable { mutableStateOf(SimpleTaskType.YES_NO) }
    var targetPercentage by rememberSaveable { mutableIntStateOf(100) }

    // 5. Exact Alarm Time & Execution Schedule (Exact Hours & Minutes)
    val initialTime = remember { java.time.LocalTime.now().plusMinutes(15) }
    var alarmHour by rememberSaveable { mutableIntStateOf(initialTime.hour) }
    var alarmMinute by rememberSaveable { mutableIntStateOf(initialTime.minute) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // 6. Snooze / Unfinished Remind Time (5, 10, 20, Custom)
    var selectedSnoozeOption by rememberSaveable { mutableStateOf("10") } // "5", "10", "20", "custom"
    var customSnoozeMinutes by rememberSaveable { mutableStateOf("15") }

    // 7. Who can join & Member Invitations
    var whoCanJoinScope by rememberSaveable { mutableStateOf("Test Centre") }
    var allowSelfJoin by rememberSaveable { mutableStateOf(true) }
    var memberSearchQuery by rememberSaveable { mutableStateOf("") }
    val selectedInvitees = remember { mutableStateListOf<UserProfile>() }
    var autoAssignedNotice by remember { mutableStateOf<String?>(null) }

    // Initialise default centre if available
    remember(groups, preselectedGroupId) {
        if (selectedGroup == null) {
            selectedGroup = if (preselectedGroupId != null) {
                groups.firstOrNull { it.id == preselectedGroupId }
            } else {
                groups.firstOrNull()
            }
        }
    }

    // Auto-assign personnel checked in today at the chosen Centre / LAN
    LaunchedEffect(selectedGroup, selectedScope, lanText) {
        val centreCode = selectedGroup?.code?.ifBlank { "141" } ?: "141"
        try {
            val checkins = com.example.TaskzApplication.instance.systemIncidentRepository.getTodayCheckinsForCentre(centreCode)
            if (checkins.isNotEmpty()) {
                val matching = when (selectedScope) {
                    TaskScopeLevel.LAN -> {
                        val cleanLan = lanText.trim().lowercase()
                        checkins.filter { it.role == "VCO" || it.lan?.lowercase()?.contains(cleanLan) == true }
                    }
                    else -> checkins
                }

                var added = 0
                matching.forEach { chk ->
                    val user = UserProfile(
                        id = chk.userId,
                        employeeId = chk.employeeId,
                        fullName = "${chk.userName} (${if (chk.role == "VCO") "VCO" else chk.lan ?: "IT Mgr"})",
                        mobile = "",
                        appRole = if (chk.role == "VCO") "admin" else "employee",
                        isActive = true
                    )
                    if (selectedInvitees.none { it.id == user.id || (user.employeeId.isNotBlank() && it.employeeId == user.employeeId) }) {
                        selectedInvitees.add(user)
                        added++
                    }
                }
                if (added > 0) {
                    autoAssignedNotice = "Auto-assigned $added personnel stationed at Centre $centreCode today ✓"
                }
            }
        } catch (_: Exception) {}
    }

    BackHandler {
        onBackClick()
    }

    fun submitTask() {
        if (title.trim().isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Please enter a Task Title")
            }
            return
        }

        val resolvedSnoozeMinutes = if (selectedSnoozeOption == "custom") {
            customSnoozeMinutes.toIntOrNull() ?: 10
        } else {
            selectedSnoozeOption.toIntOrNull() ?: 10
        }

        val resolvedLocation = when (selectedScope) {
            TaskScopeLevel.LAN -> "LAN: ${lanText.ifBlank { "All LANs" }} • ${selectedGroup?.title ?: "Jaipur"}"
            TaskScopeLevel.TEST_CENTRE -> selectedGroup?.title ?: "Centre 141 • Jaipur"
            TaskScopeLevel.CITY -> "City: ${cityName.ifBlank { "Jaipur" }}, $stateName"
            TaskScopeLevel.STATE -> "State: ${stateName.ifBlank { "Rajasthan" }}"
            TaskScopeLevel.NATIONAL -> "All India (National Operations)"
        }

        val parsedLans = if (selectedScope == TaskScopeLevel.LAN && lanText.isNotBlank()) {
            lanText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val now = java.time.LocalDateTime.now()
        var targetDateTime = now.withHour(alarmHour).withMinute(alarmMinute).withSecond(0).withNano(0)
        if (targetDateTime.isBefore(now)) {
            // If the exact time has already passed today, schedule for next occurrence or +5 minutes
            targetDateTime = targetDateTime.plusDays(1)
        }
        val scheduledInstant = targetDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()
        val scheduledIso = scheduledInstant.toString()

        val newTask = TaskItem(
            groupId = selectedGroup?.id ?: "centre_141",
            groupTitle = selectedGroup?.title ?: "141 · Yagyavalkya Institute of Technology",
            shiftId = preselectedShiftId,
            title = title.trim(),
            description = description.trim().takeIf { it.isNotBlank() },
            location = resolvedLocation,
            startsAt = scheduledIso,
            dueAt = scheduledIso,
            priority = selectedPriority.priorityString,
            visibility = if (whoCanJoinScope == "National") "everyone" else whoCanJoinScope.lowercase(),
            allowSelfJoin = allowSelfJoin,
            joinRequiresApproval = false,
            requiresCompletionConfirmation = false,
            taskType = if (selectedTaskType == SimpleTaskType.PROGRESSIVE) TaskType.PROGRESS else TaskType.TIME_SPECIFIC,
            importance = selectedPriority.importance,
            urgencyType = TimeUrgency.SPECIFIC_TIME,
            executionTime = scheduledIso,
            targetPercentage = if (selectedTaskType == SimpleTaskType.PROGRESSIVE) targetPercentage else 100,
            alarmType = selectedPriority.alarmType,
            advanceRemindersMinutes = listOf(0),
            remindUntilCompleted = true,
            overdueReminderIntervalMinutes = resolvedSnoozeMinutes,
            responseType = if (selectedTaskType == SimpleTaskType.PROGRESSIVE) ResponseType.PERCENTAGE else ResponseType.YES_NO,
            lanTargetList = parsedLans,
            completionPolicy = CompletionPolicy.SINGLE_ASSIGNEE
        )

        tasksViewModel.createTask(
            task = newTask,
            selectedEmployeeIds = selectedInvitees.map { it.id.ifBlank { it.employeeId } }
        ) { createdTask ->
            onSuccess(createdTask)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Create Task", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Simplified & Fast Task Assignment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { submitTask() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_create_task_now")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Task",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. BASIC INFORMATION
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Task Essentials",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title *") },
                        placeholder = { Text("e.g., Network Cable Check, Biometric Setup") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_title_input"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Instructions / Details (Optional)") },
                        placeholder = { Text("Provide clear steps or guidelines for the team...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_desc_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // ==========================================
            // 2. OPERATIONAL LEVEL / SCOPE
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "2. Scope Level (Where does this task apply?)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(TaskScopeLevel.values()) { scope ->
                            val isSelected = selectedScope == scope
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedScope = scope },
                                label = { Text(scope.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = scope.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    // Contextual Field according to Scope
                    when (selectedScope) {
                        TaskScopeLevel.LAN -> {
                            OutlinedTextField(
                                value = lanText,
                                onValueChange = { lanText = it },
                                label = { Text("LAN / Room Numbers *") },
                                placeholder = { Text("e.g., LAN 1, LAN 2, Server Room") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        TaskScopeLevel.TEST_CENTRE -> {
                            ExposedDropdownMenuBox(
                                expanded = centreDropdownExpanded,
                                onExpandedChange = { centreDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedGroup?.title ?: "Select Examination Centre",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Examination Centre") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = centreDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = centreDropdownExpanded,
                                    onDismissRequest = { centreDropdownExpanded = false }
                                ) {
                                    groups.forEach { group ->
                                        DropdownMenuItem(
                                            text = { Text(group.title) },
                                            onClick = {
                                                selectedGroup = group
                                                centreDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        TaskScopeLevel.CITY -> {
                            OutlinedTextField(
                                value = cityName,
                                onValueChange = { cityName = it },
                                label = { Text("Target City") },
                                placeholder = { Text("e.g., Jaipur, Delhi, Mumbai") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        TaskScopeLevel.STATE -> {
                            OutlinedTextField(
                                value = stateName,
                                onValueChange = { stateName = it },
                                label = { Text("Target State") },
                                placeholder = { Text("e.g., Rajasthan, Maharashtra") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        TaskScopeLevel.NATIONAL -> {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Applies across all centres nationwide (All India operations).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. TASK PRIORITY & ALERT NOTIFICATION
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "3. Priority & Notification Alert",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    SimpleTaskPriority.values().forEach { priority ->
                        val isSelected = selectedPriority == priority
                        OutlinedCard(
                            onClick = { selectedPriority = priority },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { selectedPriority = priority })
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = priority.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = priority.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = priority.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. TASK TYPE (YES/NO vs PROGRESSIVE)
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "4. Task Type",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SimpleTaskType.values().forEach { type ->
                            val isSelected = selectedTaskType == type
                            OutlinedCard(
                                onClick = { selectedTaskType = type },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedTaskType = type }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = type.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = type.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // If Progressive, choose Target %
                    AnimatedVisibility(visible = selectedTaskType == SimpleTaskType.PROGRESSIVE) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Target Completion Percentage: $targetPercentage%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(25, 50, 75, 100).forEach { pct ->
                                    FilterChip(
                                        selected = targetPercentage == pct,
                                        onClick = { targetPercentage = pct },
                                        label = { Text("$pct%") },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. ALARM TIME / EXECUTION TIME (Kb Alarm Bajega)
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "5. Alarm Time (Alarm Kb Bajega)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Set the exact time (Hour & Minute) when this task alarm will ring on assignee devices:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val context = androidx.compose.ui.platform.LocalContext.current

                    // Exact Time Display & Picker Card
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val displayHour12 = when {
                                alarmHour == 0 -> 12
                                alarmHour > 12 -> alarmHour - 12
                                else -> alarmHour
                            }
                            val isPm = alarmHour >= 12
                            val formattedTimeStr = String.format("%02d:%02d %s", displayHour12, alarmMinute, if (isPm) "PM" else "AM")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Target Alarm Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formattedTimeStr,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Button(
                                    onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, h, m ->
                                                alarmHour = h
                                                alarmMinute = m
                                            },
                                            alarmHour,
                                            alarmMinute,
                                            false
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Exact Time")
                                }
                            }

                            HorizontalDivider()

                            // Interactive Hour, Minute & AM/PM Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hour Input
                                OutlinedTextField(
                                    value = String.format("%02d", displayHour12),
                                    onValueChange = { input ->
                                        val hVal = input.filter { it.isDigit() }.toIntOrNull()
                                        if (hVal != null && hVal in 1..12) {
                                            alarmHour = if (isPm) {
                                                if (hVal == 12) 12 else hVal + 12
                                            } else {
                                                if (hVal == 12) 0 else hVal
                                            }
                                        }
                                    },
                                    label = { Text("Hour") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Text(":", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))

                                // Minute Input
                                OutlinedTextField(
                                    value = String.format("%02d", alarmMinute),
                                    onValueChange = { input ->
                                        val mVal = input.filter { it.isDigit() }.take(2).toIntOrNull()
                                        if (mVal != null && mVal in 0..59) {
                                            alarmMinute = mVal
                                        }
                                    },
                                    label = { Text("Minute") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // AM/PM Toggle
                                Row(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = !isPm,
                                        onClick = {
                                            if (isPm) alarmHour = (alarmHour - 12).coerceAtLeast(0)
                                        },
                                        label = { Text("AM") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = isPm,
                                        onClick = {
                                            if (!isPm) alarmHour = (alarmHour + 12).coerceAtMost(23)
                                        },
                                        label = { Text("PM") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Quick Time Shortcuts
                            Text(
                                text = "Quick Shortcuts from Now:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "+5m" to 5,
                                    "+15m" to 15,
                                    "+30m" to 30,
                                    "+1 Hr" to 60
                                ).forEach { (label, mins) ->
                                    AssistChip(
                                        onClick = {
                                            val t = java.time.LocalTime.now().plusMinutes(mins.toLong())
                                            alarmHour = t.hour
                                            alarmMinute = t.minute
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val displayH = when {
                                alarmHour == 0 -> 12
                                alarmHour > 12 -> alarmHour - 12
                                else -> alarmHour
                            }
                            val amPm = if (alarmHour >= 12) "PM" else "AM"
                            val chosenLocal = java.time.LocalTime.of(alarmHour, alarmMinute)
                            val dayLabel = if (chosenLocal.isBefore(java.time.LocalTime.now())) "Tomorrow" else "Today"
                            Text(
                                text = String.format("Alarm set for exact time: %02d:%02d %s %s", displayH, alarmMinute, amPm, dayLabel),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 6. UNFINISHED SNOOZE / REMINDER
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Snooze, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "5. Snooze & Unfinished Reminder",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "If task is not done, repeat reminder alert after:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("5", "10", "20", "custom").forEach { opt ->
                            val label = if (opt == "custom") "Custom Mins" else "$opt Mins"
                            FilterChip(
                                selected = selectedSnoozeOption == opt,
                                onClick = { selectedSnoozeOption = opt },
                                label = { Text(label) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = selectedSnoozeOption == "custom") {
                        OutlinedTextField(
                            value = customSnoozeMinutes,
                            onValueChange = { customSnoozeMinutes = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Reminder Interval (in minutes)") },
                            placeholder = { Text("e.g., 15, 30, 45") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 6. WHO CAN JOIN & MEMBER INVITES
            // ==========================================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "6. Who Can Join & Invite Access",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Who has access to view and join this task?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Test Centre", "City", "State", "National", "LAN Only")) { scope ->
                            FilterChip(
                                selected = whoCanJoinScope == scope,
                                onClick = { whoCanJoinScope = scope },
                                label = { Text(scope) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow Self-Join",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Eligible employees in this scope can join the task directly",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowSelfJoin,
                            onCheckedChange = { allowSelfJoin = it }
                        )
                    }

                    if (!autoAssignedNotice.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = autoAssignedNotice ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Invite Members via Search (Name, Email, or Phone)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = memberSearchQuery,
                        onValueChange = {
                            memberSearchQuery = it
                            tasksViewModel.searchEmployees(it)
                        },
                        label = { Text("Search Employee") },
                        placeholder = { Text("Type name, email, or mobile...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Selected Members Chips
                    if (selectedInvitees.isNotEmpty()) {
                        Text(
                            text = "Assigned Members (${selectedInvitees.size}):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedInvitees) { user ->
                                FilterChip(
                                    selected = true,
                                    onClick = { selectedInvitees.remove(user) },
                                    label = { Text("${user.fullName} (${user.employeeId})") },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }

                    // Search Results List
                    if (memberSearchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                searchResults.take(4).forEach { candidate ->
                                    val isAdded = selectedInvitees.any { it.id == candidate.id || it.employeeId == candidate.employeeId }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!isAdded) selectedInvitees.add(candidate)
                                                else selectedInvitees.removeAll { it.id == candidate.id || it.employeeId == candidate.employeeId }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = candidate.fullName.take(2).uppercase(),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = candidate.fullName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "${candidate.employeeId} • ${candidate.mobile}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (!isAdded) selectedInvitees.add(candidate)
                                                else selectedInvitees.removeAll { it.id == candidate.id || it.employeeId == candidate.employeeId }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = if (isAdded) androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ) else androidx.compose.material3.ButtonDefaults.buttonColors()
                                        ) {
                                            Text(if (isAdded) "Added ✓" else "+ Add", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
