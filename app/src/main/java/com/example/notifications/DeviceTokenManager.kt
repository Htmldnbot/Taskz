package com.example.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.data.remote.SupabaseClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Manages per-device real FCM notification token registration, token refresh, boot recovery,
 * and single-device deactivation on logout so sibling logged-in devices stay unaffected.
 * Synthetic `local:*` identifiers are NEVER sent to the backend push token registry.
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

    fun getRegisteredPushToken(): String? {
        val stored = userPrefs.getPushToken()?.trim() ?: return null
        if (stored.isBlank() || stored.startsWith("local:", ignoreCase = true)) {
            return null
        }
        return stored
    }

    suspend fun onNewPushToken(newToken: String) = withContext(Dispatchers.IO) {
        val clean = newToken.trim()
        if (clean.isBlank() || clean.startsWith("local:", ignoreCase = true)) return@withContext
        userPrefs.savePushToken(clean)
        registerActiveDeviceToken()
    }

    suspend fun fetchAndRegisterFcmTokenIfAvailable(): Result<Unit> = withContext(Dispatchers.IO) {
        val hasFirebase = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
        if (!hasFirebase) {
            Log.d(TAG, "FirebaseApp not initialized (google-services.json not configured); skipping FCM fetch")
            return@withContext Result.success(Unit)
        }
        val fetched = runCatching {
            suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!cont.isActive) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        cont.resume(task.result)
                    } else {
                        Log.w(TAG, "FCM token fetch deferred: ${task.exception?.message}")
                        cont.resume(null)
                    }
                }
            }
        }.getOrNull()?.trim()

        if (!fetched.isNullOrBlank() && !fetched.startsWith("local:", ignoreCase = true)) {
            userPrefs.savePushToken(fetched)
        }
        registerActiveDeviceToken()
    }

    suspend fun registerActiveDeviceToken(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = userPrefs.getUserSession()
        val token = session.accessToken
        if (!session.isAuthenticated || token.isNullOrBlank() || token.startsWith("offline_")) {
            return@withContext Result.success(Unit)
        }
        var pushToken = getRegisteredPushToken()
        if (pushToken == null) {
            val hasFirebase = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
            if (hasFirebase) {
                val fetched = runCatching {
                    suspendCancellableCoroutine<String?> { cont ->
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (!cont.isActive) return@addOnCompleteListener
                            cont.resume(if (task.isSuccessful) task.result else null)
                        }
                    }
                }.getOrNull()?.trim()
                if (!fetched.isNullOrBlank() && !fetched.startsWith("local:", ignoreCase = true)) {
                    userPrefs.savePushToken(fetched)
                    pushToken = fetched
                }
            }
        }
        if (pushToken.isNullOrBlank()) {
            Log.d(TAG, "No real FCM push token available yet; skipping register_device_token RPC")
            return@withContext Result.success(Unit)
        }

        val deviceId = getDeviceId()
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
