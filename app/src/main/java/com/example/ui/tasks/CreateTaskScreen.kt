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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.core.model.AlarmType
import com.example.core.model.CompletionPolicy
import com.example.core.model.OrgCentre
import com.example.core.model.OrgCity
import com.example.core.model.OrgState
import com.example.core.model.OrgZone
import com.example.core.model.ResponseType
import com.example.core.model.ScopeLevel
import com.example.core.model.ScopedMemberSearchResult
import com.example.core.model.TaskGroup
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TaskzRole
import com.example.core.model.TimeUrgency
import com.example.core.model.UserProfile
import kotlinx.coroutines.launch

enum class TaskScopeLevel(val label: String, val icon: ImageVector, val mappedScope: ScopeLevel) {
    LAN("LAN / Room", Icons.Default.Router, ScopeLevel.CENTRE),
    TEST_CENTRE("Test Centre", Icons.Default.Business, ScopeLevel.CENTRE),
    CITY("City", Icons.Default.LocationCity, ScopeLevel.CITY),
    STATE("State", Icons.Default.LocationOn, ScopeLevel.STATE),
    ZONE("Zone", Icons.Default.Public, ScopeLevel.ZONE),
    NATIONAL("National", Icons.Default.Public, ScopeLevel.GLOBAL)
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
        "Full screen alarm with sound & vibration",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val authContext by tasksViewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by tasksViewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val groups by tasksViewModel.taskGroupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchResults by tasksViewModel.directoryEmployees.collectAsStateWithLifecycle(initialValue = emptyList())
    val scopedSearchResults by tasksViewModel.scopedMembers.collectAsStateWithLifecycle(initialValue = emptyList())
    val isLoading by tasksViewModel.isLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (orgHierarchy.centres.isEmpty()) {
            tasksViewModel.refreshOrgHierarchy()
        }
        tasksViewModel.actionEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val allowedScopeEnums = remember(authContext.activeRole, authContext.isActive) {
        tasksViewModel.allowedTaskScopes()
    }
    val availableUiScopes = remember(allowedScopeEnums) {
        TaskScopeLevel.values().filter { it.mappedScope in allowedScopeEnums }
    }

    // 1. Basic Info
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    // 2. Scope Level & Cascaded Org Hierarchy
    var selectedScope by rememberSaveable { mutableStateOf(TaskScopeLevel.TEST_CENTRE) }
    LaunchedEffect(availableUiScopes) {
        if (selectedScope !in availableUiScopes && availableUiScopes.isNotEmpty()) {
            selectedScope = availableUiScopes.first()
        }
    }

    var lanText by rememberSaveable { mutableStateOf("LAN 1") }
    var cityName by rememberSaveable { mutableStateOf(authContext.scope.cityName ?: "Jaipur") }
    var stateName by rememberSaveable { mutableStateOf(authContext.scope.stateName ?: "Rajasthan") }
    var selectedGroup by remember { mutableStateOf<TaskGroup?>(null) }
    var centreDropdownExpanded by remember { mutableStateOf(false) }

    var selectedZone by remember { mutableStateOf<OrgZone?>(null) }
    var selectedState by remember { mutableStateOf<OrgState?>(null) }
    var selectedCity by remember { mutableStateOf<OrgCity?>(null) }
    var selectedCentre by remember { mutableStateOf<OrgCentre?>(null) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var orgCentreExpanded by remember { mutableStateOf(false) }

    val isGlobalRole = authContext.activeRole in setOf(TaskzRole.SUPER_ADMIN, TaskzRole.COMMAND_CENTER)
    val availableZones = remember(orgHierarchy.zones, authContext.scope, isGlobalRole) {
        if (isGlobalRole) orgHierarchy.zones
        else orgHierarchy.zones.filter { authContext.scope.zoneId != null && it.id == authContext.scope.zoneId }
    }
    val availableStates = remember(orgHierarchy.states, selectedZone, authContext.scope, authContext.activeRole, isGlobalRole) {
        orgHierarchy.states.filter { st ->
            (selectedZone == null || st.zoneId == selectedZone!!.id) && when {
                isGlobalRole -> true
                authContext.activeRole == TaskzRole.ZOM -> authContext.scope.zoneId != null && st.zoneId == authContext.scope.zoneId
                else -> authContext.scope.stateId != null && st.id == authContext.scope.stateId
            }
        }
    }
    val availableCities = remember(orgHierarchy.cities, selectedState, selectedZone, authContext.scope, authContext.activeRole, isGlobalRole) {
        orgHierarchy.cities.filter { ct ->
            (selectedState == null || ct.stateId == selectedState!!.id) &&
                (selectedZone == null || ct.zoneId == selectedZone!!.id) && when {
                    isGlobalRole -> true
                    authContext.activeRole == TaskzRole.ZOM -> authContext.scope.zoneId != null && ct.zoneId == authContext.scope.zoneId
                    authContext.activeRole == TaskzRole.ROM -> authContext.scope.stateId != null && ct.stateId == authContext.scope.stateId
                    else -> authContext.scope.cityId != null && ct.id == authContext.scope.cityId
                }
        }
    }
    val availableCentres = remember(orgHierarchy.centres, selectedCity, selectedState, selectedZone, authContext.scope, authContext.activeRole, isGlobalRole) {
        orgHierarchy.centres.filter { c ->
            (selectedCity == null || c.cityId == selectedCity!!.id) &&
                (selectedState == null || c.stateId == selectedState!!.id) &&
                (selectedZone == null || c.zoneId == selectedZone!!.id) && when {
                    isGlobalRole -> true
                    authContext.activeRole == TaskzRole.ZOM -> authContext.scope.zoneId != null && c.zoneId == authContext.scope.zoneId
                    authContext.activeRole == TaskzRole.ROM -> authContext.scope.stateId != null && c.stateId == authContext.scope.stateId
                    authContext.activeRole in setOf(TaskzRole.CITY_HEAD, TaskzRole.VCO, TaskzRole.IT_MANAGER) ->
                        (authContext.scope.cityId == null && authContext.scope.centreId == null) ||
                            (authContext.scope.cityId != null && c.cityId == authContext.scope.cityId) ||
                            (authContext.scope.centreId != null && c.id == authContext.scope.centreId)
                    else -> authContext.scope.centreId != null && c.id == authContext.scope.centreId
                }
        }
    }

    LaunchedEffect(orgHierarchy, authContext.scope, availableZones, availableStates, availableCities, availableCentres) {
        if (selectedZone == null && authContext.scope.zoneId != null) {
            selectedZone = orgHierarchy.zones.firstOrNull { it.id == authContext.scope.zoneId }
        }
        if (selectedState == null && authContext.scope.stateId != null) {
            selectedState = orgHierarchy.states.firstOrNull { it.id == authContext.scope.stateId }
        }
        if (selectedCity == null && authContext.scope.cityId != null) {
            selectedCity = orgHierarchy.cities.firstOrNull { it.id == authContext.scope.cityId }
        }
        if (selectedCentre == null && authContext.scope.centreId != null) {
            selectedCentre = orgHierarchy.centres.firstOrNull { it.id == authContext.scope.centreId }
        }
        if (selectedZone == null && availableZones.size == 1) selectedZone = availableZones.first()
        if (selectedState == null && availableStates.size == 1) selectedState = availableStates.first()
        if (selectedCity == null && availableCities.size == 1) selectedCity = availableCities.first()
        if (selectedCentre == null && availableCentres.isNotEmpty()) selectedCentre = availableCentres.first()
    }

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

    // 7. Target Roles, Who Can Join & Member Invitations
    val selectedTargetRoles = remember { mutableStateListOf<String>() }
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

    // Auto-load scoped directory colleagues across the caller's authorized city/scope
    LaunchedEffect(selectedScope, selectedCity?.id, selectedCentre?.id, selectedTargetRoles.toList()) {
        tasksViewModel.searchScopedMembers(
            query = memberSearchQuery,
            roleCode = selectedTargetRoles.singleOrNull(),
            centreId = null
        )
    }

    // Auto-assign personnel checked in today at the chosen Centre / LAN
    LaunchedEffect(selectedGroup, selectedCentre, selectedScope, lanText) {
        val centreCode = selectedCentre?.code
            ?: selectedGroup?.code?.takeIf { it.isNotBlank() && !it.equals("GEN-OPS", ignoreCase = true) }
            ?: authContext.scope.centreCode
        if (centreCode.isNullOrBlank()) return@LaunchedEffect
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

        val resolvedScopeLevel = selectedScope.mappedScope
        val fallbackCentre = selectedCentre
            ?: orgHierarchy.centres.firstOrNull { it.id == authContext.scope.centreId }
            ?: availableCentres.firstOrNull()
        val resolvedCentreId = if (resolvedScopeLevel == ScopeLevel.CENTRE) {
            fallbackCentre?.id ?: authContext.scope.centreId
        } else null
        val resolvedCityId = when (resolvedScopeLevel) {
            ScopeLevel.GLOBAL, ScopeLevel.ZONE, ScopeLevel.STATE -> null
            ScopeLevel.CITY -> selectedCity?.id ?: authContext.scope.cityId ?: availableCities.firstOrNull()?.id
            ScopeLevel.CENTRE -> fallbackCentre?.cityId ?: selectedCity?.id ?: authContext.scope.cityId ?: availableCities.firstOrNull()?.id
        }
        val resolvedStateId = when (resolvedScopeLevel) {
            ScopeLevel.GLOBAL, ScopeLevel.ZONE -> null
            ScopeLevel.STATE -> selectedState?.id ?: authContext.scope.stateId ?: availableStates.firstOrNull()?.id
            ScopeLevel.CITY -> selectedCity?.stateId ?: selectedState?.id ?: authContext.scope.stateId ?: availableStates.firstOrNull()?.id
            ScopeLevel.CENTRE -> fallbackCentre?.stateId ?: selectedCity?.stateId ?: authContext.scope.stateId ?: availableStates.firstOrNull()?.id
        }
        val resolvedZoneId = when (resolvedScopeLevel) {
            ScopeLevel.GLOBAL -> null
            ScopeLevel.ZONE -> selectedZone?.id ?: authContext.scope.zoneId ?: availableZones.firstOrNull()?.id
            ScopeLevel.STATE -> selectedState?.zoneId ?: selectedZone?.id ?: authContext.scope.zoneId ?: availableZones.firstOrNull()?.id
            ScopeLevel.CITY -> selectedCity?.zoneId ?: selectedState?.zoneId ?: selectedZone?.id ?: authContext.scope.zoneId ?: availableZones.firstOrNull()?.id
            ScopeLevel.CENTRE -> fallbackCentre?.zoneId ?: selectedCity?.zoneId ?: authContext.scope.zoneId ?: availableZones.firstOrNull()?.id
        }

        val scopeValidationError = when (resolvedScopeLevel) {
            ScopeLevel.CENTRE -> if (resolvedCentreId.isNullOrBlank()) "Please select your State, City & Examination Centre from Home or Profile first." else null
            ScopeLevel.CITY -> if (resolvedCityId.isNullOrBlank()) "Please select a target City first." else null
            ScopeLevel.STATE -> if (resolvedStateId.isNullOrBlank()) "Please select a target State first." else null
            ScopeLevel.ZONE -> if (resolvedZoneId.isNullOrBlank()) "Please select a target Zone first." else null
            ScopeLevel.GLOBAL -> null
        }
        if (scopeValidationError != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(scopeValidationError)
            }
            return
        }

        val resolvedLocation = when (selectedScope) {
            TaskScopeLevel.LAN -> "LAN: ${lanText.ifBlank { "All LANs" }} • ${fallbackCentre?.let { "${it.code} · ${it.name}" } ?: selectedGroup?.title ?: "Centre"}"
            TaskScopeLevel.TEST_CENTRE -> fallbackCentre?.let { "${it.code} · ${it.name}" } ?: selectedGroup?.title ?: "Examination Centre"
            TaskScopeLevel.CITY -> "City: ${selectedCity?.name ?: cityName.ifBlank { "Jaipur" }}, ${selectedState?.name ?: stateName}"
            TaskScopeLevel.STATE -> "State: ${selectedState?.name ?: stateName.ifBlank { "Rajasthan" }}"
            TaskScopeLevel.ZONE -> "Zone: ${selectedZone?.name ?: authContext.scope.zoneName ?: "Assigned Zone"}"
            TaskScopeLevel.NATIONAL -> "All India (National Operations)"
        }

        val parsedLans = if (selectedScope == TaskScopeLevel.LAN && lanText.isNotBlank()) {
            lanText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else emptyList()

        val now = java.time.LocalDateTime.now()
        var targetDateTime = now.withHour(alarmHour).withMinute(alarmMinute).withSecond(0).withNano(0)
        if (targetDateTime.isBefore(now)) {
            targetDateTime = targetDateTime.plusDays(1)
        }
        val scheduledInstant = targetDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant()
        val scheduledIso = scheduledInstant.toString()

        val newTask = TaskItem(
            groupId = selectedGroup?.id ?: "centre_141",
            groupTitle = selectedGroup?.title ?: fallbackCentre?.name ?: "General Operations",
            shiftId = preselectedShiftId,
            title = title.trim(),
            description = description.trim().takeIf { it.isNotBlank() },
            location = resolvedLocation,
            startsAt = scheduledIso,
            dueAt = scheduledIso,
            priority = selectedPriority.priorityString,
            visibility = "everyone",
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
            completionPolicy = CompletionPolicy.SINGLE_ASSIGNEE,
            scopeLevel = resolvedScopeLevel,
            zoneId = resolvedZoneId,
            stateId = resolvedStateId,
            cityId = resolvedCityId,
            centreId = resolvedCentreId,
            targetRoleCodes = selectedTargetRoles.toList()
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
                        text = "2. Scope Level (${authContext.activeRole.displayName})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableUiScopes) { scope ->
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
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("create_scope_${scope.name.lowercase()}")
                            )
                        }
                    }

                    // Contextual Field & Cascaded Organizational Selectors
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
                            if (availableCentres.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = orgCentreExpanded,
                                    onExpandedChange = { orgCentreExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedCentre?.let { "${it.code} · ${it.name}" } ?: "Select Examination Centre",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target Examination Centre") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = orgCentreExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("create_org_centre_dropdown"),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = orgCentreExpanded,
                                        onDismissRequest = { orgCentreExpanded = false }
                                    ) {
                                        availableCentres.forEach { centre ->
                                            DropdownMenuItem(
                                                text = { Text("${centre.code} · ${centre.name}") },
                                                onClick = {
                                                    selectedCentre = centre
                                                    orgCentreExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
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
                        }
                        TaskScopeLevel.CITY -> {
                            if (availableCities.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = cityExpanded,
                                    onExpandedChange = { cityExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedCity?.name ?: "Select Target City",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target City (Propagates to all Centres in City)") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("create_org_city_dropdown"),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = cityExpanded,
                                        onDismissRequest = { cityExpanded = false }
                                    ) {
                                        availableCities.forEach { city ->
                                            DropdownMenuItem(
                                                text = { Text("${city.name} (${city.code})") },
                                                onClick = {
                                                    selectedCity = city
                                                    cityName = city.name
                                                    cityExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
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
                        }
                        TaskScopeLevel.STATE -> {
                            if (availableStates.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = stateExpanded,
                                    onExpandedChange = { stateExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedState?.name ?: "Select Target State",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Target State / Region") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("create_org_state_dropdown"),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = stateExpanded,
                                        onDismissRequest = { stateExpanded = false }
                                    ) {
                                        availableStates.forEach { st ->
                                            DropdownMenuItem(
                                                text = { Text("${st.name} (${st.code})") },
                                                onClick = {
                                                    selectedState = st
                                                    stateName = st.name
                                                    stateExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
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
                        }
                        TaskScopeLevel.ZONE -> {
                            ExposedDropdownMenuBox(
                                expanded = zoneExpanded,
                                onExpandedChange = { zoneExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedZone?.name ?: "Select Target Zone",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Target Zone") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("create_org_zone_dropdown"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = zoneExpanded,
                                    onDismissRequest = { zoneExpanded = false }
                                ) {
                                    availableZones.forEach { z ->
                                        DropdownMenuItem(
                                            text = { Text("${z.name} (${z.code})") },
                                            onClick = {
                                                selectedZone = z
                                                zoneExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
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
                                        text = "Global Scope: Propagates across all zones and centres via single canonical task record.",
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
                        text = "Target Roles in Scope (Optional)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Creates ONE canonical task visible to all selected roles inside this scope — zero duplicate rows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val selectableTargetRoles = listOf(
                            TaskzRole.IT_MANAGER,
                            TaskzRole.VCO,
                            TaskzRole.CITY_HEAD,
                            TaskzRole.ROM,
                            TaskzRole.ZOM,
                            TaskzRole.COMMAND_CENTER
                        )
                        selectableTargetRoles.forEach { role ->
                            val isSelected = selectedTargetRoles.contains(role.code)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedTargetRoles.remove(role.code)
                                    else selectedTargetRoles.add(role.code)
                                },
                                label = { Text(role.displayName) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("target_role_chip_${role.code.lowercase()}")
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
                                text = "Eligible personnel in this scope can join the task directly",
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
                        text = "Invite Scoped Members (Name, Role, Emp ID, Email, or Mobile)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    OutlinedTextField(
                        value = memberSearchQuery,
                        onValueChange = {
                            memberSearchQuery = it
                            tasksViewModel.searchScopedMembers(
                                query = it,
                                roleCode = selectedTargetRoles.singleOrNull(),
                                centreId = null
                            )
                        },
                        label = { Text("Search Scoped Directory") },
                        placeholder = { Text("Name, Emp ID, email, or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_task_member_search"),
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

                    // Search Results List showing full scoped member identity even before typing
                    if (scopedSearchResults.isNotEmpty() || searchResults.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (scopedSearchResults.isNotEmpty()) {
                                    scopedSearchResults.take(10).forEach { member ->
                                        val asProfile = UserProfile(
                                            id = member.id,
                                            employeeId = member.employeeId,
                                            fullName = member.fullName,
                                            email = member.email,
                                            mobile = member.mobile.orEmpty(),
                                            activeRoleCode = member.activeRoleCode,
                                            assignedRoleCodes = member.assignedRoles.ifEmpty { listOf(member.activeRoleCode) },
                                            centreId = member.centreId,
                                            centreCode = member.centreCode,
                                            cityName = member.cityName,
                                            isActive = member.isActive
                                        )
                                        val isAdded = selectedInvitees.any { it.id == member.id || it.employeeId == member.employeeId }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (!isAdded) selectedInvitees.add(asProfile)
                                                    else selectedInvitees.removeAll { it.id == member.id || it.employeeId == member.employeeId }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = member.fullName.take(2).uppercase(),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "${member.fullName} • ${TaskzRole.fromCode(member.activeRoleCode).displayName}",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = listOfNotNull(
                                                            "ID: ${member.employeeId}",
                                                            member.email?.takeIf { it.isNotBlank() },
                                                            member.centreCode?.let { "Centre $it" } ?: member.cityName,
                                                            member.mobile?.takeIf { it.isNotBlank() }
                                                        ).joinToString(" • "),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isAdded) selectedInvitees.add(asProfile)
                                                    else selectedInvitees.removeAll { it.id == member.id || it.employeeId == member.employeeId }
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
                                } else {
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${candidate.fullName} • ${candidate.resolvedActiveRole.displayName}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "${candidate.employeeId} • ${candidate.mobile}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isAdded) selectedInvitees.add(candidate)
                                                    else selectedInvitees.removeAll { it.id == candidate.id || it.employeeId == candidate.employeeId }
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(if (isAdded) "Added ✓" else "+ Add", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No colleagues found in this city/scope yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
