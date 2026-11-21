package com.example.core.model

import org.json.JSONObject

data class AppReleaseInfo(
    val id: String = "",
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String = "",
    val releaseDate: String = "",
    val minimumSupportedVersion: String? = null,
    val minimumSupportedVersionCode: Int? = null,
    val sha256Checksum: String? = null,
    val isLatest: Boolean = true,
    val isCritical: Boolean = false,
    val isActive: Boolean = true,
    val forceUpdate: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun requiresMandatoryUpdate(installedVersionCode: Int): Boolean {
        if (!isActive) return false
        val belowMinSupported = minimumSupportedVersionCode != null && installedVersionCode < minimumSupportedVersionCode
        val hasNewerBuild = versionCode > installedVersionCode
        return belowMinSupported || (hasNewerBuild && (isCritical || forceUpdate))
    }

    fun toJsonString(): String = JSONObject().apply {
        put("id", id)
        put("version_name", versionName)
        put("version_code", versionCode)
        put("download_url", downloadUrl)
        put("release_notes", releaseNotes)
        put("release_date", releaseDate)
        if (!minimumSupportedVersion.isNullOrBlank()) put("minimum_supported_version", minimumSupportedVersion)
        if (minimumSupportedVersionCode != null) put("minimum_supported_version_code", minimumSupportedVersionCode)
        if (!sha256Checksum.isNullOrBlank()) put("sha256_checksum", sha256Checksum)
        put("is_latest", isLatest)
        put("is_critical", isCritical)
        put("is_active", isActive)
        put("force_update", forceUpdate)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }.toString()

    companion object {
        fun fromJson(obj: JSONObject): AppReleaseInfo? {
            val verCode = obj.optInt("version_code", 0)
            val verName = obj.optString("version_name").trim()
            val url = obj.optString("download_url").trim()
            if (verCode <= 0 || verName.isBlank() || url.isBlank()) return null
            val minCodeRaw = if (obj.isNull("minimum_supported_version_code")) 0 else obj.optInt("minimum_supported_version_code", 0)
            return AppReleaseInfo(
                id = obj.optString("id"),
                versionName = verName,
                versionCode = verCode,
                downloadUrl = url,
                releaseNotes = obj.optString("release_notes").takeIf { !it.equals("null", true) }.orEmpty(),
                releaseDate = obj.optString("release_date").takeIf { !it.equals("null", true) }.orEmpty(),
                minimumSupportedVersion = obj.optString("minimum_supported_version").takeIf { it.isNotBlank() && !it.equals("null", true) },
                minimumSupportedVersionCode = minCodeRaw.takeIf { it > 0 },
                sha256Checksum = obj.optString("sha256_checksum").takeIf { it.isNotBlank() && !it.equals("null", true) },
                isLatest = obj.optBoolean("is_latest", true),
                isCritical = obj.optBoolean("is_critical", false),
                isActive = obj.optBoolean("is_active", true),
                forceUpdate = obj.optBoolean("force_update", false),
                createdAt = obj.optString("created_at").takeIf { !it.equals("null", true) }.orEmpty(),
                updatedAt = obj.optString("updated_at").takeIf { !it.equals("null", true) }.orEmpty()
            )
        }

        fun fromJsonString(raw: String?): AppReleaseInfo? {
            if (raw.isNullOrBlank()) return null
            return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
        }
    }
}

object VersionComparator {
    /**
     * Authoritative update comparison uses numeric versionCode exclusively,
     * never lexicographical versionName string comparison.
     */
    fun isUpdateAvailable(installedVersionCode: Int, release: AppReleaseInfo?): Boolean {
        if (release == null || !release.isActive) return false
        return release.versionCode > installedVersionCode
    }

    fun isMandatoryUpdate(installedVersionCode: Int, release: AppReleaseInfo?): Boolean {
        if (release == null || !release.isActive) return false
        return release.requiresMandatoryUpdate(installedVersionCode)
    }

    fun isCheckDue(lastCheckedAtMs: Long, nowMs: Long = System.currentTimeMillis(), intervalMs: Long = DEFAULT_CHECK_INTERVAL_MS): Boolean {
        if (lastCheckedAtMs <= 0L) return true
        return (nowMs - lastCheckedAtMs) >= intervalMs
    }

    const val DEFAULT_CHECK_INTERVAL_MS: Long = 48L * 60L * 60L * 1000L // 48 hours
}

sealed interface UpdateStatusState {
    object UpToDate : UpdateStatusState
    object Checking : UpdateStatusState
    data class OptionalAvailable(val release: AppReleaseInfo) : UpdateStatusState
    data class MandatoryRequired(val release: AppReleaseInfo) : UpdateStatusState
    data class OfflineCached(val cachedRelease: AppReleaseInfo?, val message: String) : UpdateStatusState
    data class Error(val message: String, val cachedRelease: AppReleaseInfo? = null) : UpdateStatusState
}

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState
    object Verifying : UpdateDownloadState
    data class ReadyToInstall(val apkFilePath: String, val release: AppReleaseInfo) : UpdateDownloadState
    data class PermissionRequired(val apkFilePath: String, val release: AppReleaseInfo) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}
