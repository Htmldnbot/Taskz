package com.example.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.example.core.model.DailyDutyCheckin
import com.example.core.model.TaskGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.common.AccountInactiveDialog
import com.example.ui.common.SyncStatusCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToTasks: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToIncidents: () -> Unit = {},
    onNavigateToAlarmSettings: () -> Unit = {},
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profileFlow.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val scopedSummary by viewModel.scopedSummaryFlow.collectAsStateWithLifecycle()
    val syncMetadata by viewModel.syncMetadataFlow.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncManagerSyncing by viewModel.syncManagerSyncing.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val syncErrorMessage by viewModel.syncErrorMessage.collectAsStateWithLifecycle()
    val isAccountInactive by viewModel.isAccountInactive.collectAsStateWithLifecycle()
    val isSavingLocation by viewModel.isSavingLocation.collectAsStateWithLifecycle()
    val locationSaveError by viewModel.locationSaveError.collectAsStateWithLifecycle()

    LaunchedEffect(authContext.activeRole, authContext.scope) {
        viewModel.loadDashboardStats()
    }

    if (isAccountInactive) {
        AccountInactiveDialog(
            onDismiss = {
                viewModel.handleAccountInactiveDismissed(onSignedOut)
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val incidentRepo = remember { com.example.TaskzApplication.instance.systemIncidentRepository }

    var todayDutyCheckin by remember { mutableStateOf<DailyDutyCheckin?>(null) }
    var showDutyCheckinModal by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.id, authContext.scope.cityId, authContext.scope.centreId) {
        val uid = profile?.id
        if (!uid.isNullOrBlank()) {
            viewModel.ensureOrgHierarchyLoaded()
            val checkin = incidentRepo.getTodayDutyCheckin(uid)
            todayDutyCheckin = checkin
            val missingServerLocation = authContext.activeRole.hierarchyRank < com.example.core.model.TaskzRole.ROM.hierarchyRank &&
                (authContext.scope.cityId.isNullOrBlank() ||
                    (authContext.activeRole == com.example.core.model.TaskzRole.IT_MANAGER && authContext.scope.centreId.isNullOrBlank()))
            if (checkin == null || missingServerLocation) {
                showDutyCheckinModal = true
            }
        }
    }

    fun checkFullScreenIntentGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.canUseFullScreenIntent()
        } else true
    }

    fun checkExactAlarmGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    fun checkNotificationsEnabled(): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    var hasNotificationsPermission by remember {
        mutableStateOf(checkNotificationsEnabled())
    }
    var hasOverlayPermission by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    var hasFullScreenIntentPermission by remember {
        mutableStateOf(checkFullScreenIntentGranted())
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(checkExactAlarmGranted())
    }

    // Refresh alarm permission states whenever screen resumes
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        hasNotificationsPermission = checkNotificationsEnabled()
        hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
        hasFullScreenIntentPermission = checkFullScreenIntentGranted()
        hasExactAlarmPermission = checkExactAlarmGranted()
        onPauseOrDispose {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Today's Examination Duty Station Card
        if (todayDutyCheckin != null) {
            val duty = todayDutyCheckin!!
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("active_duty_station_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Station: ${duty.centreCode} · ${duty.centreName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (duty.role == "VCO") "Role: VCO (Venue Commanding Officer / Venue Head)" else "Role: IT Manager (${duty.lan ?: "All LANs"})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDutyCheckinModal = true },
                        modifier = Modifier.testTag("btn_change_duty_station")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Station",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            // Prompt to check in today
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { showDutyCheckinModal = true }
                    .testTag("prompt_duty_checkin_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Daily Duty Station Check-in",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Tap to report your assigned Centre & Role for today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Button(
                        onClick = { showDutyCheckinModal = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Check In")
                    }
                }
            }
        }

        // Alarm Permissions Banner for Full Screen Alerts & Ringing when closed
        if (!hasNotificationsPermission || !hasOverlayPermission || !hasFullScreenIntentPermission || !hasExactAlarmPermission) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .testTag("overlay_permission_banner")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Full-Screen Alarm Permissions Needed",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append("Enable the permissions below so high-priority alarms ring and open full-screen even when the app is closed:")
                            if (!hasNotificationsPermission) append("\n• Notifications (Required to post & ring alarms)")
                            if (!hasFullScreenIntentPermission) append("\n• Full-Screen Intent (Lock-screen & urgent popups)")
                            if (!hasOverlayPermission) append("\n• Display Over Other Apps (Immediate popup when unlocked)")
                            if (!hasExactAlarmPermission) append("\n• Exact Alarms (Exact trigger timing)")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasFullScreenIntentPermission &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        ) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Full-Screen", maxLines = 1)
                            }
                        } else if (!hasOverlayPermission) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Grant Overlay", maxLines = 1)
                            }
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = onNavigateToAlarmSettings,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Alarm Settings", maxLines = 1)
                        }
                    }
                }
            }
        }

        // User Greeting Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val photoUrl = profile?.avatarSignedUrl
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile?.fullName?.take(2)?.uppercase() ?: "TZ",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = profile?.fullName?.ifBlank { "Employee" } ?: "Employee",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = profile?.employeeId?.ifBlank { "EMP" } ?: "EMP",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.testTag("home_active_role_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = authContext.activeRole.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scope: ${authContext.scope.summaryLabel()} • Assigned Roles: ${authContext.assignedRoles.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synchronization Status Card
        SyncStatusCard(
            syncMetadata = syncMetadata,
            onRefreshClick = { viewModel.triggerAutomaticRefresh() },
            onClearFailedClick = {
                viewModel.clearFailedSyncItems()
            },
            externalErrorMessage = syncErrorMessage,
            isRefreshing = isRefreshing || syncManagerSyncing
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Role-Aware Dashboard Summary Statistics
        val overviewHeading = when (authContext.activeRole) {
            com.example.core.model.TaskzRole.IT_MANAGER -> "Centre & LAN Operations (${authContext.scope.summaryLabel()})"
            com.example.core.model.TaskzRole.VCO -> "Venue & City Coordination (${authContext.scope.summaryLabel()})"
            com.example.core.model.TaskzRole.CITY_HEAD -> "City Operational Overview (${authContext.scope.summaryLabel()})"
            com.example.core.model.TaskzRole.ROM -> "State / Region Overview (${authContext.scope.summaryLabel()})"
            com.example.core.model.TaskzRole.ZOM -> "Zone Operational Overview (${authContext.scope.summaryLabel()})"
            com.example.core.model.TaskzRole.COMMAND_CENTER -> "Command Center Overview"
            com.example.core.model.TaskzRole.SUPER_ADMIN -> "Global Governance Overview"
        }
        Text(
            text = overviewHeading,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Assigned / Scope Tasks",
                value = maxOf(scopedSummary.assignedTasks, dashboardStats.myTasksToday).toString(),
                icon = Icons.Default.Today,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
            StatCard(
                title = "Pending Tasks",
                value = maxOf(scopedSummary.pendingTasks, dashboardStats.upcomingTasks).toString(),
                icon = Icons.Default.EventNote,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Overdue Tasks",
                value = scopedSummary.overdueTasks.toString(),
                icon = Icons.Default.HourglassTop,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
            StatCard(
                title = if (scopedSummary.escalatedIncidents > 0) "Open / Escalated Incidents" else "Open Incidents",
                value = if (scopedSummary.escalatedIncidents > 0) "${scopedSummary.openIncidents} (${scopedSummary.escalatedIncidents}↑)" else scopedSummary.openIncidents.toString(),
                icon = Icons.Default.Lan,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToIncidents
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Unread Alerts",
                value = maxOf(scopedSummary.unreadNotifications, unreadNotifications).toString(),
                icon = Icons.Default.Warning,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
            StatCard(
                title = "Open Vacancies",
                value = dashboardStats.vacancies.toString(),
                icon = Icons.Default.WorkOutline,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Authorized Modules (${authContext.activeRole.displayName})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        ModuleTile(
            title = "Task Management",
            subtitle = "Scoped duties for ${authContext.scope.summaryLabel()} • ${authContext.activeRole.displayName}",
            icon = Icons.Default.Assignment,
            tag = "Active",
            onClick = onNavigateToTasks,
            testTag = "home_module_tasks"
        )

        if (viewModel.permissionManager.canManageGroups()) {
            Spacer(modifier = Modifier.height(10.dp))

            ModuleTile(
                title = "Groups & Events",
                subtitle = "Organize workforce by event, shifts, and operational units",
                icon = Icons.Default.Groups,
                tag = "Scope Mgmt",
                onClick = onNavigateToGroups,
                testTag = "home_module_groups"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        ModuleTile(
            title = "System Incident Register",
            subtitle = "Scoped technical failures, escalations, and live LAN logs",
            icon = Icons.Default.DesktopWindows,
            tag = "Technical",
            onClick = onNavigateToIncidents,
            testTag = "home_module_incidents"
        )

        Spacer(modifier = Modifier.height(10.dp))

        ModuleTile(
            title = "Employee Profile & Active Role",
            subtitle = "Switch between your assigned roles, view scope & device info",
            icon = Icons.Default.Person,
            tag = "Profile",
            onClick = onNavigateToProfile,
            testTag = "home_module_profile"
        )

        if (viewModel.permissionManager.canManageUsers()) {
            Spacer(modifier = Modifier.height(10.dp))
            ModuleTile(
                title = "Administrative Console",
                subtitle = "Manage employees, roles, geographic scope & audit logs",
                icon = Icons.Default.AdminPanelSettings,
                tag = "Super Admin",
                isHighlighted = true,
                onClick = onNavigateToAdmin,
                testTag = "home_module_admin"
            )
        }

        if (showDutyCheckinModal) {
            DailyDutyCheckinDialog(
                orgHierarchy = orgHierarchy,
                currentScope = authContext.scope,
                currentActiveRole = authContext.activeRole,
                currentCheckin = todayDutyCheckin,
                isSaving = isSavingLocation,
                errorMessage = locationSaveError,
                onDismiss = {
                    viewModel.clearLocationError()
                    showDutyCheckinModal = false
                },
                onConfirm = { stateId, cityId, centreId, centreCode, centreName, role, lan ->
                    val user = profile ?: return@DailyDutyCheckinDialog
                    viewModel.updateOperationalLocation(
                        roleCode = role,
                        stateId = stateId,
                        cityId = cityId,
                        centreId = centreId,
                        centreCode = centreCode,
                        lanNumber = lan
                    ) {
                        coroutineScope.launch {
                            val saved = incidentRepo.saveDailyDutyCheckin(
                                userId = user.id,
                                userName = user.fullName,
                                employeeId = user.employeeId,
                                centreCode = centreCode,
                                centreName = centreName,
                                role = role,
                                lan = lan
                            )
                            todayDutyCheckin = saved
                            showDutyCheckinModal = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModuleTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    testTag: String = ""
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DailyDutyCheckinDialog(
    orgHierarchy: com.example.core.model.OrgHierarchyData,
    currentScope: com.example.core.model.OrgScope,
    currentActiveRole: com.example.core.model.TaskzRole,
    currentCheckin: DailyDutyCheckin?,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        stateId: String?,
        cityId: String?,
        centreId: String?,
        centreCode: String,
        centreName: String,
        roleCode: String,
        lan: String?
    ) -> Unit
) {
    val incidentRepo = remember { com.example.TaskzApplication.instance.systemIncidentRepository }
    var fallbackCentres by remember { mutableStateOf<List<TaskGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (orgHierarchy.centres.isEmpty()) {
            runCatching { com.example.TaskzApplication.instance.taskzSyncManager.refreshOrgHierarchy() }
        }
        fallbackCentres = incidentRepo.getAvailableCentres()
    }

    val states = orgHierarchy.states.sortedBy { it.name }
    var selectedStateId by remember(states, currentScope.stateId) {
        mutableStateOf(
            states.firstOrNull { it.id == currentScope.stateId }?.id
                ?: states.firstOrNull { it.name.equals("Rajasthan", ignoreCase = true) }?.id
                ?: states.firstOrNull()?.id
        )
    }

    val citiesForState = remember(orgHierarchy.cities, selectedStateId) {
        orgHierarchy.cities
            .filter { selectedStateId == null || it.stateId == selectedStateId }
            .sortedBy { it.name }
    }
    var selectedCityId by remember(citiesForState, currentScope.cityId) {
        mutableStateOf(
            citiesForState.firstOrNull { it.id == currentScope.cityId }?.id
                ?: citiesForState.firstOrNull { it.name.equals("Jaipur", ignoreCase = true) }?.id
                ?: citiesForState.firstOrNull()?.id
        )
    }
    LaunchedEffect(selectedStateId, citiesForState) {
        if (citiesForState.none { it.id == selectedCityId }) {
            selectedCityId = citiesForState.firstOrNull { it.id == currentScope.cityId }?.id
                ?: citiesForState.firstOrNull { it.name.equals("Jaipur", ignoreCase = true) }?.id
                ?: citiesForState.firstOrNull()?.id
        }
    }

    val centresForCity = remember(orgHierarchy.centres, selectedCityId) {
        orgHierarchy.centres
            .filter { selectedCityId == null || it.cityId == selectedCityId }
            .sortedBy { it.code }
    }
    var selectedOrgCentreId by remember(centresForCity, currentScope.centreId, currentCheckin?.centreCode) {
        mutableStateOf(
            centresForCity.firstOrNull { it.id == currentScope.centreId }?.id
                ?: centresForCity.firstOrNull {
                    it.code.equals(currentCheckin?.centreCode, ignoreCase = true) ||
                        it.code.equals(currentScope.centreCode, ignoreCase = true)
                }?.id
                ?: centresForCity.firstOrNull()?.id
        )
    }
    LaunchedEffect(selectedCityId, centresForCity) {
        if (centresForCity.none { it.id == selectedOrgCentreId }) {
            selectedOrgCentreId = centresForCity.firstOrNull { it.id == currentScope.centreId }?.id
                ?: centresForCity.firstOrNull()?.id
        }
    }

    var fallbackCentre by remember(currentCheckin, fallbackCentres) {
        mutableStateOf(
            fallbackCentres.firstOrNull { it.code.equals(currentCheckin?.centreCode, ignoreCase = true) }
                ?: fallbackCentres.firstOrNull()
        )
    }

    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var centreDropdownExpanded by remember { mutableStateOf(false) }

    val canSwitchOnboardingRole = currentActiveRole.hierarchyRank <= com.example.core.model.TaskzRole.VCO.hierarchyRank
    var selectedRole by remember(currentCheckin, currentActiveRole) {
        mutableStateOf(
            if (canSwitchOnboardingRole) {
                currentCheckin?.role ?: currentActiveRole.code
            } else {
                currentActiveRole.code
            }
        )
    }

    val availableLans = remember { (1..25).map { "LAN $it" } }
    var selectedLan by remember(currentCheckin, currentScope.assignedLan) {
        mutableStateOf(currentScope.assignedLan ?: currentCheckin?.lan ?: "LAN 1")
    }
    var lanDropdownExpanded by remember { mutableStateOf(false) }

    val selectedState = states.firstOrNull { it.id == selectedStateId }
    val selectedCity = citiesForState.firstOrNull { it.id == selectedCityId }
    val selectedOrgCentre = centresForCity.firstOrNull { it.id == selectedOrgCentreId }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duty Station & Scope Setup",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "Select your State, City, Examination Centre & Role.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (states.isNotEmpty()) {
                    // 1. State Selector
                    Text(
                        text = "1. Select State:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { stateDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("daily_state_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedState?.name ?: "Select State",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = stateDropdownExpanded,
                            onDismissRequest = { stateDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            states.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.name} (${st.code})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedStateId = st.id
                                        stateDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (citiesForState.isNotEmpty()) {
                    // 2. City Selector (14 Rajasthan Cities)
                    Text(
                        text = "2. Select City:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { cityDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("daily_city_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedCity?.let { "${it.name} (${it.code})" } ?: "Select City",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = cityDropdownExpanded,
                            onDismissRequest = { cityDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            citiesForState.forEach { ct ->
                                DropdownMenuItem(
                                    text = { Text("${ct.name} (${ct.code})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedCityId = ct.id
                                        cityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Examination Centre Selector
                Text(
                    text = "3. Select Examination Centre:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { centreDropdownExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("daily_centre_dropdown")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val titleText = selectedOrgCentre?.let { "${it.code} · ${it.name}" }
                                    ?: fallbackCentre?.title
                                    ?: "Select Examination Centre"
                                val subText = selectedCity?.let { "${it.name}, ${selectedState?.name ?: "Rajasthan"}" }
                                    ?: fallbackCentre?.description
                                    ?: "Jaipur, Rajasthan"
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = centreDropdownExpanded,
                        onDismissRequest = { centreDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        if (centresForCity.isNotEmpty()) {
                            centresForCity.forEach { centre ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = "${centre.code} · ${centre.name}", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = "${selectedCity?.name ?: centre.districtName.orEmpty()}, ${selectedState?.name ?: "Rajasthan"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedOrgCentreId = centre.id
                                        centreDropdownExpanded = false
                                    }
                                )
                            }
                        } else {
                            fallbackCentres.forEach { centre ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = centre.title, fontWeight = FontWeight.SemiBold)
                                            Text(text = centre.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        fallbackCentre = centre
                                        centreDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. Duty Role Selection
                Text(
                    text = "4. Your Duty Role:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                if (canSwitchOnboardingRole) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRole == "IT_MANAGER",
                            onClick = { selectedRole = "IT_MANAGER" },
                            label = { Text("IT MANAGER\n(LAN & Systems)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("role_chip_it_manager")
                        )

                        FilterChip(
                            selected = selectedRole == "VCO",
                            onClick = { selectedRole = "VCO" },
                            label = { Text("VCO\n(Venue Head)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).testTag("role_chip_vco")
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentActiveRole.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // 5. If IT Manager: Select Assigned LAN
                if (selectedRole == "IT_MANAGER") {
                    Text(
                        text = "5. Assigned LAN:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { lanDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("daily_lan_dropdown")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lan, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedLan,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = lanDropdownExpanded,
                            onDismissRequest = { lanDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            availableLans.forEach { lan ->
                                DropdownMenuItem(
                                    text = { Text(lan, fontWeight = if (selectedLan == lan) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        selectedLan = lan
                                        lanDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    val code = selectedOrgCentre?.code
                        ?: fallbackCentre?.code?.ifBlank { "141" }
                        ?: "141"
                    val name = selectedOrgCentre?.name
                        ?: fallbackCentre?.title?.removePrefix("$code ·")?.removePrefix("$code -")?.trim()
                        ?: "${selectedCity?.name ?: "Jaipur"} Centre"
                    val lan = if (selectedRole == "IT_MANAGER") selectedLan else null
                    onConfirm(
                        selectedState?.id,
                        selectedCity?.id,
                        selectedOrgCentre?.id,
                        code,
                        name,
                        selectedRole,
                        lan
                    )
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_daily_checkin_btn")
            ) {
                if (isSaving) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "Saving..." else "Confirm Station")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
