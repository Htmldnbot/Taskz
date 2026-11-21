package com.example.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.TaskzApplication
import com.example.core.model.AssignedRoleInfo
import com.example.core.model.NotificationCategory
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.UpdateStatusState
import com.example.core.network.SupabaseConfig
import com.example.notifications.DeepLinkTarget
import com.example.notifications.NotificationDeepLinkRouter
import com.example.ui.admin.AdminScreen
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.WorkRolesViewModel
import com.example.ui.alarm.AlarmReliabilitySettingsScreen
import com.example.ui.common.NothingCreateActionSheet
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingFloatingNavCapsule
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingNavDestination
import com.example.ui.common.NothingQuickAction
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.groups.CreateGroupScreen
import com.example.ui.groups.EditGroupScreen
import com.example.ui.groups.GroupDetailScreen
import com.example.ui.groups.GroupJoinRequestsScreen
import com.example.ui.groups.GroupMembersScreen
import com.example.ui.groups.GroupsScreen
import com.example.ui.groups.GroupsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.incidents.SystemIncidentRegisterScreen
import com.example.ui.incidents.SystemIncidentViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel
import com.example.ui.settings.AppInfoScreen
import com.example.ui.settings.ContactAdminScreen
import com.example.ui.settings.DataAndPermissionsScreen
import com.example.ui.settings.DisclaimerScreen
import com.example.ui.settings.MandatoryUpdateGateScreen
import com.example.ui.settings.OpenSourceLicensesScreen
import com.example.ui.settings.OptionalUpdateBanner
import com.example.ui.settings.PrivacyPolicyScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SupportCategory
import com.example.ui.settings.TermsOfUseScreen
import com.example.ui.settings.UpdatesScreen
import com.example.ui.shifts.CopyShiftScreen
import com.example.ui.shifts.CreateShiftScreen
import com.example.ui.shifts.EditShiftScreen
import com.example.ui.shifts.ShiftDetailScreen
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.tasks.CreateTaskScreen
import com.example.ui.tasks.TaskDetailScreen
import com.example.ui.tasks.TaskJoinRequestsScreen
import com.example.ui.tasks.TaskParticipantsScreen
import com.example.ui.tasks.TasksScreen
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    TASKS("Tasks", Icons.Filled.Assignment, Icons.Outlined.Assignment, "nav_tab_tasks"),
    INCIDENTS("Incidents", Icons.Filled.DesktopWindows, Icons.Outlined.DesktopWindows, "nav_tab_incidents"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile"),
    ADMIN("Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_tab_admin")
}

sealed interface SubRoute {
    object None : SubRoute
    object GroupsList : SubRoute
    object CreateGroup : SubRoute
    data class GroupDetail(val group: TaskGroup) : SubRoute
    data class EditGroup(val group: TaskGroup) : SubRoute
    data class GroupMembers(val groupId: String) : SubRoute
    data class GroupJoinRequests(val groupId: String) : SubRoute

    data class CreateShift(val groupId: String) : SubRoute
    data class EditShift(val shift: Shift) : SubRoute
    data class ShiftDetail(val shift: Shift) : SubRoute
    data class CopyShift(val shift: Shift) : SubRoute

    data class CreateTask(val groupId: String? = null, val shiftId: String? = null) : SubRoute
    data class TaskDetail(val task: TaskItem) : SubRoute
    data class TaskParticipants(val taskId: String) : SubRoute
    data class TaskJoinRequests(val taskId: String) : SubRoute
    object AlarmReliabilitySettings : SubRoute

    object Settings : SubRoute
    object AppUpdates : SubRoute
    data class ContactAdmin(val category: SupportCategory = SupportCategory.GENERAL_SUPPORT) : SubRoute
    object PrivacyPolicy : SubRoute
    object Disclaimer : SubRoute
    object TermsOfUse : SubRoute
    object DataAndPermissions : SubRoute
    object OpenSourceLicenses : SubRoute
    object AppInfo : SubRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    homeViewModel: HomeViewModel,
    profileViewModel: ProfileViewModel,
    adminViewModel: AdminViewModel,
    tasksViewModel: TasksViewModel,
    groupsViewModel: GroupsViewModel,
    shiftsViewModel: ShiftsViewModel,
    workRolesViewModel: WorkRolesViewModel,
    systemIncidentViewModel: SystemIncidentViewModel,
    onLogoutSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val subRouteStack = remember { mutableStateListOf<SubRoute>() }
    val currentSubRoute = subRouteStack.lastOrNull() ?: SubRoute.None
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val app = TaskzApplication.instance
    val syncManager = app.taskzSyncManager
    val permissionManager = app.permissionManager

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                runCatching { app.deviceTokenManager.fetchAndRegisterFcmTokenIfAvailable() }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        runCatching { app.deviceTokenManager.fetchAndRegisterFcmTokenIfAvailable() }
        syncManager.syncNow(forceFull = false)
    }

    val profile by homeViewModel.profileFlow.collectAsStateWithLifecycle()
    val authContext by permissionManager.contextFlow.collectAsStateWithLifecycle()
    val syncMetadata by homeViewModel.syncMetadataFlow.collectAsStateWithLifecycle()
    val isSyncing by syncManager.isSyncing.collectAsStateWithLifecycle()
    val notifications by syncManager.notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val unreadCount by syncManager.unreadNotificationCount.collectAsStateWithLifecycle(initialValue = 0)
    val allShifts by tasksViewModel.allShiftsFlow.collectAsStateWithLifecycle()
    val allGroups by tasksViewModel.taskGroupsFlow.collectAsStateWithLifecycle()
    val allTasks by tasksViewModel.allTasksFlow.collectAsStateWithLifecycle()
    val allIncidents by systemIncidentViewModel.allIncidents.collectAsStateWithLifecycle()
    val isOnline by app.networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = app.networkMonitor.isCurrentlyConnected())
    val pendingDeepLink by NotificationDeepLinkRouter.pendingTarget.collectAsStateWithLifecycle()
    val appUpdateManager = app.appUpdateManager
    val userPrefs = app.userPreferences
    val updateStatus by appUpdateManager.updateStatus.collectAsStateWithLifecycle()
    val dismissedOptionalVersionCode by userPrefs.dismissedUpdateVersionCodeFlow.collectAsStateWithLifecycle(initialValue = 0)

    var showRoleMenu by remember { mutableStateOf(false) }
    var showAvailabilityMenu by remember { mutableStateOf(false) }
    var isUpdatingAvailability by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showCreateActionSheet by remember { mutableStateOf(false) }
    var pendingShift1Candidates by remember { mutableStateOf<List<Shift>?>(null) }

    LaunchedEffect(authContext.activeRole, authContext.assignedRoles) {
        if (currentTab == MainTab.ADMIN && !permissionManager.canManageUsers()) {
            currentTab = MainTab.HOME
            subRouteStack.clear()
        }
    }

    LaunchedEffect(pendingDeepLink, authContext.userId) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        NotificationDeepLinkRouter.consume()
        target.notificationId?.let { syncManager.markNotificationRead(it) }
        syncManager.syncNow(forceFull = false)

        when (target) {
            is DeepLinkTarget.TaskDetails -> {
                val task = app.taskRepository.getTaskById(target.taskId)
                if (task != null && permissionManager.canViewTask(task)) {
                    currentTab = MainTab.TASKS
                    subRouteStack.clear()
                    subRouteStack.add(SubRoute.TaskDetail(task))
                } else {
                    snackbarHostState.showSnackbar("Task unavailable or outside your current role scope.")
                }
            }
            is DeepLinkTarget.IncidentDetails -> {
                val incident = app.systemIncidentRepository.getIncidentById(target.incidentId)
                if (incident != null && permissionManager.canViewIncident(incident)) {
                    currentTab = MainTab.INCIDENTS
                    subRouteStack.clear()
                    systemIncidentViewModel.selectIncidentForDetail(incident)
                } else {
                    snackbarHostState.showSnackbar("Incident unavailable or outside your current role scope.")
                }
            }
            is DeepLinkTarget.ProfileRoles -> {
                currentTab = MainTab.PROFILE
                subRouteStack.clear()
            }
            is DeepLinkTarget.AdminControls -> {
                if (permissionManager.canManageUsers()) {
                    currentTab = MainTab.ADMIN
                    subRouteStack.clear()
                } else {
                    snackbarHostState.showSnackbar("Administrator access required.")
                }
            }
        }
    }

    BackHandler(enabled = subRouteStack.isNotEmpty() || currentTab != MainTab.HOME) {
        if (subRouteStack.isNotEmpty()) {
            subRouteStack.removeAt(subRouteStack.lastIndex)
        } else if (currentTab != MainTab.HOME) {
            currentTab = MainTab.HOME
        }
    }

    val canManageUsers = permissionManager.canManageUsers()
    val canCreateTask = permissionManager.canCreateTask()
    val canSelfJoinGroups = permissionManager.canSelfJoinGroups()

    val openRepeatShift1To2: () -> Unit = {
        val candidates = tasksViewModel.findShift1Candidates(allShifts)
        when {
            candidates.isEmpty() -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Create an Exam Group & Shift 1 first")
                }
            }
            candidates.size == 1 -> {
                subRouteStack.add(SubRoute.CopyShift(candidates.first()))
            }
            else -> {
                pendingShift1Candidates = candidates
            }
        }
    }

    val leftNavItems = remember {
        listOf(
            NothingNavDestination(
                id = MainTab.HOME.name,
                label = MainTab.HOME.title,
                selectedIcon = MainTab.HOME.selectedIcon,
                unselectedIcon = MainTab.HOME.unselectedIcon,
                testTag = MainTab.HOME.testTag
            ),
            NothingNavDestination(
                id = MainTab.TASKS.name,
                label = MainTab.TASKS.title,
                selectedIcon = MainTab.TASKS.selectedIcon,
                unselectedIcon = MainTab.TASKS.unselectedIcon,
                testTag = MainTab.TASKS.testTag
            )
        )
    }

    val rightNavItems = remember {
        listOf(
            NothingNavDestination(
                id = MainTab.INCIDENTS.name,
                label = MainTab.INCIDENTS.title,
                selectedIcon = MainTab.INCIDENTS.selectedIcon,
                unselectedIcon = MainTab.INCIDENTS.unselectedIcon,
                testTag = MainTab.INCIDENTS.testTag
            ),
            NothingNavDestination(
                id = MainTab.PROFILE.name,
                label = MainTab.PROFILE.title,
                selectedIcon = MainTab.PROFILE.selectedIcon,
                unselectedIcon = MainTab.PROFILE.unselectedIcon,
                testTag = MainTab.PROFILE.testTag
            )
        )
    }

    val quickActions = remember(canCreateTask, canSelfJoinGroups, canManageUsers, allShifts) {
        buildList {
            if (canCreateTask) {
                add(
                    NothingQuickAction(
                        title = "Create Task",
                        subtitle = "Schedule centre, shift or progressive task",
                        icon = Icons.Default.AddTask,
                        testTag = "quick_action_create_task",
                        onClick = { subRouteStack.add(SubRoute.CreateTask()) }
                    )
                )
            }
            add(
                NothingQuickAction(
                    title = "Report System Incident",
                    subtitle = "Fast lock / LAN failure entry",
                    icon = Icons.Default.ReportProblem,
                    testTag = "quick_action_create_incident",
                    onClick = {
                        currentTab = MainTab.INCIDENTS
                        subRouteStack.clear()
                        systemIncidentViewModel.selectTab(0)
                    }
                )
            )
            if (canSelfJoinGroups) {
                add(
                    NothingQuickAction(
                        title = "Exam Groups & Shifts",
                        subtitle = "1-click group join or shift management",
                        icon = Icons.Default.Groups,
                        testTag = "quick_action_groups",
                        onClick = { subRouteStack.add(SubRoute.GroupsList) }
                    )
                )
            }
            if (canCreateTask || permissionManager.canManageGroups()) {
                add(
                    NothingQuickAction(
                        title = "Repeat Shift 1 → Shift 2",
                        subtitle = "Copy selected tasks with relative timing",
                        icon = Icons.Default.ContentCopy,
                        testTag = "quick_action_repeat_shift",
                        onClick = openRepeatShift1To2
                    )
                )
            }
            if (canManageUsers) {
                add(
                    NothingQuickAction(
                        title = "Admin Console",
                        subtitle = "Users, work roles & reset requests",
                        icon = Icons.Default.AdminPanelSettings,
                        testTag = "quick_action_admin",
                        onClick = {
                            currentTab = MainTab.ADMIN
                            subRouteStack.clear()
                        }
                    )
                )
            }
        }
    }

    val mandatoryGateRelease = if (isOnline && currentSubRoute !is SubRoute.ContactAdmin) {
        (updateStatus as? UpdateStatusState.MandatoryRequired)?.release
    } else null

    if (mandatoryGateRelease != null) {
        MandatoryUpdateGateScreen(
            release = mandatoryGateRelease,
            appUpdateManager = appUpdateManager,
            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.APP_UPDATE)) }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = NothingTheme.colors.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(
                        bottom = if (currentSubRoute == SubRoute.None) 84.dp else 8.dp
                    )
                )
            },
            topBar = {
                if (currentSubRoute == SubRoute.None) {
                    val initials = remember(profile?.fullName) {
                        val parts = profile?.fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                        when {
                            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                            parts.size == 1 -> parts.first().take(2).uppercase()
                            else -> "TZ"
                        }
                    }
                    TopAppBar(
                        title = {
                            if (currentTab == MainTab.HOME) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    NothingDotMatrixText(
                                        text = "Taskz",
                                        fontSize = 22.sp,
                                        letterSpacing = 1.6.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Operations. Simplified.",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = NothingTheme.colors.textSecondary,
                                            maxLines = 1
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height(1.dp)
                                                .background(NothingTheme.colors.border)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.taskz_launcher_icon_1791997779230),
                                        contentDescription = "Taskz",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    NothingDotMatrixText(
                                        text = "Taskz",
                                        fontSize = 18.sp,
                                        letterSpacing = 1.4.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "/ ${currentTab.title.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = TechMonoFontFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = NothingTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            // Clear 1-tap Available / Away pill right at the top
                            val isAway = authContext.isOnLeave
                            val statusColor = if (isAway) NothingAmber else NothingGreen
                            Box {
                                Surface(
                                    onClick = { showAvailabilityMenu = true },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isAway) NothingAmber.copy(alpha = 0.12f) else NothingTheme.colors.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isAway) NothingAmber.copy(alpha = 0.48f) else NothingTheme.colors.border
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("topbar_availability_toggle")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                    ) {
                                        NothingStatusDot(color = statusColor, size = 8.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAway) "Away" else "Available",
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            ),
                                            color = if (isAway) NothingAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Change availability status",
                                            tint = if (isAway) NothingAmber else NothingTheme.colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showAvailabilityMenu,
                                    onDismissRequest = { showAvailabilityMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        enabled = !isUpdatingAvailability,
                                        leadingIcon = { NothingStatusDot(color = NothingGreen, size = 9.dp) },
                                        trailingIcon = if (!isAway) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Available",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                                )
                                                Text(
                                                    text = "Active · Receive tasks & alarms",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = NothingTheme.colors.textSecondary
                                                )
                                            }
                                        },
                                        onClick = {
                                            showAvailabilityMenu = false
                                            if (isAway && !isUpdatingAvailability) {
                                                coroutineScope.launch {
                                                    isUpdatingAvailability = true
                                                    syncManager.updateLeaveStatus(false).onFailure { err ->
                                                        snackbarHostState.showSnackbar(err.message ?: "Unable to set Available")
                                                    }
                                                    isUpdatingAvailability = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("availability_option_available")
                                    )
                                    DropdownMenuItem(
                                        enabled = !isUpdatingAvailability,
                                        leadingIcon = { NothingStatusDot(color = NothingAmber, size = 9.dp) },
                                        trailingIcon = if (isAway) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Away",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = NothingAmber
                                                    )
                                                )
                                                Text(
                                                    text = "Paused · No new tasks or alarms",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = NothingTheme.colors.textSecondary
                                                )
                                            }
                                        },
                                        onClick = {
                                            showAvailabilityMenu = false
                                            if (!isAway && !isUpdatingAvailability) {
                                                coroutineScope.launch {
                                                    isUpdatingAvailability = true
                                                    syncManager.updateLeaveStatus(true).onFailure { err ->
                                                        snackbarHostState.showSnackbar(err.message ?: "Unable to set Away")
                                                    }
                                                    isUpdatingAvailability = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("availability_option_away")
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showNotificationsSheet = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("topbar_notifications_bell")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) {
                                            Badge(
                                                containerColor = NothingRed,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = TechMonoFontFamily,
                                                        fontSize = 9.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Box(modifier = Modifier.padding(end = 6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(NothingTheme.colors.surface)
                                        .border(1.dp, NothingTheme.colors.border, CircleShape)
                                        .clickable { showRoleMenu = true }
                                        .testTag("topbar_role_switcher"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val photoUrl = profile?.avatarSignedUrl
                                    if (!photoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Account and Role Menu",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = TechMonoFontFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (!isOnline || syncMetadata.pendingSyncCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(NothingAmber)
                                                .border(1.dp, NothingTheme.colors.background, CircleShape)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showRoleMenu,
                                    onDismissRequest = { showRoleMenu = false }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = profile?.fullName?.ifBlank { "Account" } ?: "Account",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${authContext.activeRole.displayName} · " + when {
                                                !isOnline -> "No network connection"
                                                isSyncing -> "Syncing..."
                                                syncMetadata.pendingSyncCount > 0 -> "${syncMetadata.pendingSyncCount} pending sync"
                                                else -> "All data synced"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                            color = NothingTheme.colors.textSecondary
                                        )
                                    }
                                    HorizontalDivider(color = NothingTheme.colors.borderSubtle)

                                    val assigned = authContext.assignedRoles.ifEmpty {
                                        listOf(
                                            AssignedRoleInfo(
                                                roleId = "",
                                                roleCode = authContext.activeRole.code,
                                                roleName = authContext.activeRole.displayName,
                                                isPrimary = true
                                            )
                                        )
                                    }
                                    assigned.forEach { role ->
                                        val isCurrent = role.roleCode.equals(authContext.activeRole.code, ignoreCase = true)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "Role: ${role.roleName}",
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = if (isCurrent) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            onClick = {
                                                showRoleMenu = false
                                                if (!isCurrent) {
                                                    coroutineScope.launch {
                                                        val res = syncManager.switchActiveRole(role.roleCode)
                                                        res.onFailure { err ->
                                                            snackbarHostState.showSnackbar(err.message ?: "Unable to switch role")
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("role_option_${role.roleCode}")
                                        )
                                    }

                                    HorizontalDivider(color = NothingTheme.colors.borderSubtle)

                                    DropdownMenuItem(
                                        text = { Text("Sync Now") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            homeViewModel.triggerManualRefresh()
                                        }
                                    )

                                    if (canManageUsers) {
                                        DropdownMenuItem(
                                            text = { Text("Admin Console") },
                                            leadingIcon = {
                                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            onClick = {
                                                showRoleMenu = false
                                                currentTab = MainTab.ADMIN
                                                subRouteStack.clear()
                                            },
                                            modifier = Modifier.testTag("nav_tab_admin")
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("Profile") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            currentTab = MainTab.PROFILE
                                            subRouteStack.clear()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            subRouteStack.add(SubRoute.Settings)
                                        },
                                        modifier = Modifier.testTag("menu_open_settings")
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            val hasUpdate = updateStatus is UpdateStatusState.OptionalAvailable ||
                                                updateStatus is UpdateStatusState.MandatoryRequired
                                            Text(if (hasUpdate) "App Updates (Available)" else "Check for Updates")
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            showRoleMenu = false
                                            subRouteStack.add(SubRoute.AppUpdates)
                                        },
                                        modifier = Modifier.testTag("menu_open_updates")
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = NothingTheme.colors.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val route = currentSubRoute) {
                    is SubRoute.GroupsList -> {
                        GroupsScreen(
                            viewModel = groupsViewModel,
                            onCreateGroupClick = { subRouteStack.add(SubRoute.CreateGroup) },
                            onGroupClick = { group -> subRouteStack.add(SubRoute.GroupDetail(group)) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.CreateGroup -> {
                        CreateGroupScreen(
                            viewModel = groupsViewModel,
                            onSuccess = { createdGroup ->
                                subRouteStack.removeAt(subRouteStack.lastIndex)
                                subRouteStack.add(SubRoute.GroupDetail(createdGroup))
                            },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.EditGroup -> {
                        EditGroupScreen(
                            group = route.group,
                            viewModel = groupsViewModel,
                            onSuccess = { subRouteStack.removeAt(subRouteStack.lastIndex) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.GroupDetail -> {
                        GroupDetailScreen(
                            group = route.group,
                            groupsViewModel = groupsViewModel,
                            shiftsViewModel = shiftsViewModel,
                            tasksViewModel = tasksViewModel,
                            onEditGroupClick = { grp -> subRouteStack.add(SubRoute.EditGroup(grp)) },
                            onAddShiftClick = { gid -> subRouteStack.add(SubRoute.CreateShift(gid)) },
                            onViewMembersClick = { gid -> subRouteStack.add(SubRoute.GroupMembers(gid)) },
                            onViewJoinRequestsClick = { gid -> subRouteStack.add(SubRoute.GroupJoinRequests(gid)) },
                            onShiftClick = { shf -> subRouteStack.add(SubRoute.ShiftDetail(shf)) },
                            onTaskClick = { tsk -> subRouteStack.add(SubRoute.TaskDetail(tsk)) },
                            onCreateTaskClick = { gid -> subRouteStack.add(SubRoute.CreateTask(groupId = gid)) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.GroupMembers -> {
                        GroupMembersScreen(
                            groupId = route.groupId,
                            viewModel = groupsViewModel,
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.GroupJoinRequests -> {
                        GroupJoinRequestsScreen(
                            groupId = route.groupId,
                            viewModel = groupsViewModel,
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.CreateShift -> {
                        CreateShiftScreen(
                            groupId = route.groupId,
                            viewModel = shiftsViewModel,
                            onSuccess = { createdShift ->
                                subRouteStack.removeAt(subRouteStack.lastIndex)
                                subRouteStack.add(SubRoute.ShiftDetail(createdShift))
                            },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.EditShift -> {
                        EditShiftScreen(
                            shift = route.shift,
                            viewModel = shiftsViewModel,
                            onSuccess = { subRouteStack.removeAt(subRouteStack.lastIndex) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.ShiftDetail -> {
                        ShiftDetailScreen(
                            shift = route.shift,
                            shiftsViewModel = shiftsViewModel,
                            tasksViewModel = tasksViewModel,
                            onEditShiftClick = { shf -> subRouteStack.add(SubRoute.EditShift(shf)) },
                            onCopyShiftClick = { shf -> subRouteStack.add(SubRoute.CopyShift(shf)) },
                            onAddTaskClick = { gid, sid -> subRouteStack.add(SubRoute.CreateTask(groupId = gid, shiftId = sid)) },
                            onTaskClick = { tsk -> subRouteStack.add(SubRoute.TaskDetail(tsk)) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.CopyShift -> {
                        CopyShiftScreen(
                            sourceShift = route.shift,
                            shiftsViewModel = shiftsViewModel,
                            tasksViewModel = tasksViewModel,
                            onSuccess = { copiedShift ->
                                subRouteStack.removeAt(subRouteStack.lastIndex)
                                subRouteStack.add(SubRoute.ShiftDetail(copiedShift))
                            },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.CreateTask -> {
                        CreateTaskScreen(
                            preselectedGroupId = route.groupId,
                            preselectedShiftId = route.shiftId,
                            tasksViewModel = tasksViewModel,
                            groupsViewModel = groupsViewModel,
                            shiftsViewModel = shiftsViewModel,
                            onSuccess = { createdTask ->
                                subRouteStack.removeAt(subRouteStack.lastIndex)
                                subRouteStack.add(SubRoute.TaskDetail(createdTask))
                            },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.TaskDetail -> {
                        TaskDetailScreen(
                            task = route.task,
                            viewModel = tasksViewModel,
                            onViewParticipantsClick = { tid -> subRouteStack.add(SubRoute.TaskParticipants(tid)) },
                            onViewJoinRequestsClick = { tid -> subRouteStack.add(SubRoute.TaskJoinRequests(tid)) },
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.TaskParticipants -> {
                        TaskParticipantsScreen(
                            taskId = route.taskId,
                            viewModel = tasksViewModel,
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.TaskJoinRequests -> {
                        TaskJoinRequestsScreen(
                            taskId = route.taskId,
                            viewModel = tasksViewModel,
                            onBackClick = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.AlarmReliabilitySettings -> {
                        AlarmReliabilitySettingsScreen(
                            onNavigateBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.Settings -> {
                        SettingsScreen(
                            profile = profile,
                            authContext = authContext,
                            userPrefs = userPrefs,
                            appUpdateManager = appUpdateManager,
                            onOpenProfile = {
                                subRouteStack.clear()
                                currentTab = MainTab.PROFILE
                            },
                            onOpenAlarmReliability = { subRouteStack.add(SubRoute.AlarmReliabilitySettings) },
                            onOpenUpdates = { subRouteStack.add(SubRoute.AppUpdates) },
                            onOpenContactAdmin = { cat -> subRouteStack.add(SubRoute.ContactAdmin(cat)) },
                            onOpenPrivacyPolicy = { subRouteStack.add(SubRoute.PrivacyPolicy) },
                            onOpenDisclaimer = { subRouteStack.add(SubRoute.Disclaimer) },
                            onOpenTermsOfUse = { subRouteStack.add(SubRoute.TermsOfUse) },
                            onOpenDataAndPermissions = { subRouteStack.add(SubRoute.DataAndPermissions) },
                            onOpenLicenses = { subRouteStack.add(SubRoute.OpenSourceLicenses) },
                            onOpenAppInfo = { subRouteStack.add(SubRoute.AppInfo) },
                            onSignOut = {
                                subRouteStack.clear()
                                profileViewModel.logout(onLogoutSuccess)
                            },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.AppUpdates -> {
                        UpdatesScreen(
                            appUpdateManager = appUpdateManager,
                            isOnline = isOnline,
                            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.APP_UPDATE)) },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.ContactAdmin -> {
                        ContactAdminScreen(
                            initialCategory = route.category,
                            employeeId = profile?.employeeId,
                            userEmail = SupabaseConfig.sanitizeDisplayEmail(profile?.email),
                            roleName = authContext.activeRole.displayName,
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.PrivacyPolicy -> {
                        PrivacyPolicyScreen(
                            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.GENERAL_SUPPORT)) },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.Disclaimer -> {
                        DisclaimerScreen(
                            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.GENERAL_SUPPORT)) },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.TermsOfUse -> {
                        TermsOfUseScreen(
                            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.GENERAL_SUPPORT)) },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.DataAndPermissions -> {
                        DataAndPermissionsScreen(
                            isOnline = isOnline,
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    is SubRoute.OpenSourceLicenses -> {
                        OpenSourceLicensesScreen(onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) })
                    }
                    is SubRoute.AppInfo -> {
                        AppInfoScreen(
                            onOpenUpdates = { subRouteStack.add(SubRoute.AppUpdates) },
                            onContactAdmin = { subRouteStack.add(SubRoute.ContactAdmin(SupportCategory.GENERAL_SUPPORT)) },
                            onBack = { subRouteStack.removeAt(subRouteStack.lastIndex) }
                        )
                    }
                    SubRoute.None -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val optionalRelease = (updateStatus as? UpdateStatusState.OptionalAvailable)?.release
                            if (optionalRelease != null && optionalRelease.versionCode != dismissedOptionalVersionCode) {
                                OptionalUpdateBanner(
                                    release = optionalRelease,
                                    onOpenUpdates = { subRouteStack.add(SubRoute.AppUpdates) },
                                    onDismiss = { appUpdateManager.dismissOptionalPrompt(optionalRelease.versionCode) },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(160)) togetherWith fadeOut(animationSpec = tween(120))
                                },
                                label = "main_tab_transition",
                                modifier = Modifier.weight(1f)
                            ) { activeTab ->
                                when (activeTab) {
                                    MainTab.HOME -> HomeScreen(
                                        viewModel = homeViewModel,
                                        onNavigateToTasks = { currentTab = MainTab.TASKS },
                                        onNavigateToGroups = { subRouteStack.add(SubRoute.GroupsList) },
                                        onNavigateToProfile = { currentTab = MainTab.PROFILE },
                                        onNavigateToAdmin = { currentTab = MainTab.ADMIN },
                                        onNavigateToIncidents = {
                                            systemIncidentViewModel.selectTab(1)
                                            currentTab = MainTab.INCIDENTS
                                        },
                                        onNavigateToReports = {
                                            systemIncidentViewModel.selectTab(2)
                                            currentTab = MainTab.INCIDENTS
                                        },
                                        onNavigateToAlarmSettings = { subRouteStack.add(SubRoute.AlarmReliabilitySettings) },
                                        onSignedOut = onLogoutSuccess,
                                        tasks = allTasks,
                                        incidents = allIncidents,
                                        notifications = notifications,
                                        onTaskClick = { task -> subRouteStack.add(SubRoute.TaskDetail(task)) },
                                        onIncidentClick = { incident ->
                                            systemIncidentViewModel.selectTab(1)
                                            systemIncidentViewModel.selectIncidentForDetail(incident)
                                            currentTab = MainTab.INCIDENTS
                                        },
                                        onOpenNotifications = { showNotificationsSheet = true }
                                    )
                                    MainTab.TASKS -> TasksScreen(
                                        viewModel = tasksViewModel,
                                        onCreateTaskClick = { subRouteStack.add(SubRoute.CreateTask()) },
                                        onTaskClick = { task -> subRouteStack.add(SubRoute.TaskDetail(task)) },
                                        onManageGroupsClick = { subRouteStack.add(SubRoute.GroupsList) },
                                        onRepeatShiftClick = openRepeatShift1To2
                                    )
                                    MainTab.INCIDENTS -> SystemIncidentRegisterScreen(
                                        viewModel = systemIncidentViewModel
                                    )
                                    MainTab.PROFILE -> ProfileScreen(
                                        viewModel = profileViewModel,
                                        onLogoutSuccess = onLogoutSuccess,
                                        onNavigateToSettings = { subRouteStack.add(SubRoute.Settings) },
                                        onNavigateToAlarmSettings = { subRouteStack.add(SubRoute.AlarmReliabilitySettings) },
                                        onNavigateToGroups = { subRouteStack.add(SubRoute.GroupsList) },
                                        onNavigateToAdmin = { currentTab = MainTab.ADMIN },
                                        canManageUsers = canManageUsers,
                                        canSelfJoinGroups = canSelfJoinGroups
                                    )
                                    MainTab.ADMIN -> AdminScreen(
                                        viewModel = adminViewModel,
                                        workRolesViewModel = workRolesViewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (currentSubRoute == SubRoute.None) {
            NothingFloatingNavCapsule(
                leftItems = leftNavItems,
                rightItems = rightNavItems,
                selectedId = currentTab.name,
                isActionSheetOpen = showCreateActionSheet,
                onSelect = { dest ->
                    currentTab = MainTab.valueOf(dest.id)
                    subRouteStack.clear()
                },
                onCreateClick = { showCreateActionSheet = !showCreateActionSheet },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showCreateActionSheet) {
            NothingCreateActionSheet(
                actions = quickActions,
                onDismiss = { showCreateActionSheet = false }
            )
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                containerColor = NothingTheme.colors.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("notifications_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            NothingDotMatrixText(
                                text = "NOTIFICATIONS",
                                fontSize = 18.sp,
                                letterSpacing = 1.4.sp
                            )
                            Text(
                                text = "$unreadCount unread · ${authContext.activeRole.displayName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = NothingTheme.colors.textSecondary
                            )
                        }
                        if (unreadCount > 0) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch { syncManager.markAllNotificationsRead() }
                                },
                                modifier = Modifier.testTag("mark_all_notifications_read")
                            ) {
                                Text(
                                    text = "MARK ALL READ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    if (notifications.isEmpty()) {
                        NothingEmptyState(
                            title = "ALL CAUGHT UP",
                            subtitle = "Task alerts, escalations and access updates appear here.",
                            icon = Icons.Outlined.Notifications
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifications, key = { it.id }) { item ->
                                val category = NotificationCategory.fromRaw(item.category, item.type)
                                NothingHairlineCard(
                                    onClick = {
                                        coroutineScope.launch {
                                            syncManager.markNotificationRead(item.id)
                                        }
                                        showNotificationsSheet = false
                                        val target = NotificationDeepLinkRouter.parse(
                                            deepLink = item.deepLink,
                                            taskId = item.taskId,
                                            incidentId = item.incidentId,
                                            category = item.category,
                                            notificationId = item.id
                                        )
                                        if (target != null) {
                                            NotificationDeepLinkRouter.route(target)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    containerColor = if (item.isRead) {
                                        NothingTheme.colors.surface
                                    } else {
                                        NothingTheme.colors.surfaceElevated
                                    },
                                    borderColor = if (!item.isRead && category.urgent) {
                                        NothingRed.copy(alpha = 0.55f)
                                    } else {
                                        NothingTheme.colors.border
                                    },
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier.testTag("notification_item_${item.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!item.isRead) {
                                            NothingStatusDot(
                                                color = if (category.urgent) NothingRed else MaterialTheme.colorScheme.onSurface,
                                                size = 7.dp,
                                                modifier = Modifier.padding(top = 5.dp)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                NothingTagChip(
                                                    text = category.displayName.uppercase(),
                                                    containerColor = if (category.urgent) NothingRed.copy(alpha = 0.14f) else NothingTheme.colors.surface,
                                                    contentColor = if (category.urgent) NothingRed else MaterialTheme.colorScheme.onSurface,
                                                    borderColor = if (category.urgent) NothingRed.copy(alpha = 0.4f) else NothingTheme.colors.border
                                                )
                                                Text(
                                                    text = item.createdAt.take(16).replace("T", " "),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                                    color = NothingTheme.colors.textSecondary
                                                )
                                            }
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!item.body.isNullOrBlank()) {
                                                Text(
                                                    text = item.body,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NothingTheme.colors.textSecondary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        if (!pendingShift1Candidates.isNullOrEmpty()) {
            val candidates = pendingShift1Candidates.orEmpty()
            AlertDialog(
                onDismissRequest = { pendingShift1Candidates = null },
                containerColor = NothingTheme.colors.surface,
                title = {
                    NothingDotMatrixText(text = "SELECT SHIFT 1", fontSize = 16.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Choose which exam's Shift 1 to copy:",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                        candidates.forEach { shiftCandidate ->
                            val group = allGroups.firstOrNull { it.id == shiftCandidate.groupId }
                            val groupLabel = listOfNotNull(
                                group?.title,
                                group?.code?.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            val timeLabel = TaskItem.formatForDisplay(shiftCandidate.startsAt) ?: shiftCandidate.startsAt
                            NothingHairlineCard(
                                onClick = {
                                    pendingShift1Candidates = null
                                    subRouteStack.add(SubRoute.CopyShift(shiftCandidate))
                                },
                                shape = RoundedCornerShape(10.dp),
                                containerColor = NothingTheme.colors.surfaceElevated,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier.testTag("pick_shift1_candidate_${shiftCandidate.id}")
                            ) {
                                Text(
                                    text = if (groupLabel.isNotBlank()) "$groupLabel · ${shiftCandidate.name}" else shiftCandidate.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$timeLabel (${shiftCandidate.taskCount} tasks)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = NothingTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { pendingShift1Candidates = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
