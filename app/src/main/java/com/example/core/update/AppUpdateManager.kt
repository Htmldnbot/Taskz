package com.example.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AppReleaseInfo
import com.example.core.model.UpdateDownloadState
import com.example.core.model.UpdateStatusState
import com.example.core.model.VersionComparator
import com.example.core.network.NetworkMonitor
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages automatic (48h cadence) and manual app release checks against Supabase `app_releases`,
 * plus in-app HTTPS APK downloads, optional SHA-256 verification, and FileProvider package installation.
 */
class AppUpdateManager(
    private val context: Context,
    private val userPrefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor,
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TAG = "TaskzAppUpdate"
        private const val UPDATES_DIR = "updates"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()
    private var activeDownloadJob: Job? = null

    private val downloadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val installedVersionName: String = BuildConfig.VERSION_NAME
    val installedVersionCode: Int = BuildConfig.VERSION_CODE

    private val _updateStatus = MutableStateFlow<UpdateStatusState>(UpdateStatusState.UpToDate)
    val updateStatus: StateFlow<UpdateStatusState> = _updateStatus.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    val lastCheckedAtMs: StateFlow<Long> = userPrefs.lastUpdateCheckAtFlow
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val latestRelease: StateFlow<AppReleaseInfo?> = userPrefs.cachedLatestReleaseFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    val dismissedOptionalVersionCode: StateFlow<Int> = userPrefs.dismissedUpdateVersionCodeFlow
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        scope.launch {
            val cached = userPrefs.getCachedLatestRelease()
            if (cached != null) {
                evaluateReleaseState(cached, fromCache = true)
            }
        }
    }

    fun checkIfDue(triggerReason: String) {
        scope.launch {
            val lastChecked = userPrefs.getLastUpdateCheckAt()
            if (!VersionComparator.isCheckDue(lastChecked)) {
                Log.d(TAG, "Skipping update check ($triggerReason); within 48h window")
                val cached = userPrefs.getCachedLatestRelease()
                if (cached != null && _updateStatus.value is UpdateStatusState.UpToDate) {
                    evaluateReleaseState(cached, fromCache = true)
                }
                return@launch
            }
            performCheck(force = false, reason = triggerReason)
        }
    }

    fun forceCheckNow() {
        scope.launch {
            performCheck(force = true, reason = "manual")
        }
    }

    fun dismissOptionalPrompt(versionCode: Int) {
        scope.launch {
            userPrefs.setDismissedUpdateVersionCode(versionCode)
        }
    }

    fun resetDownloadState() {
        if (_downloadState.value !is UpdateDownloadState.Downloading &&
            _downloadState.value !is UpdateDownloadState.Verifying
        ) {
            _downloadState.value = UpdateDownloadState.Idle
        }
    }

    private suspend fun performCheck(force: Boolean, reason: String) {
        checkMutex.withLock {
            val cached = userPrefs.getCachedLatestRelease()
            if (!force) {
                val lastChecked = userPrefs.getLastUpdateCheckAt()
                if (!VersionComparator.isCheckDue(lastChecked)) {
                    if (cached != null) evaluateReleaseState(cached, fromCache = true)
                    return
                }
            }

            if (!networkMonitor.isCurrentlyConnected()) {
                if (force) {
                    _updateStatus.value = UpdateStatusState.OfflineCached(
                        cachedRelease = cached,
                        message = "Offline — showing cached release details."
                    )
                } else if (cached != null) {
                    evaluateReleaseState(cached, fromCache = true)
                }
                return
            }

            if (force) {
                _updateStatus.value = UpdateStatusState.Checking
            }

            val accessToken = runCatching { userPrefs.getUserSession().accessToken }.getOrNull()
            val result = supabaseClient.fetchLatestAppRelease(accessToken)
            result.fold(
                onSuccess = { remote ->
                    val now = System.currentTimeMillis()
                    userPrefs.saveUpdateCheckResult(now, remote)
                    if (remote != null && remote.versionCode > userPrefs.getDismissedUpdateVersionCode()) {
                        // A newer version than previously dismissed automatically clears old dismissal
                        if (userPrefs.getDismissedUpdateVersionCode() != 0) {
                            userPrefs.setDismissedUpdateVersionCode(0)
                        }
                    }
                    evaluateReleaseState(remote, fromCache = false)
                    Log.i(TAG, "Update check ($reason) succeeded: remote=${remote?.versionName}/${remote?.versionCode}")
                },
                onFailure = { err ->
                    Log.w(TAG, "Update check ($reason) failed: ${err.message}")
                    if (force) {
                        _updateStatus.value = UpdateStatusState.Error(
                            message = err.message ?: "Unable to check for updates right now.",
                            cachedRelease = cached
                        )
                    } else if (cached != null) {
                        evaluateReleaseState(cached, fromCache = true)
                    }
                }
            )
        }
    }

    private fun evaluateReleaseState(release: AppReleaseInfo?, fromCache: Boolean) {
        if (release == null || !release.isActive) {
            _updateStatus.value = UpdateStatusState.UpToDate
            return
        }
        val mandatory = VersionComparator.isMandatoryUpdate(installedVersionCode, release)
        val optional = VersionComparator.isUpdateAvailable(installedVersionCode, release)
        _updateStatus.value = when {
            mandatory -> UpdateStatusState.MandatoryRequired(release)
            optional -> UpdateStatusState.OptionalAvailable(release)
            else -> if (fromCache && !networkMonitor.isCurrentlyConnected()) {
                UpdateStatusState.UpToDate
            } else {
                UpdateStatusState.UpToDate
            }
        }
    }

    fun downloadAndInstallUpdate(release: AppReleaseInfo) {
        val url = release.downloadUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            _downloadState.value = UpdateDownloadState.Failed("Invalid download URL: HTTPS is required.")
            return
        }
        if (activeDownloadJob?.isActive == true) return

        activeDownloadJob = scope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(0, 0L, 0L)
            try {
                val updatesDir = File(context.cacheDir, UPDATES_DIR).apply {
                    if (!exists()) mkdirs()
                }
                // Clean stale cached APKs except the target filename
                val targetName = "Taskz-v${release.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
                updatesDir.listFiles()?.forEach { existing ->
                    if (existing.isFile && existing.name != targetName) {
                        runCatching { existing.delete() }
                    }
                }
                val apkFile = File(updatesDir, targetName)
                val tempFile = File(updatesDir, "$targetName.part")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = downloadHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    _downloadState.value = UpdateDownloadState.Failed("Download failed (HTTP ${response.code}).")
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    _downloadState.value = UpdateDownloadState.Failed("Empty response from update server.")
                    return@launch
                }

                val totalBytes = body.contentLength().coerceAtLeast(-1L)
                var downloadedBytes = 0L
                val buffer = ByteArray(16 * 1024)

                response.use {
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            var lastEmittedPct = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                val pct = if (totalBytes > 0) {
                                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (pct != lastEmittedPct || read < buffer.size) {
                                    lastEmittedPct = pct
                                    _downloadState.value = UpdateDownloadState.Downloading(
                                        progressPercent = pct,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.length() <= 0L) {
                    runCatching { tempFile.delete() }
                    _downloadState.value = UpdateDownloadState.Failed("Downloaded file is empty.")
                    return@launch
                }

                val expectedSha = release.sha256Checksum?.trim()?.lowercase()?.takeIf { it.length == 64 }
                if (expectedSha != null) {
                    _downloadState.value = UpdateDownloadState.Verifying
                    val actualSha = computeSha256Hex(tempFile)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        runCatching { tempFile.delete() }
                        _downloadState.value = UpdateDownloadState.Failed(
                            "Package checksum verification failed. Please retry or contact munesh.y@tcs.com."
                        )
                        return@launch
                    }
                }

                if (apkFile.exists()) {
                    apkFile.delete()
                }
                if (!tempFile.renameTo(apkFile)) {
                    tempFile.copyTo(apkFile, overwrite = true)
                    tempFile.delete()
                }

                if (!canInstallUnknownApps()) {
                    _downloadState.value = UpdateDownloadState.PermissionRequired(
                        apkFilePath = apkFile.absolutePath,
                        release = release
                    )
                } else {
                    _downloadState.value = UpdateDownloadState.ReadyToInstall(
                        apkFilePath = apkFile.absolutePath,
                        release = release
                    )
                    launchPackageInstaller(apkFile)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed: ${e.message}")
                _downloadState.value = UpdateDownloadState.Failed(
                    e.message ?: "Unable to download update package."
                )
            }
        }
    }

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    suspend fun launchPackageInstaller(apkFile: File): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            if (!apkFile.exists()) {
                throw IllegalStateException("Update APK file not found on device.")
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }

    fun resumeInstallAfterPermissionGranted() {
        val current = _downloadState.value
        val filePath = when (current) {
            is UpdateDownloadState.PermissionRequired -> current.apkFilePath
            is UpdateDownloadState.ReadyToInstall -> current.apkFilePath
            else -> return
        }
        val release = when (current) {
            is UpdateDownloadState.PermissionRequired -> current.release
            is UpdateDownloadState.ReadyToInstall -> current.release
            else -> return
        }
        val file = File(filePath)
        if (!file.exists()) {
            _downloadState.value = UpdateDownloadState.Idle
            return
        }
        if (canInstallUnknownApps()) {
            _downloadState.value = UpdateDownloadState.ReadyToInstall(filePath, release)
            scope.launch {
                launchPackageInstaller(file).onFailure { err ->
                    _downloadState.value = UpdateDownloadState.Failed(err.message ?: "Unable to launch installer.")
                }
            }
        }
    }

    private fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
