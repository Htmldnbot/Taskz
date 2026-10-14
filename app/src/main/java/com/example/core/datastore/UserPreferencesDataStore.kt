package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taskz_user_prefs")

data class UserSession(
    val accessToken: String?,
    val refreshToken: String?,
    val authUserId: String?,
    val employeeId: String?,
    val mustChangePassword: Boolean
) {
    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank() && !authUserId.isNullOrBlank()
}

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        private val KEY_EMPLOYEE_ID = stringPreferencesKey("employee_id")
        private val KEY_MUST_CHANGE_PASSWORD = booleanPreferencesKey("must_change_password")
        private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
        private val KEY_CUSTOM_SUPABASE_KEY = stringPreferencesKey("custom_supabase_key")
    }

    val userSessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            accessToken = prefs[KEY_ACCESS_TOKEN],
            refreshToken = prefs[KEY_REFRESH_TOKEN],
            authUserId = prefs[KEY_AUTH_USER_ID],
            employeeId = prefs[KEY_EMPLOYEE_ID],
            mustChangePassword = prefs[KEY_MUST_CHANGE_PASSWORD] ?: false
        )
    }

    suspend fun getUserSession(): UserSession = userSessionFlow.first()

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String?,
        authUserId: String,
        employeeId: String,
        mustChangePassword: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_AUTH_USER_ID] = authUserId
            prefs[KEY_EMPLOYEE_ID] = employeeId
            prefs[KEY_MUST_CHANGE_PASSWORD] = mustChangePassword
        }
    }

    suspend fun updateAccessToken(newAccessToken: String, newRefreshToken: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = newAccessToken
            if (newRefreshToken != null) {
                prefs[KEY_REFRESH_TOKEN] = newRefreshToken
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_AUTH_USER_ID)
            prefs.remove(KEY_EMPLOYEE_ID)
            prefs.remove(KEY_MUST_CHANGE_PASSWORD)
        }
    }

    val customConfigFlow: Flow<Pair<String?, String?>> = context.dataStore.data.map { prefs ->
        Pair(prefs[KEY_CUSTOM_SUPABASE_URL], prefs[KEY_CUSTOM_SUPABASE_KEY])
    }

    suspend fun saveCustomConfig(url: String, key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_SUPABASE_URL] = url
            prefs[KEY_CUSTOM_SUPABASE_KEY] = key
        }
    }
}
