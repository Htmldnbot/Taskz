package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.model.DailyDutyCheckin
import com.example.core.model.OrgCentre
import com.example.core.model.OrgHierarchyData
import com.example.core.model.OrgScope
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.TaskzRole
import com.example.ui.common.AccountInactiveDialog
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatTile
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.SyncStatusCard
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch

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
    tasks: List<TaskItem> = emptyList(),
    onTaskClick: (TaskItem) -> Unit = {},
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
    val isTogglingLeave by viewModel.isTogglingLeave.collectAsStateWithLifecycle()

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

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val incidentRepo = remember { com.example.TaskzApplication.instance.systemIncidentRepository }

    var todayDutyCheckin by remember { mutableStateOf<DailyDutyCheckin?>(null) }
    var showDutyCheckinModal by remember { mutableStateOf(false) }
    var alarmBannerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.id, authContext.activeRole, authContext.scope, authContext.isOnLeave) {
        val uid = profile?.id
        if (!uid.isNullOrBlank()) {
            viewModel.ensureOrgHierarchyLoaded()
            val checkin = incidentRepo.getTodayDutyCheckin(uid)
            todayDutyCheckin = checkin
            val missingServerLocation = !authContext.isOnLeave && when (authContext.activeRole) {
                TaskzRole.ZOM -> authContext.scope.zoneId.isNullOrBlank()
                TaskzRole.ROM -> authContext.scope.stateId.isNullOrBlank()
                TaskzRole.CITY_HEAD -> authContext.scope.cityId.isNullOrBlank()
                TaskzRole.VCO ->
                    authContext.scope.centreId.isNullOrBlank() && authContext.scope.cityId.isNullOrBlank()
                TaskzRole.IT_MANAGER ->
                    authContext.scope.centreId.isNullOrBlank() || authContext.scope.assignedLan.isNullOrBlank()
                TaskzRole.COMMAND_CENTER,
                TaskzRole.SUPER_ADMIN -> false
            }
            val needsCheckin = !authContext.isOnLeave &&
                authContext.activeRole.hierarchyRank <= TaskzRole.VCO.hierarchyRank &&
                checkin == null
            if (missingServerLocation || needsCheckin) {
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

    var hasNotificationsPermission by remember { mutableStateOf(checkNotificationsEnabled()) }
    var hasOverlayPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    var hasFullScreenIntentPermission by remember { mutableStateOf(checkFullScreenIntentGranted()) }
    var hasExactAlarmPermission by remember { mutableStateOf(checkExactAlarmGranted()) }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        hasNotificationsPermission = checkNotificationsEnabled()
        hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
        hasFullScreenIntentPermission = checkFullScreenIntentGranted()
        hasExactAlarmPermission = checkExactAlarmGranted()
        onPauseOrDispose {}
    }

    val liveTimelineTasks = remember(tasks) {
        tasks
            .filter { !it.status.equals("completed", ignoreCase = true) }
            .sortedBy { it.executionTime ?: it.dueAt ?: it.startsAt ?: "9999" }
            .take(4)
    }
    val nextDueTask = liveTimelineTasks.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NothingTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Compact Operator Identity & Duty Station Strip
        val isFieldLevel = authContext.activeRole.hierarchyRank <= TaskzRole.VCO.hierarchyRank
        NothingHairlineCard(contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val photoUrl = profile?.avatarSignedUrl
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, NothingTheme.colors.border, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NothingTheme.colors.surfaceElevated)
                            .border(1.dp, NothingTheme.colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        NothingDotMatrixText(
                            text = profile?.fullName?.take(2)?.uppercase() ?: "TZ",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.fullName?.ifBlank { "Employee" } ?: "Employee",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingTagChip(text = profile?.employeeId?.ifBlank { "EMP" } ?: "EMP")
                        NothingTagChip(
                            text = authContext.activeRole.displayName,
                            containerColor = NothingTheme.colors.accentPrimary,
                            contentColor = NothingTheme.colors.onAccentPrimary,
                            borderColor = NothingTheme.colors.accentPrimary,
                            modifier = Modifier.testTag("home_active_role_badge")
                        )
                    }
                }

                IconButton(
                    onClick = onNavigateToProfile,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Profile",
                        tint = NothingTheme.colors.textSecondary
                    )
                }
            }

            if (!isFieldLevel &&
                authContext.activeRole != TaskzRole.COMMAND_CENTER &&
                authContext.activeRole != TaskzRole.SUPER_ADMIN
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_duty_station_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${authContext.activeRole.displayName} · ${authContext.scope.summaryLabel()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showDutyCheckinModal = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_change_duty_station")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Scope",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (isFieldLevel && todayDutyCheckin != null) {
                val duty = todayDutyCheckin!!
                Spacer(modifier = Modifier.height(10.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_duty_station_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${duty.centreCode} · ${duty.centreName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (duty.role == "VCO") "VCO" else "IT Manager · ${duty.lan ?: "All LANs"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { showDutyCheckinModal = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_change_duty_station")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Station",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (isFieldLevel) {
                Spacer(modifier = Modifier.height(10.dp))
                NothingDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDutyCheckinModal = true }
                        .testTag("prompt_duty_checkin_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        NothingStatusDot(color = NothingAmber, size = 7.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select Duty Station (Centre & LAN)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingAmber
                        )
                    }
                    NothingTagChip(
                        text = "SET",
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary,
                        borderColor = NothingTheme.colors.accentPrimary
                    )
                }
            }
        }

        // 2. Compact Duty / On-Leave Strip
        NothingHairlineCard(
            containerColor = if (authContext.isOnLeave) NothingAmber.copy(alpha = 0.08f) else NothingTheme.colors.surface,
            borderColor = if (authContext.isOnLeave) NothingAmber.copy(alpha = 0.45f) else NothingTheme.colors.border,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("home_on_leave_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingStatusDot(
                        color = if (authContext.isOnLeave) NothingAmber else NothingGreen,
                        size = 7.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = if (authContext.isOnLeave) NothingAmber else NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (authContext.isOnLeave) "On Leave · No alarms/tasks" else "On Duty · Active for assignments",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (authContext.isOnLeave) NothingAmber else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = authContext.isOnLeave,
                    enabled = !isTogglingLeave,
                    onCheckedChange = { checked -> viewModel.toggleLeaveStatus(checked) },
                    modifier = Modifier.testTag("home_on_leave_switch")
                )
            }
        }

        // 3. Compact Collapsible Alarm Permissions Warning
        if (!hasNotificationsPermission || !hasOverlayPermission || !hasFullScreenIntentPermission || !hasExactAlarmPermission) {
            NothingHairlineCard(
                containerColor = NothingRed.copy(alpha = 0.08f),
                borderColor = NothingRed.copy(alpha = 0.45f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.testTag("overlay_permission_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alarmBannerExpanded = !alarmBannerExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NothingRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alarm permissions required",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = NothingRed
                        )
                    }
                    Icon(
                        imageVector = if (alarmBannerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle details",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = alarmBannerExpanded) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            text = buildString {
                                if (!hasNotificationsPermission) append("• Notifications (ring alarms)\n")
                                if (!hasFullScreenIntentPermission) append("• Full-Screen Intent (lock-screen popup)\n")
                                if (!hasOverlayPermission) append("• Display Over Other Apps\n")
                                if (!hasExactAlarmPermission) append("• Exact Alarms (precise timing)")
                            }.trimEnd(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!hasFullScreenIntentPermission &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NothingRed, contentColor = Color.White),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                        ) {
                            Text("Grant Full-Screen", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NothingRed, contentColor = Color.White),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                        ) {
                            Text("Grant Overlay", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToAlarmSettings,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Text("Alarm Settings", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // 4. Role-Aware KPI Command Grid
        val overviewHeading = when (authContext.activeRole) {
            TaskzRole.IT_MANAGER -> "CENTRE TELEMETRY"
            TaskzRole.VCO -> "VENUE TELEMETRY"
            TaskzRole.CITY_HEAD -> "CITY TELEMETRY"
            TaskzRole.ROM -> "STATE TELEMETRY"
            TaskzRole.ZOM -> "ZONE TELEMETRY"
            TaskzRole.COMMAND_CENTER -> "SPOC TELEMETRY"
            TaskzRole.SUPER_ADMIN -> "ADMIN TELEMETRY"
        }
        NothingSectionHeader(
            title = overviewHeading,
            trailingText = authContext.scope.summaryLabel(),
            onTrailingClick = if (isFieldLevel) ({ showDutyCheckinModal = true }) else null,
            useDotMatrix = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NothingStatTile(
                value = maxOf(scopedSummary.assignedTasks, dashboardStats.myTasksToday).toString(),
                label = "Assigned / Scope",
                statusDotColor = NothingGreen,
                onClick = onNavigateToTasks,
                modifier = Modifier.weight(1f)
            )
            NothingStatTile(
                value = maxOf(scopedSummary.pendingTasks, dashboardStats.upcomingTasks).toString(),
                label = "Pending Tasks",
                onClick = onNavigateToTasks,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NothingStatTile(
                value = scopedSummary.overdueTasks.toString(),
                label = "Overdue",
                statusDotColor = if (scopedSummary.overdueTasks > 0) NothingRed else null,
                onClick = onNavigateToTasks,
                modifier = Modifier.weight(1f)
            )
            NothingStatTile(
                value = if (scopedSummary.escalatedIncidents > 0) "${scopedSummary.openIncidents}+${scopedSummary.escalatedIncidents}" else scopedSummary.openIncidents.toString(),
                label = "Open Incidents",
                statusDotColor = if (scopedSummary.openIncidents > 0) NothingAmber else null,
                onClick = onNavigateToIncidents,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NothingStatTile(
                value = maxOf(scopedSummary.unreadNotifications, unreadNotifications).toString(),
                label = "Unread Alerts",
                onClick = onNavigateToTasks,
                modifier = Modifier.weight(1f)
            )
            NothingStatTile(
                value = dashboardStats.vacancies.toString(),
                label = "Open Vacancies",
                onClick = onNavigateToTasks,
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Next Due Task & Live Today Timeline
        NothingSectionHeader(
            title = "LIVE TIMELINE",
            trailingText = "ALL TASKS ›",
            onTrailingClick = onNavigateToTasks,
            useDotMatrix = true
        )

        if (nextDueTask != null) {
            val nextTime = (nextDueTask.executionTime ?: nextDueTask.dueAt ?: nextDueTask.startsAt)?.let {
                TaskItem.formatForDisplay(it) ?: it
            } ?: "SCHEDULED"
            NothingHairlineCard(
                onClick = { onTaskClick(nextDueTask) },
                containerColor = NothingTheme.colors.surfaceElevated,
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingTagChip(
                        text = "NEXT DUE",
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary,
                        borderColor = NothingTheme.colors.accentPrimary
                    )
                    Text(
                        text = nextTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = nextDueTask.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(nextDueTask.groupTitle, nextDueTask.shiftName, nextDueTask.location).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (liveTimelineTasks.size > 1) {
                NothingHairlineCard(contentPadding = PaddingValues(0.dp)) {
                    liveTimelineTasks.drop(1).forEachIndexed { idx, item ->
                        if (idx > 0) NothingDivider()
                        val itemTime = (item.executionTime ?: item.dueAt ?: item.startsAt)?.let {
                            TaskItem.formatForDisplay(it) ?: it
                        } ?: "--:--"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTaskClick(item) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NothingStatusDot(
                                color = if (item.priority.equals("urgent", ignoreCase = true)) NothingRed else NothingTheme.colors.textSecondary,
                                size = 6.dp
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = itemTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontSize = 10.sp
                                ),
                                color = NothingTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // 6. Sync Status Strip
        SyncStatusCard(
            syncMetadata = syncMetadata,
            onRefreshClick = { viewModel.triggerAutomaticRefresh() },
            onClearFailedClick = { viewModel.clearFailedSyncItems() },
            externalErrorMessage = syncErrorMessage,
            isRefreshing = isRefreshing || syncManagerSyncing
        )

        // 7. Compact Operations Modules
        NothingSectionHeader(title = "MODULES", useDotMatrix = true)

        NothingHairlineCard(contentPadding = PaddingValues(0.dp)) {
            HomeModuleRow(
                title = "Tasks",
                subtitle = "${authContext.scope.summaryLabel()} · ${authContext.activeRole.displayName}",
                icon = Icons.Default.Assignment,
                tag = "ACTIVE",
                onClick = onNavigateToTasks,
                testTag = "home_module_tasks"
            )
            if (viewModel.permissionManager.canSelfJoinGroups()) {
                NothingDivider()
                HomeModuleRow(
                    title = "Exam Groups & Shifts",
                    subtitle = "1-click group join & shift workflows",
                    icon = Icons.Default.Groups,
                    tag = if (viewModel.permissionManager.canManageGroups()) "MANAGE" else "JOIN",
                    onClick = onNavigateToGroups,
                    testTag = "home_module_groups"
                )
            }
            NothingDivider()
            HomeModuleRow(
                title = "Incident Register",
                subtitle = "System locks & LAN telemetry",
                icon = Icons.Default.DesktopWindows,
                tag = "LOG",
                onClick = onNavigateToIncidents,
                testTag = "home_module_incidents"
            )
            NothingDivider()
            HomeModuleRow(
                title = "Profile & Role",
                subtitle = "Roles, scope & leave state",
                icon = Icons.Default.Person,
                tag = "PROFILE",
                onClick = onNavigateToProfile,
                testTag = "home_module_profile"
            )
            if (viewModel.permissionManager.canManageUsers()) {
                NothingDivider()
                HomeModuleRow(
                    title = "Admin Console",
                    subtitle = "Users, roles & scope controls",
                    icon = Icons.Default.AdminPanelSettings,
                    tag = "ADMIN",
                    onClick = onNavigateToAdmin,
                    testTag = "home_module_admin"
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
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
            onConfirm = { zoneId, stateId, cityId, centreId, centreCode, centreName, role, lan ->
                val user = profile ?: return@DailyDutyCheckinDialog
                viewModel.updateOperationalLocation(
                    roleCode = role,
                    zoneId = zoneId,
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

@Composable
private fun HomeModuleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NothingTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NothingTagChip(text = tag)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NothingTheme.colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun DailyDutyCheckinDialog(
    orgHierarchy: OrgHierarchyData,
    currentScope: OrgScope,
    currentActiveRole: TaskzRole,
    currentCheckin: DailyDutyCheckin?,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        zoneId: String?,
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
        if (orgHierarchy.centres.isEmpty() || orgHierarchy.zones.isEmpty()) {
            runCatching { com.example.TaskzApplication.instance.taskzSyncManager.refreshOrgHierarchy() }
        }
        fallbackCentres = incidentRepo.getAvailableCentres()
    }

    val canSwitchOnboardingRole = currentActiveRole.hierarchyRank <= TaskzRole.VCO.hierarchyRank
    var selectedRole by remember(currentCheckin, currentActiveRole) {
        mutableStateOf(
            if (canSwitchOnboardingRole) {
                TaskzRole.fromCode(currentCheckin?.role ?: currentActiveRole.code).code
            } else {
                currentActiveRole.code
            }
        )
    }
    val effectiveRole = remember(selectedRole, currentActiveRole, canSwitchOnboardingRole) {
        if (canSwitchOnboardingRole) TaskzRole.fromCode(selectedRole) else currentActiveRole
    }

    val zones = orgHierarchy.zones.sortedBy { it.name }
    var selectedZoneId by remember(zones, currentScope.zoneId) {
        mutableStateOf(zones.firstOrNull { it.id == currentScope.zoneId }?.id ?: zones.firstOrNull()?.id)
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
        orgHierarchy.cities.filter { selectedStateId == null || it.stateId == selectedStateId }.sortedBy { it.name }
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
        orgHierarchy.centres.filter { selectedCityId == null || it.cityId == selectedCityId }.sortedBy { it.code }
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

    var zoneDropdownExpanded by remember { mutableStateOf(false) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var centreDropdownExpanded by remember { mutableStateOf(false) }

    val selectedZone = zones.firstOrNull { it.id == selectedZoneId }
    val selectedState = states.firstOrNull { it.id == selectedStateId }
    val selectedCity = citiesForState.firstOrNull { it.id == selectedCityId }
    val selectedOrgCentre = centresForCity.firstOrNull { it.id == selectedOrgCentreId }

    val availableLans = remember(selectedOrgCentre, fallbackCentre) {
        selectedOrgCentre?.resolvedLans()
            ?: OrgCentre.defaultLansForCode(fallbackCentre?.code)
    }
    var selectedLan by remember(currentCheckin, currentScope.assignedLan) {
        mutableStateOf(
            (currentScope.assignedLan ?: currentCheckin?.lan)?.takeIf {
                availableLans.any { lan -> lan.equals(it, ignoreCase = true) }
            } ?: availableLans.firstOrNull() ?: "LAN 1"
        )
    }
    LaunchedEffect(availableLans) {
        if (availableLans.isNotEmpty() && availableLans.none { it.equals(selectedLan, ignoreCase = true) }) {
            selectedLan = availableLans.first()
        }
    }
    var lanDropdownExpanded by remember { mutableStateOf(false) }

    val showZoneField = effectiveRole == TaskzRole.ZOM
    val showStateField = effectiveRole in setOf(TaskzRole.ROM, TaskzRole.CITY_HEAD, TaskzRole.VCO, TaskzRole.IT_MANAGER)
    val showCityField = effectiveRole in setOf(TaskzRole.CITY_HEAD, TaskzRole.VCO, TaskzRole.IT_MANAGER)
    val showCentreField = effectiveRole in setOf(TaskzRole.VCO, TaskzRole.IT_MANAGER)
    val showLanField = effectiveRole == TaskzRole.IT_MANAGER

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = NothingTheme.colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                NothingDotMatrixText(
                    text = "${effectiveRole.displayName.uppercase()} SCOPE",
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (canSwitchOnboardingRole) {
                    Text(
                        text = "Role",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isItm = selectedRole == "IT_MANAGER"
                        Surface(
                            onClick = { selectedRole = "IT_MANAGER" },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isItm) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                            border = BorderStroke(1.dp, if (isItm) NothingTheme.colors.accentPrimary else NothingTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .testTag("role_chip_it_manager")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = if (isItm) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "IT Manager",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isItm) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val isVco = selectedRole == "VCO"
                        Surface(
                            onClick = { selectedRole = "VCO" },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isVco) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                            border = BorderStroke(1.dp, if (isVco) NothingTheme.colors.accentPrimary else NothingTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .testTag("role_chip_vco")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isVco) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "VCO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isVco) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (showZoneField) {
                    Text(text = "Zone", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NothingHairlineCard(
                            onClick = { zoneDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = NothingTheme.colors.surfaceElevated,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.testTag("daily_zone_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedZone?.let { "${it.name} (${it.code})" } ?: "Select Zone",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = zoneDropdownExpanded,
                            onDismissRequest = { zoneDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)
                        ) {
                            zones.forEach { zn ->
                                DropdownMenuItem(
                                    text = { Text("${zn.name} (${zn.code})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedZoneId = zn.id
                                        zoneDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (showStateField && states.isNotEmpty()) {
                    Text(text = "State", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NothingHairlineCard(
                            onClick = { stateDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = NothingTheme.colors.surfaceElevated,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.testTag("daily_state_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedState?.name ?: "Select State",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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

                if (showCityField && citiesForState.isNotEmpty()) {
                    Text(text = "City", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NothingHairlineCard(
                            onClick = { cityDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = NothingTheme.colors.surfaceElevated,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.testTag("daily_city_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedCity?.let { "${it.name} (${it.code})" } ?: "Select City",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
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

                if (showCentreField) {
                    Text(text = "Centre", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NothingHairlineCard(
                            onClick = { centreDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = NothingTheme.colors.surfaceElevated,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.testTag("daily_centre_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val titleText = selectedOrgCentre?.let { "${it.code} · ${it.name}" }
                                        ?: fallbackCentre?.title
                                        ?: "Select Centre"
                                    val subText = selectedCity?.let { "${it.name}, ${selectedState?.name ?: ""}" }
                                        ?: fallbackCentre?.description
                                        ?: ""
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (subText.isNotBlank()) {
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NothingTheme.colors.textSecondary
                                        )
                                    }
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
                                                    text = "${selectedCity?.name ?: centre.districtName.orEmpty()}, ${selectedState?.name ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NothingTheme.colors.textSecondary
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
                                                Text(
                                                    text = centre.description ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = NothingTheme.colors.textSecondary
                                                )
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
                }

                if (showLanField) {
                    Text(text = "Assigned LAN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        NothingHairlineCard(
                            onClick = { lanDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = NothingTheme.colors.surfaceElevated,
                            contentPadding = PaddingValues(12.dp),
                            modifier = Modifier.testTag("daily_lan_dropdown")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedLan,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = TechMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
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

                if (!showZoneField && !showStateField && !showCityField && !showCentreField) {
                    Text(
                        text = "Global scope active for ${effectiveRole.displayName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTheme.colors.textSecondary
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    val outZoneId = when (effectiveRole) {
                        TaskzRole.ZOM -> selectedZone?.id
                        else -> selectedState?.zoneId ?: selectedZone?.id
                    }
                    val outStateId = if (showStateField) selectedState?.id else null
                    val outCityId = if (showCityField) selectedCity?.id else null
                    val outCentreId = if (showCentreField) selectedOrgCentre?.id else null
                    val outCode = when (effectiveRole) {
                        TaskzRole.ZOM -> selectedZone?.code.orEmpty()
                        TaskzRole.ROM -> selectedState?.code.orEmpty()
                        TaskzRole.CITY_HEAD -> selectedCity?.code.orEmpty()
                        TaskzRole.COMMAND_CENTER,
                        TaskzRole.SUPER_ADMIN -> "GLOBAL"
                        else -> selectedOrgCentre?.code
                            ?: fallbackCentre?.code?.ifBlank { "141" }
                            ?: "141"
                    }
                    val outName = when (effectiveRole) {
                        TaskzRole.ZOM -> selectedZone?.name.orEmpty()
                        TaskzRole.ROM -> selectedState?.name.orEmpty()
                        TaskzRole.CITY_HEAD -> selectedCity?.name.orEmpty()
                        TaskzRole.COMMAND_CENTER,
                        TaskzRole.SUPER_ADMIN -> "Global"
                        else -> selectedOrgCentre?.name
                            ?: fallbackCentre?.title?.removePrefix("$outCode ·")?.removePrefix("$outCode -")?.trim()
                            ?: selectedCity?.name.orEmpty()
                    }
                    val outLan = if (showLanField) selectedLan else null
                    onConfirm(
                        outZoneId,
                        outStateId,
                        outCityId,
                        outCentreId,
                        outCode,
                        outName,
                        effectiveRole.code,
                        outLan
                    )
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("confirm_daily_checkin_btn")
            ) {
                if (isSaving) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
