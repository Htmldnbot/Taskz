package com.example.ui.settings

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AppReleaseInfo
import com.example.core.model.UpdateDownloadState
import com.example.core.model.UpdateStatusState
import com.example.core.model.VersionComparator
import com.example.core.update.AppUpdateManager
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.TaskzAmbientBackground
import com.example.ui.common.TaskzGlassSurface
import com.example.ui.common.TaskzHaptics
import com.example.ui.common.TaskzStatusPill
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

@Composable
fun UpdatesScreen(
    appUpdateManager: AppUpdateManager,
    isOnline: Boolean,
    onContactAdmin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = NothingTheme.colors

    val updateStatus by appUpdateManager.updateStatus.collectAsStateWithLifecycle()
    val downloadState by appUpdateManager.downloadState.collectAsStateWithLifecycle()
    val latestRelease by appUpdateManager.latestRelease.collectAsStateWithLifecycle()
    val lastCheckedAtMs by appUpdateManager.lastCheckedAtMs.collectAsStateWithLifecycle()

    val lastCheckedLabel = if (lastCheckedAtMs > 0L) {
        DateUtils.getRelativeTimeSpanString(
            lastCheckedAtMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } else {
        "Not checked yet"
    }

    val activeRelease: AppReleaseInfo? = when (val st = updateStatus) {
        is UpdateStatusState.OptionalAvailable -> st.release
        is UpdateStatusState.MandatoryRequired -> st.release
        is UpdateStatusState.OfflineCached -> st.cachedRelease ?: latestRelease
        is UpdateStatusState.Error -> st.cachedRelease ?: latestRelease
        else -> latestRelease
    }

    val hasNewerVersion = VersionComparator.isUpdateAvailable(
        installedVersionCode = appUpdateManager.installedVersionCode,
        release = activeRelease
    )
    val isMandatory = VersionComparator.isMandatoryUpdate(
        installedVersionCode = appUpdateManager.installedVersionCode,
        release = activeRelease
    )

    TaskzAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("updates_screen")
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
                    modifier = Modifier.testTag("updates_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                NothingDotMatrixText(
                    text = "APP UPDATES",
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
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskzGlassSurface {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "INSTALLED BUILD",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "v${appUpdateManager.installedVersionName} (${appUpdateManager.installedVersionCode})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            when {
                                updateStatus is UpdateStatusState.Checking -> {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                                isMandatory -> TaskzStatusPill("Mandatory", colors.red)
                                hasNewerVersion -> TaskzStatusPill("Update Ready", colors.amber)
                                else -> TaskzStatusPill("Up to Date", colors.green)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Automatic check runs every 48 hours • Last checked: $lastCheckedLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                        if (!isOnline) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Offline mode — local cached release metadata shown.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.amber
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                TaskzHaptics.tap(view)
                                appUpdateManager.forceCheckNow()
                            },
                            enabled = updateStatus !is UpdateStatusState.Checking,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("check_updates_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (updateStatus is UpdateStatusState.Checking) "Checking..." else "Check for Updates Now")
                        }
                    }

                    if (updateStatus is UpdateStatusState.Error) {
                        TaskzGlassSurface(elevated = true) {
                            Text(
                                text = (updateStatus as UpdateStatusState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.red
                            )
                        }
                    }

                    NothingSectionHeader(title = "LATEST RELEASE")

                    if (activeRelease == null) {
                        TaskzGlassSurface {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Up to date",
                                    tint = colors.green,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "You are on the latest published build or no server release has been posted yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        TaskzGlassSurface(elevated = hasNewerVersion) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Taskz v${activeRelease.versionName}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Build ${activeRelease.versionCode}" +
                                            (activeRelease.releaseDate.takeIf { it.isNotBlank() }?.let { " • ${it.take(10)}" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                        color = colors.textSecondary
                                    )
                                }
                                if (isMandatory) {
                                    TaskzStatusPill("Critical", colors.red)
                                } else if (hasNewerVersion) {
                                    TaskzStatusPill("New", colors.green)
                                }
                            }

                            if (activeRelease.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = activeRelease.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (!activeRelease.sha256Checksum.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "SHA-256: ${activeRelease.sha256Checksum.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            UpdateDownloadActionBlock(
                                release = activeRelease,
                                hasNewerVersion = hasNewerVersion,
                                downloadState = downloadState,
                                onDownload = { appUpdateManager.downloadAndInstallUpdate(activeRelease) },
                                onInstallReady = {
                                    appUpdateManager.resumeInstallAfterPermissionGranted()
                                },
                                onRequestUnknownSources = {
                                    runCatching {
                                        context.startActivity(appUpdateManager.createUnknownSourcesSettingsIntent())
                                    }
                                }
                            )
                        }
                    }

                    TaskzGlassSurface(onClick = onContactAdmin) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "Contact Administrator",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Need help updating?",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Contact Administrator ($ADMIN_SUPPORT_EMAIL)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDownloadActionBlock(
    release: AppReleaseInfo,
    hasNewerVersion: Boolean,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onInstallReady: () -> Unit,
    onRequestUnknownSources: () -> Unit
) {
    val view = LocalView.current
    val colors = NothingTheme.colors

    when (downloadState) {
        is UpdateDownloadState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                val mbDown = downloadState.downloadedBytes / (1024f * 1024f)
                val mbTotal = if (downloadState.totalBytes > 0L) downloadState.totalBytes / (1024f * 1024f) else 0f
                val progressText = if (mbTotal > 0f) {
                    "Downloading ${downloadState.progressPercent}% (%.1f / %.1f MB)".format(mbDown, mbTotal)
                } else {
                    "Downloading %.1f MB...".format(mbDown)
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = colors.textSecondary
                )
            }
        }

        is UpdateDownloadState.Verifying -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Verifying APK integrity checksum...",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = colors.textSecondary
                )
            }
        }

        is UpdateDownloadState.PermissionRequired -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Allow Taskz to install app updates in Android Settings, then tap Install APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.amber
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            TaskzHaptics.tap(view)
                            onRequestUnknownSources()
                        },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Allow Installs")
                    }
                    OutlinedButton(
                        onClick = {
                            TaskzHaptics.confirm(view)
                            onInstallReady()
                        },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Install APK")
                    }
                }
            }
        }

        is UpdateDownloadState.ReadyToInstall -> {
            Button(
                onClick = {
                    TaskzHaptics.confirm(view)
                    onInstallReady()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Install Taskz v${downloadState.release.versionName}")
            }
        }

        is UpdateDownloadState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.red
                )
                Button(
                    onClick = {
                        TaskzHaptics.tap(view)
                        onDownload()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Retry Download")
                }
            }
        }

        UpdateDownloadState.Idle -> {
            if (hasNewerVersion) {
                Button(
                    onClick = {
                        TaskzHaptics.confirm(view)
                        onDownload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPrimary,
                        contentColor = colors.onAccentPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("download_update_button")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download & Install Update")
                }
            } else {
                Text(
                    text = "Installed version matches or exceeds this release.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun OptionalUpdateBanner(
    release: AppReleaseInfo,
    onOpenUpdates: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NothingTheme.colors
    TaskzGlassSurface(
        elevated = true,
        modifier = modifier.testTag("optional_update_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Taskz update available",
                    tint = colors.green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Taskz update available (v${release.versionName})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (release.releaseNotes.isNotBlank()) {
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("optional_update_later_button")
            ) {
                Text("Later")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOpenUpdates,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.onAccentPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("optional_update_download_button")
            ) {
                Text("Download update")
            }
        }
    }
}

@Composable
fun MandatoryUpdateGateScreen(
    release: AppReleaseInfo,
    appUpdateManager: AppUpdateManager,
    onContactAdmin: () -> Unit
) {
    val context = LocalContext.current
    val downloadState by appUpdateManager.downloadState.collectAsStateWithLifecycle()
    val colors = NothingTheme.colors

    TaskzAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TaskzGlassSurface(
                elevated = true,
                modifier = Modifier.widthIn(max = 520.dp).testTag("mandatory_update_gate")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SecurityUpdateWarning,
                        contentDescription = "Critical Update Required",
                        tint = colors.red,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        NothingDotMatrixText(
                            text = "CRITICAL UPDATE REQUIRED",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Installed v${appUpdateManager.installedVersionName} • Required v${release.versionName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = colors.textSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = release.releaseNotes.ifBlank {
                        "A critical Taskz release is required before continuing online operations."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                UpdateDownloadActionBlock(
                    release = release,
                    hasNewerVersion = true,
                    downloadState = downloadState,
                    onDownload = { appUpdateManager.downloadAndInstallUpdate(release) },
                    onInstallReady = { appUpdateManager.resumeInstallAfterPermissionGranted() },
                    onRequestUnknownSources = {
                        runCatching {
                            context.startActivity(appUpdateManager.createUnknownSourcesSettingsIntent())
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { appUpdateManager.forceCheckNow() }) {
                        Text("Retry Check")
                    }
                    TextButton(onClick = onContactAdmin) {
                        Text("Contact Admin ($ADMIN_SUPPORT_EMAIL)")
                    }
                }
            }
        }
    }
}
