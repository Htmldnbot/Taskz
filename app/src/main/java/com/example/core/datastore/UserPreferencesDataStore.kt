package com.example.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AppReleaseInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "taskz_app_prefs")

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        fun fromStorage(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class NotificationPreferences(
    val taskAlertsEnabled: Boolean = true,
    val incidentAlertsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true
)

data class UserSession(
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?, // UUID matching auth.users(id) and public.profiles(id)
    val employeeId: String?,
    val mustChangePassword: Boolean
) {
    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()
}

class UserPreferencesDataStore(private val context: Context) {

    private val authPrefs: SharedPreferences =
        context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)

    private val accountHintsPrefs: SharedPreferences =
        context.getSharedPreferences(ACCOUNT_HINTS_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val AUTH_PREFS_NAME = "taskz_auth_session"
        private const val ACCOUNT_HINTS_PREFS_NAME = "taskz_account_hints"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMPLOYEE_ID = "employee_id"
        private const val KEY_MUST_CHANGE_PASSWORD = "must_change_password"

        private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
        private val KEY_CUSTOM_SUPABASE_KEY = stringPreferencesKey("custom_supabase_key")
        private val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode")

        private val KEY_NOTIF_TASKS = booleanPreferencesKey("notif_tasks_enabled")
        private val KEY_NOTIF_INCIDENTS = booleanPreferencesKey("notif_incidents_enabled")
        private val KEY_NOTIF_SOUND = booleanPreferencesKey("notif_sound_enabled")
        private val KEY_NOTIF_VIBRATION = booleanPreferencesKey("notif_vibration_enabled")

        private val KEY_LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")
        private val KEY_CACHED_RELEASE_JSON = stringPreferencesKey("cached_latest_release_json")
        private val KEY_DISMISSED_UPDATE_CODE = intPreferencesKey("dismissed_update_version_code")
    }

    private fun readSession(): UserSession {
        return UserSession(
            accessToken = authPrefs.getString(KEY_ACCESS_TOKEN, null),
            refreshToken = authPrefs.getString(KEY_REFRESH_TOKEN, null),
            userId = authPrefs.getString(KEY_USER_ID, null),
            employeeId = authPrefs.getString(KEY_EMPLOYEE_ID, null),
            mustChangePassword = authPrefs.getBoolean(KEY_MUST_CHANGE_PASSWORD, false)
        )
    }

    val userSessionFlow: Flow<UserSession> = callbackFlow {
        trySend(readSession())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readSession())
        }
        authPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            authPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    suspend fun getUserSession(): UserSession = readSession()

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String?,
        userId: String,
        employeeId: String,
        mustChangePassword: Boolean = false
    ) {
        authPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            } else {
                remove(KEY_REFRESH_TOKEN)
            }
            putString(KEY_USER_ID, userId)
            putString(KEY_EMPLOYEE_ID, employeeId)
            putBoolean(KEY_MUST_CHANGE_PASSWORD, mustChangePassword)
            apply()
        }
    }

    suspend fun updateAccessToken(newAccessToken: String, newRefreshToken: String? = null) {
        authPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, newAccessToken)
            if (newRefreshToken != null) {
                putString(KEY_REFRESH_TOKEN, newRefreshToken)
            }
            apply()
        }
    }

    suspend fun clearSession() {
        authPrefs.edit().clear().apply()
    }

    fun saveEmailEmployeeHint(email: String, employeeId: String) {
        val cleanEmail = email.trim().lowercase()
        val cleanEmpId = employeeId.trim().uppercase()
        if (cleanEmail.isNotBlank() && cleanEmpId.isNotBlank()) {
            accountHintsPrefs.edit().putString("emp_for_$cleanEmail", cleanEmpId).apply()
        }
    }

    fun getEmployeeIdForEmail(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        return accountHintsPrefs.getString("emp_for_$cleanEmail", null)?.takeIf { it.isNotBlank() }
    }

    fun saveEmailAuthMethodHint(email: String, method: String) {
        val cleanEmail = email.trim().lowercase()
        val cleanMethod = method.trim().lowercase()
        if (cleanEmail.isNotBlank() && cleanMethod.isNotBlank()) {
            accountHintsPrefs.edit().putString("method_for_$cleanEmail", cleanMethod).apply()
        }
    }

    fun getEmailAuthMethodHint(email: String): String? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        return accountHintsPrefs.getString("method_for_$cleanEmail", null)?.takeIf { it.isNotBlank() }
    }

    // --- Non-sensitive app preferences ---
    val customConfigFlow: Flow<Pair<String?, String?>> = context.appDataStore.data.map { prefs ->
        Pair(prefs[KEY_CUSTOM_SUPABASE_URL], prefs[KEY_CUSTOM_SUPABASE_KEY])
    }

    suspend fun saveCustomConfig(url: String, key: String) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SUPABASE_URL] = url
            prefs[KEY_CUSTOM_SUPABASE_KEY] = key
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.appDataStore.data.map { prefs ->
        AppThemeMode.fromStorage(prefs[KEY_APP_THEME_MODE])
    }

    suspend fun saveThemeMode(mode: AppThemeMode) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_APP_THEME_MODE] = mode.name
        }
    }

    val notificationPreferencesFlow: Flow<NotificationPreferences> = context.appDataStore.data.map { prefs ->
        NotificationPreferences(
            taskAlertsEnabled = prefs[KEY_NOTIF_TASKS] ?: true,
            incidentAlertsEnabled = prefs[KEY_NOTIF_INCIDENTS] ?: true,
            soundEnabled = prefs[KEY_NOTIF_SOUND] ?: true,
            vibrationEnabled = prefs[KEY_NOTIF_VIBRATION] ?: true
        )
    }

    suspend fun getNotificationPreferences(): NotificationPreferences = notificationPreferencesFlow.first()

    suspend fun updateNotificationPreferences(
        taskAlertsEnabled: Boolean? = null,
        incidentAlertsEnabled: Boolean? = null,
        soundEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null
    ) {
        context.appDataStore.edit { prefs ->
            if (taskAlertsEnabled != null) prefs[KEY_NOTIF_TASKS] = taskAlertsEnabled
            if (incidentAlertsEnabled != null) prefs[KEY_NOTIF_INCIDENTS] = incidentAlertsEnabled
            if (soundEnabled != null) prefs[KEY_NOTIF_SOUND] = soundEnabled
            if (vibrationEnabled != null) prefs[KEY_NOTIF_VIBRATION] = vibrationEnabled
        }
    }

    val lastUpdateCheckAtFlow: Flow<Long> = context.appDataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK_MS] ?: 0L
    }

    val cachedLatestReleaseFlow: Flow<AppReleaseInfo?> = context.appDataStore.data.map { prefs ->
        AppReleaseInfo.fromJsonString(prefs[KEY_CACHED_RELEASE_JSON])
    }

    val dismissedUpdateVersionCodeFlow: Flow<Int> = context.appDataStore.data.map { prefs ->
        prefs[KEY_DISMISSED_UPDATE_CODE] ?: 0
    }

    suspend fun getLastUpdateCheckAt(): Long = lastUpdateCheckAtFlow.first()

    suspend fun getCachedLatestRelease(): AppReleaseInfo? = cachedLatestReleaseFlow.first()

    suspend fun getDismissedUpdateVersionCode(): Int = dismissedUpdateVersionCodeFlow.first()

    suspend fun saveUpdateCheckResult(checkedAtMs: Long, release: AppReleaseInfo?) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK_MS] = checkedAtMs
            if (release != null) {
                prefs[KEY_CACHED_RELEASE_JSON] = release.toJsonString()
            } else {
                prefs.remove(KEY_CACHED_RELEASE_JSON)
            }
        }
    }

    suspend fun setDismissedUpdateVersionCode(versionCode: Int) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_DISMISSED_UPDATE_CODE] = versionCode
        }
    }

    // --- Per-device identification, push token & delta sync marker ---
    fun getOrCreateDeviceId(): String {
        val existing = accountHintsPrefs.getString("taskz_device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = java.util.UUID.randomUUID().toString()
        accountHintsPrefs.edit().putString("taskz_device_id", created).apply()
        return created
    }

    fun getPushToken(): String? = accountHintsPrefs.getString("taskz_push_token", null)?.takeIf { it.isNotBlank() }

    fun savePushToken(token: String) {
        accountHintsPrefs.edit().putString("taskz_push_token", token.trim()).apply()
    }

    fun getLastDeltaSyncIso(): String? = authPrefs.getString("last_delta_sync_iso", null)?.takeIf { it.isNotBlank() }

    fun saveLastDeltaSyncIso(iso: String) {
        authPrefs.edit().putString("last_delta_sync_iso", iso).apply()
    }

    fun clearSyncCursor() {
        authPrefs.edit().remove("last_delta_sync_iso").apply()
    }
}
