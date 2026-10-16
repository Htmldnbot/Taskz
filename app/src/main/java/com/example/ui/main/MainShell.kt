package com.example.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.ui.admin.AdminScreen
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.WorkRolesViewModel
import com.example.ui.groups.CreateGroupScreen
import com.example.ui.groups.EditGroupScreen
import com.example.ui.groups.GroupDetailScreen
import com.example.ui.groups.GroupJoinRequestsScreen
import com.example.ui.groups.GroupMembersScreen
import com.example.ui.groups.GroupsScreen
import com.example.ui.groups.GroupsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
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

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    TASKS("Tasks", Icons.Filled.Assignment, Icons.Outlined.Assignment, "nav_tab_tasks"),
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
    onLogoutSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val subRouteStack = remember { mutableStateListOf<SubRoute>() }
    val currentSubRoute = subRouteStack.lastOrNull() ?: SubRoute.None

    val profile by homeViewModel.profileFlow.collectAsStateWithLifecycle()

    // BackHandler: pops subroute first, otherwise navigates to HOME
    BackHandler(enabled = subRouteStack.isNotEmpty() || currentTab != MainTab.HOME) {
        if (subRouteStack.isNotEmpty()) {
            subRouteStack.removeAt(subRouteStack.lastIndex)
        } else if (currentTab != MainTab.HOME) {
            currentTab = MainTab.HOME
        }
    }

    val visibleTabs = rememberSaveable(profile?.isAdmin) {
        if (profile?.isAdmin == true) {
            listOf(MainTab.HOME, MainTab.TASKS, MainTab.PROFILE, MainTab.ADMIN)
        } else {
            listOf(MainTab.HOME, MainTab.TASKS, MainTab.PROFILE)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (currentSubRoute == SubRoute.None) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.taskz_launcher_icon_1791997779230),
                                contentDescription = "Taskz",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (currentTab == MainTab.HOME) "Taskz" else currentTab.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
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
                            label = { Text(tab.title) },
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
                .padding(if (currentSubRoute == SubRoute.None) innerPadding else innerPadding)
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
                SubRoute.None -> {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToTasks = { currentTab = MainTab.TASKS },
                            onNavigateToGroups = { subRouteStack.add(SubRoute.GroupsList) },
                            onNavigateToProfile = { currentTab = MainTab.PROFILE },
                            onNavigateToAdmin = { currentTab = MainTab.ADMIN },
                            onSignedOut = onLogoutSuccess
                        )
                        MainTab.TASKS -> TasksScreen(
                            viewModel = tasksViewModel,
                            onCreateTaskClick = { subRouteStack.add(SubRoute.CreateTask()) },
                            onTaskClick = { task -> subRouteStack.add(SubRoute.TaskDetail(task)) },
                            onManageGroupsClick = { subRouteStack.add(SubRoute.GroupsList) }
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
    }
}
