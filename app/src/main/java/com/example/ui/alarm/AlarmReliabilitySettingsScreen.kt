package com.example.ui.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.TaskzApplication
import com.example.sync.SyncWorker
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingStatusDot
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmReliabilitySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var notificationsEnabled by remember { mutableStateOf(false) }
    var exactAlarmGranted by remember { mutableStateOf(false) }
    var fullScreenIntentGranted by remember { mutableStateOf(false) }
    var batteryOptimizationIgnored by remember { mutableStateOf(false) }
    var firebaseConfigured by remember { mutableStateOf(false) }
    var hasFcmToken by remember { mutableStateOf(false) }
    var pushSyncStatusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshStates() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        fullScreenIntentGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        firebaseConfigured = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
        hasFcmToken = runCatching {
            TaskzApplication.instance.deviceTokenManager.getRegisteredPushToken() != null
        }.getOrDefault(false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshStates()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = NothingTheme.colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NothingTheme.colors.background),
                title = {
                    NothingDotMatrixText(
                        text = "ALARM RELIABILITY",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NothingHairlineCard(
                containerColor = NothingTheme.colors.surfaceElevated,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Time-Critical Examination Alarms",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Verify each permission below to guarantee scheduled and immediate exam alarms trigger reliably.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }
            }

            PermissionStatusCard(
                title = "Notification Permission",
                description = "Required for reminders, action banners, and sound alerts.",
                isGranted = notificationsEnabled,
                grantedText = "ENABLED",
                notGrantedText = "DISABLED",
                icon = Icons.Default.Notifications,
                actionLabel = "Open Notification Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            PermissionStatusCard(
                title = "Exact Alarm Permission",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Triggers alarms at the exact second scheduled, unaffected by Doze mode."
                } else {
                    "Exact alarms are automatically granted on this Android OS version."
                },
                isGranted = exactAlarmGranted,
                grantedText = "GRANTED",
                notGrantedText = "MISSING",
                icon = Icons.Default.Alarm,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Open Exact Alarm Settings" else null,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            PermissionStatusCard(
                title = "Full-Screen Intent",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    "Wakes the screen and shows urgent examination alerts over the lock screen."
                } else {
                    "Managed by standard system priority on this Android version."
                },
                isGranted = fullScreenIntentGranted,
                grantedText = "ALLOWED",
                notGrantedText = "RESTRICTED",
                icon = Icons.Default.Fullscreen,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "Manage Full-Screen Intent" else null,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            PermissionStatusCard(
                title = "Battery Optimization",
                description = "Exempting Taskz prevents OEM background process killing during shifts.",
                isGranted = batteryOptimizationIgnored,
                grantedText = "UNRESTRICTED",
                notGrantedText = "OPTIMIZED",
                icon = Icons.Default.BatteryChargingFull,
                actionLabel = "Open Battery Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            )

            PermissionStatusCard(
                title = "Push & Background Sync",
                description = when {
                    !firebaseConfigured -> "Firebase google-services.json is missing from this build. Set GOOGLE_SERVICES_JSON for real-time FCM push wakeups."
                    !hasFcmToken -> "Firebase is active, but device push token is not registered yet."
                    else -> "FCM push token registered for closed-app sync with 15m WorkManager fallback."
                } + (pushSyncStatusMessage?.let { "\nStatus: $it" } ?: ""),
                isGranted = firebaseConfigured && hasFcmToken,
                grantedText = "READY",
                notGrantedText = if (firebaseConfigured) "PENDING" else "NO FCM",
                icon = Icons.Default.Sync,
                actionLabel = "Refresh Push Token & Sync",
                onAction = {
                    coroutineScope.launch {
                        pushSyncStatusMessage = "Registering push token & syncing..."
                        val app = runCatching { TaskzApplication.instance }.getOrNull()
                        if (app == null) {
                            pushSyncStatusMessage = "Application context unavailable"
                            return@launch
                        }
                        runCatching { SyncWorker.schedulePeriodicSync(context) }
                        val tokenRes = app.deviceTokenManager.fetchAndRegisterFcmTokenIfAvailable()
                        val syncRes = app.taskzSyncManager.syncNow(forceFull = false)
                        refreshStates()
                        pushSyncStatusMessage = when {
                            !firebaseConfigured -> "Build missing google-services.json; sync ${if (syncRes.isSuccess) "succeeded" else "deferred"}"
                            tokenRes.isSuccess && hasFcmToken && syncRes.isSuccess -> "FCM token registered & tasks synced"
                            tokenRes.isFailure -> "Token registration error: ${tokenRes.exceptionOrNull()?.message}"
                            syncRes.isFailure -> "Token ready; sync warning: ${syncRes.exceptionOrNull()?.message}"
                            else -> "FCM token pending from Google Play Services; retry shortly"
                        }
                    }
                }
            )

            NothingHairlineCard(
                containerColor = NothingTheme.colors.surfaceElevated,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "RTC_WAKEUP ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Taskz uses native Android AlarmManager with RTC_WAKEUP alongside battery exemption for deterministic shift alert delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    grantedText: String,
    notGrantedText: String,
    icon: ImageVector,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isGranted) NothingGreen else NothingRed
    NothingHairlineCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NothingTheme.colors.surfaceElevated)
                        .border(1.dp, NothingTheme.colors.border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingStatusDot(color = statusColor, size = 6.dp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isGranted) grantedText else notGrantedText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = NothingTheme.colors.textSecondary
        )

        if (actionLabel != null && !isGranted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = actionLabel, fontWeight = FontWeight.Bold)
            }
        } else if (actionLabel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, NothingTheme.colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = actionLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
