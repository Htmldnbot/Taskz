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
    val syncMetadata by viewModel.syncMetadataFlow.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncErrorMessage by viewModel.syncErrorMessage.collectAsStateWithLifecycle()
    val isAccountInactive by viewModel.isAccountInactive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
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

    LaunchedEffect(profile?.id) {
        val uid = profile?.id
        if (!uid.isNullOrBlank()) {
            val checkin = incidentRepo.getTodayDutyCheckin(uid)
            todayDutyCheckin = checkin
            if (checkin == null) {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                        if (profile?.isAdmin == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                                        text = "Admin",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Synchronization Status Card
        SyncStatusCard(
            syncMetadata = syncMetadata,
            onRefreshClick = {
                if (syncMetadata.pendingSyncCount > 0 || syncMetadata.status == com.example.core.model.SyncStatus.ERROR) {
                    viewModel.retryPendingSync()
                } else {
                    viewModel.triggerManualRefresh()
                }
            },
            onClearFailedClick = {
                viewModel.clearFailedSyncItems()
            },
            externalErrorMessage = syncErrorMessage,
            isRefreshing = isRefreshing
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Real Dashboard Summary Statistics
        Text(
            text = "Operational Overview",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "My Tasks Today",
                value = dashboardStats.myTasksToday.toString(),
                icon = Icons.Default.Today,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
            StatCard(
                title = "Upcoming Tasks",
                value = dashboardStats.upcomingTasks.toString(),
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
                title = "Pending Requests",
                value = dashboardStats.pendingRequests.toString(),
                icon = Icons.Default.HourglassTop,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
            StatCard(
                title = "Vacancies",
                value = dashboardStats.vacancies.toString(),
                icon = Icons.Default.WorkOutline,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToTasks
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Workforce Modules",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        ModuleTile(
            title = "Task Management",
            subtitle = "View your assigned duties, discover available tasks, or manage created tasks",
            icon = Icons.Default.Assignment,
            tag = "Active",
            onClick = onNavigateToTasks,
            testTag = "home_module_tasks"
        )

        Spacer(modifier = Modifier.height(10.dp))

        ModuleTile(
            title = "Groups & Events",
            subtitle = "Organize workforce by event, shifts, and departments",
            icon = Icons.Default.Groups,
            tag = "Events",
            onClick = onNavigateToGroups,
            testTag = "home_module_groups"
        )

        Spacer(modifier = Modifier.height(10.dp))

        ModuleTile(
            title = "System Incident Register",
            subtitle = "Record computer system failures, candidate shifts, and technical exam logs",
            icon = Icons.Default.DesktopWindows,
            tag = "Technical",
            onClick = onNavigateToIncidents,
            testTag = "home_module_incidents"
        )

        Spacer(modifier = Modifier.height(10.dp))

        ModuleTile(
            title = "Employee Profile",
            subtitle = "View credentials, photo, and update contact information",
            icon = Icons.Default.Person,
            tag = "Profile",
            onClick = onNavigateToProfile,
            testTag = "home_module_profile"
        )

        if (profile?.isAdmin == true) {
            Spacer(modifier = Modifier.height(10.dp))
            ModuleTile(
                title = "Administrative Console",
                subtitle = "Manage employees, temporary password resets & configure work roles",
                icon = Icons.Default.AdminPanelSettings,
                tag = "Admin Access",
                isHighlighted = true,
                onClick = onNavigateToAdmin,
                testTag = "home_module_admin"
            )
        }

        if (showDutyCheckinModal) {
            DailyDutyCheckinDialog(
                currentCheckin = todayDutyCheckin,
                onDismiss = { showDutyCheckinModal = false },
                onConfirm = { centreCode, centreName, role, lan ->
                    val user = profile ?: return@DailyDutyCheckinDialog
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
    currentCheckin: DailyDutyCheckin?,
    onDismiss: () -> Unit,
    onConfirm: (centreCode: String, centreName: String, role: String, lan: String?) -> Unit
) {
    val incidentRepo = remember { com.example.TaskzApplication.instance.systemIncidentRepository }
    var availableCentres by remember { mutableStateOf<List<TaskGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableCentres = incidentRepo.getAvailableCentres()
    }

    var selectedCentre by remember(currentCheckin, availableCentres) {
        mutableStateOf(
            availableCentres.firstOrNull { it.code == currentCheckin?.centreCode }
                ?: availableCentres.firstOrNull()
        )
    }

    var centreDropdownExpanded by remember { mutableStateOf(false) }
    var selectedRole by remember(currentCheckin) {
        mutableStateOf(currentCheckin?.role ?: "IT_MANAGER") // "IT_MANAGER" or "VCO"
    }

    val availableLans = remember { (1..25).map { "LAN $it" } }
    var selectedLan by remember(currentCheckin) {
        mutableStateOf(currentCheckin?.lan ?: "LAN 1")
    }
    var lanDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        text = "Examination Station Check-In",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "Report your active examination duty station for today.",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Examination Centre Selection
                Text(
                    text = "1. Which Centre are you stationed at today?",
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
                                Text(
                                    text = selectedCentre?.title ?: "Select Examination Centre",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = selectedCentre?.description ?: "Jaipur, Rajasthan",
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
                        availableCentres.forEach { centre ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(text = centre.title, fontWeight = FontWeight.SemiBold)
                                        Text(text = centre.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedCentre = centre
                                    centreDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Examination Role Selection
                Text(
                    text = "2. Your Examination Duty Role:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == "IT_MANAGER",
                        onClick = { selectedRole = "IT_MANAGER" },
                        label = { Text("IT MANAGER\n(System Operator)", fontSize = 12.sp) },
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

                // 3. If IT Manager: Select Assigned LAN
                if (selectedRole == "IT_MANAGER") {
                    Text(
                        text = "3. Assigned LAN:",
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val centre = selectedCentre ?: availableCentres.firstOrNull()
                    val code = centre?.code?.ifBlank { "141" } ?: "141"
                    val name = centre?.title?.removePrefix("$code ·")?.removePrefix("$code -")?.trim() ?: "Test Centre"
                    val lan = if (selectedRole == "VCO") null else selectedLan
                    onConfirm(code, name, selectedRole, lan)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_daily_checkin_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Station")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
