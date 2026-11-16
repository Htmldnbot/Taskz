package com.example.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.TaskzApplication
import com.example.core.model.AppNotification
import com.example.core.model.NotificationCategory
import com.example.core.model.Shift
import com.example.core.model.SyncStatus
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.notifications.DeepLinkTarget
import com.example.notifications.NotificationDeepLinkRouter
import com.example.ui.admin.AdminScreen
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.WorkRolesViewModel
import com.example.ui.alarm.AlarmReliabilitySettingsScreen
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
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    TASKS("Tasks", Icons.Filled.Assignment, Icons.Outlined.Assignment, "nav_tab_tasks"),
    INCIDENTS("Incident Register", Icons.Filled.DesktopWindows, Icons.Outlined.DesktopWindows, "nav_tab_incidents"),
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

    val authContext by permissionManager.contextFlow.collectAsStateWithLifecycle()
    val syncMetadata by homeViewModel.syncMetadataFlow.collectAsStateWithLifecycle()
    val isSyncing by syncManager.isSyncing.collectAsStateWithLifecycle()
    val notifications by syncManager.notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val unreadCount by syncManager.unreadNotificationCount.collectAsStateWithLifecycle(initialValue = 0)
    val isOnline by app.networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = app.networkMonitor.isCurrentlyConnected())
    val pendingDeepLink by NotificationDeepLinkRouter.pendingTarget.collectAsStateWithLifecycle()

    var showRoleMenu by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    // If active role switches away from SUPER_ADMIN while on Admin tab, redirect to HOME cleanly
    LaunchedEffect(authContext.activeRole, authContext.assignedRoles) {
        if (currentTab == MainTab.ADMIN && !permissionManager.canManageUsers()) {
            currentTab = MainTab.HOME
            subRouteStack.clear()
        }
        if (currentSubRoute is SubRoute.GroupsList && !permissionManager.canManageGroups()) {
            subRouteStack.clear()
        }
    }

    // Server-verified deep link routing from push/system notifications & notification sheet
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

    // BackHandler: pops subroute first, otherwise navigates to HOME
    BackHandler(enabled = subRouteStack.isNotEmpty() || currentTab != MainTab.HOME) {
        if (subRouteStack.isNotEmpty()) {
            subRouteStack.removeAt(subRouteStack.lastIndex)
        } else if (currentTab != MainTab.HOME) {
            currentTab = MainTab.HOME
        }
    }

    val canManageUsers = permissionManager.canManageUsers()
    val visibleTabs = remember(canManageUsers) {
        if (canManageUsers) {
            listOf(MainTab.HOME, MainTab.TASKS, MainTab.INCIDENTS, MainTab.PROFILE, MainTab.ADMIN)
        } else {
            listOf(MainTab.HOME, MainTab.TASKS, MainTab.INCIDENTS, MainTab.PROFILE)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentSubRoute == SubRoute.None) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.taskz_launcher_icon_1791997779230),
                                contentDescription = "Taskz",
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentTab) {
                                    MainTab.HOME -> "Taskz"
                                    MainTab.INCIDENTS -> "Incidents"
                                    else -> currentTab.title
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Active Role Selector (shows ONLY server-assigned roles)
                        Box {
                            AssistChip(
                                onClick = { showRoleMenu = true },
                                label = {
                                    Text(
                                        text = authContext.activeRole.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch Active Role",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("topbar_role_switcher")
                            )
                            DropdownMenu(
                                expanded = showRoleMenu,
                                onDismissRequest = { showRoleMenu = false }
                            ) {
                                val assigned = authContext.assignedRoles.ifEmpty {
                                    listOf(
                                        com.example.core.model.AssignedRoleInfo(
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
                                                text = role.roleName,
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
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Unobtrusive Sync Status Pill (no manual sync needed)
                        val syncBadgeColor = when {
                            !isOnline -> Color.Gray
                            isSyncing || syncMetadata.status == SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                            syncMetadata.pendingSyncCount > 0 || syncMetadata.status == SyncStatus.PENDING -> TaskzWarning
                            else -> TaskzSuccess
                        }
                        val syncIcon = when {
                            !isOnline -> Icons.Default.CloudOff
                            isSyncing || syncMetadata.status == SyncStatus.SYNCING -> Icons.Default.CloudSync
                            else -> Icons.Default.CloudDone
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = syncBadgeColor.copy(alpha = 0.14f),
                            modifier = Modifier.testTag("topbar_sync_status")
                        ) {
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = when {
                                    !isOnline -> "Offline"
                                    isSyncing -> "Syncing"
                                    syncMetadata.pendingSyncCount > 0 -> "Pending Sync"
                                    else -> "Synced"
                                },
                                tint = syncBadgeColor,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }

                        // Notifications Bell with unread count badge
                        IconButton(
                            onClick = { showNotificationsSheet = true },
                            modifier = Modifier.testTag("topbar_notifications_bell")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (unreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = "Notifications"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (currentSubRoute == SubRoute.None) {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    visibleTabs.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = tab
                                subRouteStack.clear()
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
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
                SubRoute.None -> {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToTasks = { currentTab = MainTab.TASKS },
                            onNavigateToGroups = { subRouteStack.add(SubRoute.GroupsList) },
                            onNavigateToProfile = { currentTab = MainTab.PROFILE },
                            onNavigateToAdmin = { currentTab = MainTab.ADMIN },
                            onNavigateToIncidents = { currentTab = MainTab.INCIDENTS },
                            onNavigateToAlarmSettings = { subRouteStack.add(SubRoute.AlarmReliabilitySettings) },
                            onSignedOut = onLogoutSuccess
                        )
                        MainTab.TASKS -> TasksScreen(
                            viewModel = tasksViewModel,
                            onCreateTaskClick = { subRouteStack.add(SubRoute.CreateTask()) },
                            onTaskClick = { task -> subRouteStack.add(SubRoute.TaskDetail(task)) },
                            onManageGroupsClick = { subRouteStack.add(SubRoute.GroupsList) }
                        )
                        MainTab.INCIDENTS -> SystemIncidentRegisterScreen(
                            viewModel = systemIncidentViewModel
                        )
                        MainTab.PROFILE -> ProfileScreen(
                            viewModel = profileViewModel,
                            onLogoutSuccess = onLogoutSuccess
                        )
                        MainTab.ADMIN -> AdminScreen(
                            viewModel = adminViewModel,
                            workRolesViewModel = workRolesViewModel
                        )
                    }
                }
            }
        }

        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                modifier = Modifier.testTag("notifications_bottom_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Notifications",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${unreadCount} unread in ${authContext.activeRole.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (unreadCount > 0) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch { syncManager.markAllNotificationsRead() }
                                },
                                modifier = Modifier.testTag("mark_all_notifications_read")
                            ) {
                                Text("Mark all read")
                            }
                        }
                    }

                    if (notifications.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "You're all caught up",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Task assignments, overdue alerts, incident escalations, and access updates appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifications, key = { it.id }) { item ->
                                val category = NotificationCategory.fromRaw(item.category, item.type)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
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
                                        }
                                        .testTag("notification_item_${item.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (item.isRead) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!item.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (category.urgent) MaterialTheme.colorScheme.error
                                                        else MaterialTheme.colorScheme.primary
                                                    )
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
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (category.urgent) {
                                                        MaterialTheme.colorScheme.errorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    }
                                                ) {
                                                    Text(
                                                        text = category.displayName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (category.urgent) {
                                                            MaterialTheme.colorScheme.onErrorContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSecondaryContainer
                                                        },
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = item.createdAt.take(16).replace("T", " "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold
                                            )
                                            if (!item.body.isNullOrBlank()) {
                                                Text(
                                                    text = item.body,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

