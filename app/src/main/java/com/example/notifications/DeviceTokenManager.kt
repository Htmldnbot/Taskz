package com.example.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages per-device notification token registration, token refresh, boot recovery,
 * and single-device deactivation on logout so sibling logged-in devices stay unaffected.
 */
class DeviceTokenManager(
    private val context: Context,
    private val userPrefs: UserPreferencesDataStore,
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TAG = "DeviceTokenManager"
    }

    fun getDeviceId(): String = userPrefs.getOrCreateDeviceId()

    fun getCurrentOrFallbackToken(): String {
        return userPrefs.getPushToken() ?: "local:${getDeviceId()}"
    }

    suspend fun onNewPushToken(newToken: String) = withContext(Dispatchers.IO) {
        val clean = newToken.trim()
        if (clean.isBlank()) return@withContext
        userPrefs.savePushToken(clean)
        registerActiveDeviceToken()
    }

    suspend fun registerActiveDeviceToken(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
        if (!session.isAuthenticated || token.isNullOrBlank() || token.startsWith("offline_")) {
            return@withContext Result.success(Unit)
        }
        val deviceId = getDeviceId()
        val pushToken = getCurrentOrFallbackToken()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")

        supabaseClient.registerDeviceToken(
            accessToken = token,
            deviceId = deviceId,
            token = pushToken,
            deviceName = deviceName,
            appVersion = appVersion
        ).onFailure {
            Log.w(TAG, "Device token registration deferred: ${it.message}")
        }
    }

    suspend fun deactivateThisDeviceOnly() = withContext(Dispatchers.IO) {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
        if (token.isNullOrBlank() || token.startsWith("offline_")) return@withContext
        val deviceId = getDeviceId()
        supabaseClient.deactivateDeviceToken(token, deviceId).onFailure {
            Log.w(TAG, "Device token deactivation failed gracefully: ${it.message}")
        }
    }
}
