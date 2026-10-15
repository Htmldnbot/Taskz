package com.example.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "taskz_app_prefs")

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

    companion object {
        private const val AUTH_PREFS_NAME = "taskz_auth_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMPLOYEE_ID = "employee_id"
        private const val KEY_MUST_CHANGE_PASSWORD = "must_change_password"

        private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
        private val KEY_CUSTOM_SUPABASE_KEY = stringPreferencesKey("custom_supabase_key")
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
}
