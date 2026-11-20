package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AppNotification
import com.example.core.model.DailyDutyCheckin
import com.example.core.model.NotificationCategory
import com.example.core.model.OrgCentre
import com.example.core.model.OrgHierarchyData
import com.example.core.model.OrgScope
import com.example.core.model.SystemIncident
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.TaskStatus
import com.example.core.model.TaskzRole
import com.example.ui.common.AccountInactiveDialog
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingDotPatternStrip
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class HomeActivityRowData(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val sortKey: Long,
    val dotColor: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToTasks: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToIncidents: () -> Unit = {},
    onNavigateToReports: () -> Unit = onNavigateToIncidents,
    onNavigateToAlarmSettings: () -> Unit = {},
    onSignedOut: () -> Unit,
    tasks: List<TaskItem> = emptyList(),
    incidents: List<SystemIncident> = emptyList(),
    notifications: List<AppNotification> = emptyList(),
    onTaskClick: (TaskItem) -> Unit = {},
    onIncidentClick: (SystemIncident) -> Unit = { onNavigateToIncidents() },
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile by viewModel.profileFlow.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val scopedSummary by viewModel.scopedSummaryFlow.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
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

    // Real task summary & Today's Timeline
    val activeTasks = remember(tasks) {
        tasks.filter {
            !it.status.equals("completed", ignoreCase = true) &&
                !it.status.equals("cancelled", ignoreCase = true)
        }
    }
    val completedTasksCount = remember(tasks) {
        tasks.count { it.status.equals("completed", ignoreCase = true) }
    }
    val activeTaskCount = maxOf(activeTasks.size, scopedSummary.pendingTasks)
    val overdueCount = remember(activeTasks, scopedSummary) {
        maxOf(activeTasks.count { it.displayStatus == TaskStatus.OVERDUE }, scopedSummary.overdueTasks)
    }
    val dueTodayCount = remember(activeTasks, dashboardStats) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val matchingToday = activeTasks.count { task ->
            val millis = task.scheduledExecutionMillis() ?: return@count false
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == today
        }
        if (matchingToday > 0) matchingToday else dashboardStats.myTasksToday
    }
    val progressRingSweepRatio = remember(activeTaskCount, completedTasksCount, overdueCount) {
        val total = activeTaskCount + completedTasksCount
        when {
            total <= 0 -> 0f
            completedTasksCount > 0 -> (completedTasksCount.toFloat() / total.toFloat()).coerceIn(0.14f, 0.96f)
            else -> (1f - (overdueCount.toFloat() / total.coerceAtLeast(1)).coerceAtMost(0.75f)).coerceIn(0.25f, 0.72f)
        }
    }

    val liveTimelineTasks = remember(activeTasks) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val sorted = activeTasks.sortedBy { it.scheduledExecutionMillis() ?: Long.MAX_VALUE }
        val todayOrOverdue = sorted.filter { task ->
            val ms = task.scheduledExecutionMillis() ?: return@filter true
            val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            !date.isAfter(today)
        }
        (if (todayOrOverdue.isNotEmpty()) todayOrOverdue else sorted).take(4)
    }

    val recentActivityRows = remember(incidents, notifications, onOpenNotifications) {
        val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
        val zone = ZoneId.systemDefault()
        val incidentRows = incidents
            .sortedByDescending { it.incidentTimestampMillis }
            .take(2)
            .map { inc ->
                val isResolved = inc.status.equals("RESOLVED", ignoreCase = true)
                val meta = listOfNotNull(
                    inc.centreCode.takeIf { it.isNotBlank() }?.let { "Centre $it" }
                        ?: inc.centreName.takeIf { it.isNotBlank() },
                    inc.lanName.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                HomeActivityRowData(
                    id = "inc_${inc.id}",
                    title = inc.reason.ifBlank { "System Incident" },
                    subtitle = meta.ifBlank { inc.lockedSystemNumber.ifBlank { "System Log" } },
                    timeLabel = inc.incidentTime.ifBlank {
                        Instant.ofEpochMilli(inc.incidentTimestampMillis).atZone(zone).format(timeFmt)
                    },
                    sortKey = inc.incidentTimestampMillis,
                    dotColor = if (isResolved) NothingGreen else NothingRed,
                    onClick = { onIncidentClick(inc) }
                )
            }
        val notifRows = if (incidentRows.size < 2) {
            notifications.take(2 - incidentRows.size).map { notif ->
                val cat = NotificationCategory.fromRaw(notif.category, notif.type)
                val parsedMs = TaskItem.parseIsoToMillis(notif.createdAt) ?: 0L
                val formattedTime = if (parsedMs > 0L) {
                    Instant.ofEpochMilli(parsedMs).atZone(zone).format(timeFmt)
                } else {
                    notif.createdAt.takeLast(5)
                }
                HomeActivityRowData(
                    id = "notif_${notif.id}",
                    title = notif.title,
                    subtitle = notif.body?.takeIf { it.isNotBlank() } ?: cat.displayName,
                    timeLabel = formattedTime,
                    sortKey = parsedMs,
                    dotColor = if (cat.urgent) NothingRed else NothingGreen,
                    onClick = onOpenNotifications
                )
            }
        } else {
            emptyList()
        }
        (incidentRows + notifRows).sortedByDescending { it.sortKey }.take(2)
    }

    val greetingText = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
    }

    val isFieldLevel = authContext.activeRole.hierarchyRank <= TaskzRole.VCO.hierarchyRank
    val scopeSubtitle = remember(authContext.activeRole, authContext.scope, todayDutyCheckin, isFieldLevel) {
        val roleTitle = authContext.activeRole.displayName
        val stationPart = when {
            isFieldLevel && todayDutyCheckin != null -> {
                val duty = todayDutyCheckin!!
                val base = if (duty.centreCode.isNotBlank()) "Centre ${duty.centreCode}" else duty.centreName
                if (authContext.activeRole == TaskzRole.IT_MANAGER && !duty.lan.isNullOrBlank()) {
                    "$base · ${duty.lan}"
                } else {
                    base
                }
            }
            authContext.activeRole == TaskzRole.COMMAND_CENTER || authContext.activeRole == TaskzRole.SUPER_ADMIN ->
                "Global Scope"
            else -> authContext.scope.summaryLabel()
        }
        "$roleTitle • $stationPart"
    }
    val needsStationSelection = isFieldLevel &&
        todayDutyCheckin == null &&
        authContext.scope.centreCode.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NothingTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Minimalist Greeting & Active Role Scope Line
        Box(modifier = Modifier.fillMaxWidth()) {
            NothingDotPatternStrip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(72.dp)
                    .height(54.dp),
                dotRadiusDp = 1.4.dp,
                spacingDp = 9.dp
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 21.sp
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                Text(
                    text = profile?.fullName?.trim()?.ifBlank { "Team Member" } ?: "Team Member",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val canEditScope = authContext.activeRole != TaskzRole.COMMAND_CENTER &&
                    authContext.activeRole != TaskzRole.SUPER_ADMIN
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (canEditScope) {
                                Modifier.clickable { showDutyCheckinModal = true }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .testTag(
                            when {
                                needsStationSelection -> "prompt_duty_checkin_card"
                                canEditScope -> "btn_change_duty_station"
                                else -> "home_active_role_badge"
                            }
                        )
                ) {
                    if (needsStationSelection) {
                        NothingStatusDot(color = NothingAmber, size = 7.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${authContext.activeRole.displayName} • Select Duty Station ›",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = scopeSubtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = NothingTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (canEditScope) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Change scope",
                                tint = NothingTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Optional compact sync error notice
        if (!syncErrorMessage.isNullOrBlank()) {
            NothingHairlineCard(
                containerColor = NothingRed.copy(alpha = 0.08f),
                borderColor = NothingRed.copy(alpha = 0.4f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = syncErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingRed,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { viewModel.clearSyncError() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Compact Collapsible Alarm Permissions Warning (only visible when missing)
        if (!hasNotificationsPermission || !hasOverlayPermission || !hasFullScreenIntentPermission || !hasExactAlarmPermission) {
            NothingHairlineCard(
                containerColor = NothingRed.copy(alpha = 0.07f),
                borderColor = NothingRed.copy(alpha = 0.4f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alarm permissions required",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingRed
                        )
                    }
                    Icon(
                        imageVector = if (alarmBannerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle alarm permission details",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = alarmBannerExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
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
            }
        }

        // 2. Hero Active Tasks Progress Ring Card
        HomeTaskSummaryRingCard(
            activeTaskCount = activeTaskCount,
            overdueCount = overdueCount,
            dueTodayCount = dueTodayCount,
            sweepFraction = progressRingSweepRatio,
            onClick = onNavigateToTasks
        )

        // 3. Today's Timeline
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionTitleRow(
                title = "Today's Timeline",
                actionText = "View All",
                onActionClick = onNavigateToTasks,
                actionTestTag = "home_timeline_view_all"
            )

            if (liveTimelineTasks.isEmpty()) {
                NothingHairlineCard(
                    onClick = onNavigateToTasks,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "No pending tasks in timeline",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Upcoming & scheduled tasks appear here automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Tasks",
                            tint = NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    liveTimelineTasks.forEachIndexed { index, task ->
                        TimelineTaskRow(
                            task = task,
                            scopeFallback = authContext.scope,
                            isFirst = index == 0,
                            isLast = index == liveTimelineTasks.lastIndex,
                            onClick = { onTaskClick(task) }
                        )
                    }
                }
            }
        }

        // 4. Quick Access (2x2 Grid)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionTitleRow(title = "Quick Access")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeQuickAccessTile(
                    icon = Icons.Outlined.CheckBox,
                    title = "My Tasks",
                    subtitle = "View & manage",
                    onClick = onNavigateToTasks,
                    testTag = "home_module_tasks",
                    modifier = Modifier.weight(1f)
                )
                HomeQuickAccessTile(
                    icon = Icons.Outlined.ReportProblem,
                    title = "Incidents",
                    subtitle = "View incidents",
                    onClick = onNavigateToIncidents,
                    testTag = "home_module_incidents",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeQuickAccessTile(
                    icon = Icons.Outlined.Assessment,
                    title = "Reports",
                    subtitle = "View reports",
                    onClick = onNavigateToReports,
                    testTag = "home_module_reports",
                    modifier = Modifier.weight(1f)
                )
                when {
                    viewModel.permissionManager.canManageUsers() -> {
                        HomeQuickAccessTile(
                            icon = Icons.Outlined.Settings,
                            title = "Admin",
                            subtitle = "Manage system",
                            onClick = onNavigateToAdmin,
                            testTag = "home_module_admin",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    viewModel.permissionManager.canSelfJoinGroups() -> {
                        HomeQuickAccessTile(
                            icon = Icons.Outlined.Groups,
                            title = "Groups",
                            subtitle = "Exam shifts",
                            onClick = onNavigateToGroups,
                            testTag = "home_module_groups",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        HomeQuickAccessTile(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "Alarms",
                            subtitle = "Alert settings",
                            onClick = onNavigateToAlarmSettings,
                            testTag = "home_module_alarms",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. Recent Activity
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSectionTitleRow(
                title = "Recent Activity",
                actionText = "View All",
                onActionClick = if (incidents.isNotEmpty()) onNavigateToIncidents else onOpenNotifications,
                actionTestTag = "home_activity_view_all"
            )

            if (recentActivityRows.isEmpty()) {
                NothingHairlineCard(
                    onClick = onOpenNotifications,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NothingStatusDot(color = NothingTheme.colors.textSecondary, size = 8.dp)
                            Column {
                                Text(
                                    text = "All clear",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "No recent incidents or alerts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTheme.colors.textSecondary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View notifications",
                            tint = NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentActivityRows.forEach { entry ->
                        NothingHairlineCard(
                            onClick = entry.onClick,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.testTag("home_activity_${entry.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NothingStatusDot(color = entry.dotColor, size = 8.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.timeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontSize = 11.sp
                                    ),
                                    color = NothingTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = NothingTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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
private fun HomeTaskSummaryRingCard(
    activeTaskCount: Int,
    overdueCount: Int,
    dueTodayCount: Int,
    sweepFraction: Float,
    onClick: () -> Unit
) {
    val trackColor = NothingTheme.colors.border
    val progressColor = MaterialTheme.colorScheme.onSurface

    NothingHairlineCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        modifier = Modifier.testTag("home_summary_ring_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(98.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 6.dp.toPx()
                    val pad = strokePx / 2f + 3.dp.toPx()
                    val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
                    val topLeft = Offset(pad, pad)

                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    val clamped = sweepFraction.coerceIn(0f, 1f)
                    if (clamped > 0.01f) {
                        val sweepDeg = 360f * clamped
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = sweepDeg,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        val endRad = ((-90f + sweepDeg) * (PI / 180.0))
                        val radius = arcSize.width / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dotOffset = Offset(
                            x = center.x + (radius * cos(endRad)).toFloat(),
                            y = center.y + (radius * sin(endRad)).toFloat()
                        )
                        drawCircle(
                            color = progressColor.copy(alpha = 0.28f),
                            radius = 6.5.dp.toPx(),
                            center = dotOffset
                        )
                        drawCircle(
                            color = progressColor,
                            radius = 3.5.dp.toPx(),
                            center = dotOffset
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = activeTaskCount.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Active Tasks",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(58.dp)
                    .background(NothingTheme.colors.border)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(NothingRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = overdueCount.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (overdueCount > 0) NothingRed else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Overdue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dueTodayCount.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Due Today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NothingTheme.colors.surfaceElevated)
                    .border(1.dp, NothingTheme.colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Tasks",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTitleRow(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionTestTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NothingDotGridIcon(size = 15.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = NothingTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun NothingDotGridIcon(
    size: Dp = 15.dp,
    color: Color = NothingTheme.colors.textSecondary
) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = (this.size.minDimension / 9f).coerceAtLeast(1.4f)
        val step = (this.size.minDimension - radius * 2f) / 2f
        for (row in 0..2) {
            for (col in 0..2) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(radius + col * step, radius + row * step)
                )
            }
        }
    }
}

@Composable
private fun TimelineTaskRow(
    task: TaskItem,
    scopeFallback: OrgScope,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val lineColor = NothingTheme.colors.border
    val nodeActiveColor = MaterialTheme.colorScheme.onBackground
    val nodeInactiveColor = NothingTheme.colors.textSecondary
    val bgColor = NothingTheme.colors.background

    val timeString = remember(task.executionTime, task.dueAt, task.startsAt, task.taskType) {
        val ms = task.scheduledExecutionMillis()
        when {
            ms != null -> Instant.ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
            task.taskType == com.example.core.model.TaskType.IMMEDIATE -> "NOW"
            else -> "--:--"
        }
    }

    val subtitleString = remember(task, scopeFallback) {
        val loc = task.location?.takeIf { it.isNotBlank() }
            ?: scopeFallback.centreCode?.takeIf { it.isNotBlank() }?.let { "Centre $it" }
        val lan = task.lanTargetList.takeIf { it.isNotEmpty() }?.let { list ->
            if (list.size == 1) list.first() else "${list.first()} +${list.size - 1}"
        }
        val groupOrShift = task.shiftName?.takeIf { it.isNotBlank() }
            ?: task.groupTitle?.takeIf { it.isNotBlank() }
        listOfNotNull(loc, lan, if (loc == null && lan == null) groupOrShift else null)
            .joinToString(" • ")
            .ifBlank { task.description?.takeIf { it.isNotBlank() } ?: "Scheduled task" }
    }

    val status = task.displayStatus

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("timeline_task_${task.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left timeline node & connector column
        Canvas(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            if (!isFirst) {
                drawLine(
                    color = lineColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, cy),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            if (!isLast) {
                drawLine(
                    color = lineColor,
                    start = Offset(cx, cy),
                    end = Offset(cx, this.size.height),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            if (isFirst) {
                drawCircle(
                    color = nodeActiveColor.copy(alpha = 0.18f),
                    radius = 9.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = nodeActiveColor,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            } else {
                drawCircle(
                    color = bgColor,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = nodeInactiveColor,
                    radius = 4.5.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    ),
                    color = if (isFirst) MaterialTheme.colorScheme.onBackground else NothingTheme.colors.textSecondary,
                    modifier = Modifier.width(68.dp)
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(28.dp)
                        .background(NothingTheme.colors.borderSubtle)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = if (isFirst) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleString,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = NothingTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    status == TaskStatus.OVERDUE -> {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NothingRed.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Overdue",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = NothingRed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    isFirst || status == TaskStatus.ACTION_REQUIRED || status == TaskStatus.PENDING_CONFIRMATION -> {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NothingTheme.colors.accentPrimary
                        ) {
                            Text(
                                text = "Due",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = NothingTheme.colors.onAccentPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    task.currentProgress in 1..99 -> {
                        Spacer(modifier = Modifier.width(8.dp))
                        NothingTagChip(text = "${task.currentProgress}%")
                    }
                }
            }
            if (!isLast) {
                NothingDivider(color = NothingTheme.colors.borderSubtle)
            }
        }
    }
}

@Composable
private fun HomeQuickAccessTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    NothingHairlineCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        modifier = modifier
            .heightIn(min = 66.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = NothingTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = NothingTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
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
