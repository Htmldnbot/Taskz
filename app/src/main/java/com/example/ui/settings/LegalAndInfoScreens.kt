package com.example.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.TaskzAmbientBackground
import com.example.ui.common.TaskzGlassSurface
import com.example.ui.common.TaskzHaptics
import com.example.ui.common.TaskzStatusPill
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

@Composable
private fun LegalDocumentShell(
    title: String,
    screenTag: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    TaskzAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag(screenTag)
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
                    modifier = Modifier.testTag("${screenTag}_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                NothingDotMatrixText(
                    text = title.uppercase(),
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
                    content()
                }
            }
        }
    }
}

@Composable
private fun LegalSectionCard(heading: String, body: String) {
    TaskzGlassSurface {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = NothingTheme.colors.textSecondary
        )
    }
}

@Composable
fun PrivacyPolicyScreen(
    onContactAdmin: () -> Unit,
    onBack: () -> Unit
) {
    LegalDocumentShell(
        title = "Privacy Policy",
        screenTag = "privacy_policy_screen",
        onBack = onBack
    ) {
        LegalSectionCard(
            heading = "1. Account & Identity Information",
            body = "Taskz stores your Employee ID, Full Name, Mobile Number, optional Email address, optional profile photo, active role, and assigned organizational location (Zone, State, City, Centre, and LAN) to authorize operational workflows."
        )
        LegalSectionCard(
            heading = "2. Operational Data & Sync",
            body = "Tasks, exam shifts, group memberships, task checklist responses, incident reports, and escalation audit events are synchronized with the organization's Supabase PostgreSQL backend and cached locally in an on-device Room database for offline availability."
        )
        LegalSectionCard(
            heading = "3. Authentication & Push Messaging",
            body = "Sign-in uses Supabase Auth (Email/Password, Employee ID + Password, or Google Sign-In via Android Credential Manager). Device notification tokens are registered with Firebase Cloud Messaging and stored per device to deliver task and incident alerts."
        )
        LegalSectionCard(
            heading = "4. Device Permissions",
            body = "Taskz may use Internet/Network state (for sync and update checks), Post Notifications (tray alerts), Exact Alarms (shift/task reminders), Photo Picker / optional Camera (profile photo upload), and Request Install Packages (for verified in-app APK updates)."
        )
        LegalSectionCard(
            heading = "5. Data Retention & Privacy Inquiries",
            body = "Local device caches are cleared automatically on sign-out. Server-side retention periods depend on your organization's Supabase project configuration (exact server retention schedule is not defined in the mobile client). For data access, correction, or privacy requests, contact $ADMIN_SUPPORT_EMAIL."
        )
        ContactSupportFooterButton(onContactAdmin)
    }
}

@Composable
fun DisclaimerScreen(
    onContactAdmin: () -> Unit,
    onBack: () -> Unit
) {
    LegalDocumentShell(
        title = "Disclaimer",
        screenTag = "disclaimer_screen",
        onBack = onBack
    ) {
        LegalSectionCard(
            heading = "Operational Coordination Aid",
            body = "Taskz is an internal workflow, shift checklist, and incident escalation tool intended to assist examination centre and operations staff."
        )
        LegalSectionCard(
            heading = "Venue Verification Responsibility",
            body = "Task completion states, timestamps, and alarm reminders assist operational tracking but do not replace physical verification or official examination protocols mandated by venue supervisors and operations leadership."
        )
        LegalSectionCard(
            heading = "Network & Device Conditions",
            body = "Offline actions are queued locally and synchronized once network connectivity returns. Exact alarm delivery can be affected by aggressive OS battery restrictions if exemption settings are disabled."
        )
        ContactSupportFooterButton(onContactAdmin)
    }
}

@Composable
fun TermsOfUseScreen(
    onContactAdmin: () -> Unit,
    onBack: () -> Unit
) {
    LegalDocumentShell(
        title = "Terms of Use",
        screenTag = "terms_of_use_screen",
        onBack = onBack
    ) {
        LegalSectionCard(
            heading = "1. Authorized Access Only",
            body = "Use of Taskz is restricted to authorized personnel assigned valid organizational roles and organizational scope. Accounts may be activated, updated, or deactivated by system administrators."
        )
        LegalSectionCard(
            heading = "2. Account & Role Conduct",
            body = "You are responsible for safeguarding your login credentials and reporting accurate operational checklist outcomes, task updates, and incidents within your authorized scope."
        )
        LegalSectionCard(
            heading = "3. Mandatory Security Updates",
            body = "To protect operational integrity, critical app releases marked mandatory by administrators must be installed before continuing online operations."
        )
        ContactSupportFooterButton(onContactAdmin)
    }
}

@Composable
fun DataAndPermissionsScreen(
    isOnline: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = NothingTheme.colors

    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() == true
    } else true

    val unknownSourcesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else true

    LegalDocumentShell(
        title = "Data & Permissions",
        screenTag = "data_permissions_screen",
        onBack = onBack
    ) {
        NothingSectionHeader(title = "RUNTIME PERMISSION STATUS")
        PermissionStatusCard(
            title = "Notifications (POST_NOTIFICATIONS)",
            detail = "Used for task updates, incident escalations, and shift alarm notifications.",
            granted = notifGranted
        )
        PermissionStatusCard(
            title = "Exact Alarms",
            detail = "Used so task deadline reminders fire accurately on schedule.",
            granted = exactAlarmGranted
        )
        PermissionStatusCard(
            title = "Install Unknown Apps",
            detail = "Allows Taskz to launch Android's package installer for downloaded APK updates.",
            granted = unknownSourcesGranted
        )
        PermissionStatusCard(
            title = "Network Connectivity",
            detail = "Current connection status used for Supabase sync & release checks.",
            granted = isOnline,
            grantedLabel = "Online",
            deniedLabel = "Offline"
        )
        OutlinedButton(
            onClick = {
                TaskzHaptics.tap(view)
                runCatching {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Manage Permissions in Android Settings")
        }
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    detail: String,
    granted: Boolean,
    grantedLabel: String = "Granted",
    deniedLabel: String = "Not Granted"
) {
    val colors = NothingTheme.colors
    TaskzGlassSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            TaskzStatusPill(
                label = if (granted) grantedLabel else deniedLabel,
                accentColor = if (granted) colors.green else colors.amber
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit
) {
    val libs = listOf(
        "AndroidX Core / Lifecycle / Activity / WorkManager" to "Apache License 2.0",
        "Jetpack Compose UI, Material 3 & Navigation Compose" to "Apache License 2.0",
        "AndroidX Room & DataStore Preferences" to "Apache License 2.0",
        "AndroidX Credentials & Google ID Identity" to "Apache License 2.0",
        "Kotlin Standard Library, Coroutines & Serialization" to "Apache License 2.0",
        "OkHttp" to "Apache License 2.0",
        "Coil Compose" to "Apache License 2.0",
        "Firebase Messaging" to "Apache License 2.0"
    )
    LegalDocumentShell(
        title = "Open Source Licenses",
        screenTag = "open_source_licenses_screen",
        onBack = onBack
    ) {
        NothingSectionHeader(title = "LIBRARIES USED BY TASKZ")
        libs.forEach { (name, license) ->
            TaskzGlassSurface {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = license,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun AppInfoScreen(
    onOpenUpdates: () -> Unit,
    onContactAdmin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = NothingTheme.colors

    val infoRows = listOf(
        "Application Name" to "Taskz",
        "Package ID" to BuildConfig.APPLICATION_ID,
        "Version Name" to BuildConfig.VERSION_NAME,
        "Version Code" to BuildConfig.VERSION_CODE.toString(),
        "Build Type" to BuildConfig.BUILD_TYPE.uppercase(),
        "Android OS" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Support Email" to ADMIN_SUPPORT_EMAIL
    )

    LegalDocumentShell(
        title = "App Info",
        screenTag = "app_info_screen",
        onBack = onBack
    ) {
        TaskzGlassSurface {
            infoRows.forEachIndexed { idx, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                TaskzHaptics.tap(view)
                val summary = infoRows.joinToString("\n") { "${it.first}: ${it.second}" }
                copyToClipboard(context, "App Info", summary)
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy Build & Device Info")
        }

        Button(
            onClick = onOpenUpdates,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentPrimary,
                contentColor = colors.onAccentPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Check for App Updates")
        }

        ContactSupportFooterButton(onContactAdmin)
    }
}

@Composable
private fun ContactSupportFooterButton(onContactAdmin: () -> Unit) {
    val colors = NothingTheme.colors
    OutlinedButton(
        onClick = onContactAdmin,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Contact Administrator ($ADMIN_SUPPORT_EMAIL)")
    }
}
