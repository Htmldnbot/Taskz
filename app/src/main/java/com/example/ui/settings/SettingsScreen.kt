package com.example.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.NotificationPreferences
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AuthorizationContext
import com.example.core.model.UpdateStatusState
import com.example.core.model.UserProfile
import com.example.core.update.AppUpdateManager
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingSegmentedTabs
import com.example.ui.common.TaskzAmbientBackground
import com.example.ui.common.TaskzGlassSurface
import com.example.ui.common.TaskzHaptics
import com.example.ui.common.TaskzSettingsRow
import com.example.ui.common.TaskzStatusPill
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    authContext: AuthorizationContext,
    userPrefs: UserPreferencesDataStore,
    appUpdateManager: AppUpdateManager,
    onOpenProfile: () -> Unit,
    onOpenAlarmReliability: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenContactAdmin: (SupportCategory) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    onOpenTermsOfUse: () -> Unit,
    onOpenDataAndPermissions: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val colors = NothingTheme.colors

    val themeMode by userPrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
    val notifPrefs by userPrefs.notificationPreferencesFlow.collectAsStateWithLifecycle(initialValue = NotificationPreferences())
    val updateStatus by appUpdateManager.updateStatus.collectAsStateWithLifecycle()

    var showSignOutConfirm by remember { mutableStateOf(false) }

    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val updateBadge = when (updateStatus) {
        is UpdateStatusState.MandatoryRequired -> "Critical"
        is UpdateStatusState.OptionalAvailable -> "Update"
        else -> null
    }
    val updateBadgeColor = when (updateStatus) {
        is UpdateStatusState.MandatoryRequired -> colors.red
        is UpdateStatusState.OptionalAvailable -> colors.green
        else -> colors.textSecondary
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = colors.surface,
            title = { Text("Sign Out") },
            text = { Text("Sign out of Taskz on this device? Local sync caches will be cleared securely.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.red,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    TaskzAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("settings_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        TaskzHaptics.tap(view)
                        onBack()
                    },
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                NothingDotMatrixText(
                    text = "SETTINGS",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Appearance
                    NothingSectionHeader(title = "APPEARANCE")
                    TaskzGlassSurface {
                        NothingSegmentedTabs(
                            options = AppThemeMode.entries.map { it.label },
                            selectedIndex = AppThemeMode.entries.indexOf(themeMode),
                            onSelect = { idx ->
                                TaskzHaptics.tap(view)
                                scope.launch {
                                    userPrefs.saveThemeMode(AppThemeMode.entries[idx])
                                }
                            },
                            tagPrefix = "settings_theme_tab"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TaskzGlassSurface(
                            elevated = true,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "THEME PREVIEW • ${if (colors.isDark) "DARK" else "LIGHT"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                        color = colors.textSecondary
                                    )
                                    Text(
                                        text = "Restrained Nothing OS Glass",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                TaskzStatusPill(
                                    label = themeMode.label,
                                    accentColor = if (colors.isDark) colors.green else colors.amber
                                )
                            }
                        }
                    }

                    // 2. Account
                    NothingSectionHeader(title = "ACCOUNT")
                    TaskzGlassSurface(contentPadding = PaddingValues(8.dp)) {
                        TaskzSettingsRow(
                            title = profile?.fullName?.ifBlank { "Account Profile" } ?: "Account Profile",
                            subtitle = listOfNotNull(
                                profile?.email?.takeIf { it.isNotBlank() } ?: "Email not linked",
                                profile?.employeeId?.takeIf { it.isNotBlank() }?.let { "ID: $it" }
                            ).joinToString(" • "),
                            icon = Icons.Default.Person,
                            trailingBadge = authContext.activeRole.displayName,
                            onClick = onOpenProfile,
                            testTag = "settings_account_row"
                        )
                    }

                    // 3. Notifications
                    NothingSectionHeader(title = "NOTIFICATIONS & ALARMS")
                    TaskzGlassSurface {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OS Notification Permission",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            TaskzStatusPill(
                                label = if (notificationsGranted) "Allowed" else "Denied",
                                accentColor = if (notificationsGranted) colors.green else colors.red
                            )
                        }
                        if (!notificationsGranted) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Text("Open Android Notification Settings")
                            }
                        }
                        NothingDivider(modifier = Modifier.padding(vertical = 8.dp))
                        NotificationToggleItem(
                            title = "Task Alerts",
                            subtitle = "Assignments, reminders, and shift updates",
                            checked = notifPrefs.taskAlertsEnabled,
                            onCheckedChange = { enabled ->
                                TaskzHaptics.tap(view)
                                scope.launch { userPrefs.updateNotificationPreferences(taskAlertsEnabled = enabled) }
                            },
                            testTag = "toggle_task_alerts"
                        )
                        NotificationToggleItem(
                            title = "Incident Alerts",
                            subtitle = "New incidents and hierarchy escalations",
                            checked = notifPrefs.incidentAlertsEnabled,
                            onCheckedChange = { enabled ->
                                TaskzHaptics.tap(view)
                                scope.launch { userPrefs.updateNotificationPreferences(incidentAlertsEnabled = enabled) }
                            },
                            testTag = "toggle_incident_alerts"
                        )
                        NotificationToggleItem(
                            title = "Alert Sound",
                            subtitle = "Play notification sound for push alerts",
                            checked = notifPrefs.soundEnabled,
                            onCheckedChange = { enabled ->
                                TaskzHaptics.tap(view)
                                scope.launch { userPrefs.updateNotificationPreferences(soundEnabled = enabled) }
                            },
                            testTag = "toggle_alert_sound"
                        )
                        NotificationToggleItem(
                            title = "Vibration",
                            subtitle = "Vibrate when receiving task/incident tray alerts",
                            checked = notifPrefs.vibrationEnabled,
                            onCheckedChange = { enabled ->
                                TaskzHaptics.tap(view)
                                scope.launch { userPrefs.updateNotificationPreferences(vibrationEnabled = enabled) }
                            },
                            testTag = "toggle_alert_vibration"
                        )
                        NothingDivider(modifier = Modifier.padding(vertical = 6.dp))
                        TaskzSettingsRow(
                            title = "Alarm & Reminder Reliability",
                            subtitle = "Exact alarms & battery optimization for shift reminders",
                            icon = Icons.Default.Alarm,
                            onClick = onOpenAlarmReliability,
                            testTag = "settings_alarm_reliability_row"
                        )
                    }

                    // 4. Updates
                    NothingSectionHeader(title = "UPDATES")
                    TaskzGlassSurface(contentPadding = PaddingValues(8.dp)) {
                        TaskzSettingsRow(
                            title = "App Updates",
                            subtitle = "Installed v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            icon = Icons.Default.SystemUpdate,
                            trailingBadge = updateBadge,
                            trailingBadgeColor = updateBadgeColor,
                            onClick = onOpenUpdates,
                            testTag = "settings_updates_row"
                        )
                    }

                    // 5. Privacy & Security
                    NothingSectionHeader(title = "PRIVACY & SECURITY")
                    TaskzGlassSurface(contentPadding = PaddingValues(8.dp)) {
                        TaskzSettingsRow(
                            title = "Privacy Policy",
                            subtitle = "How Taskz handles account, operational, and device data",
                            icon = Icons.Default.PrivacyTip,
                            onClick = onOpenPrivacyPolicy,
                            testTag = "settings_privacy_policy_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "Disclaimer",
                            subtitle = "Operational tool boundaries and venue responsibility",
                            icon = Icons.Default.Policy,
                            onClick = onOpenDisclaimer,
                            testTag = "settings_disclaimer_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "Terms of Use",
                            subtitle = "Authorized personnel obligations & usage terms",
                            icon = Icons.Default.Gavel,
                            onClick = onOpenTermsOfUse,
                            testTag = "settings_terms_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "Data & Permissions",
                            subtitle = "Live Android permission and sync status overview",
                            icon = Icons.Default.Shield,
                            onClick = onOpenDataAndPermissions,
                            testTag = "settings_data_permissions_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "Open Source Licenses",
                            subtitle = "Android, Jetpack, OkHttp, Coil & Firebase attributions",
                            icon = Icons.Default.Description,
                            onClick = onOpenLicenses,
                            testTag = "settings_licenses_row"
                        )
                    }

                    // 6. Help & Support
                    NothingSectionHeader(title = "HELP & SUPPORT")
                    TaskzGlassSurface(contentPadding = PaddingValues(8.dp)) {
                        TaskzSettingsRow(
                            title = "Contact Administrator",
                            subtitle = ADMIN_SUPPORT_EMAIL,
                            icon = Icons.Default.SupportAgent,
                            onClick = { onOpenContactAdmin(SupportCategory.GENERAL_SUPPORT) },
                            testTag = "settings_contact_admin_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "Report a Bug",
                            subtitle = "Send bug details & diagnostics to $ADMIN_SUPPORT_EMAIL",
                            icon = Icons.Default.BugReport,
                            onClick = { onOpenContactAdmin(SupportCategory.BUG_REPORT) },
                            testTag = "settings_report_bug_row"
                        )
                        NothingDivider(modifier = Modifier.padding(horizontal = 10.dp))
                        TaskzSettingsRow(
                            title = "App Info",
                            subtitle = "Version, build code, and package diagnostics",
                            icon = Icons.Default.Info,
                            onClick = onOpenAppInfo,
                            testTag = "settings_app_info_row"
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            TaskzHaptics.warning(view)
                            showSignOutConfirm = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.red.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTheme.colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
