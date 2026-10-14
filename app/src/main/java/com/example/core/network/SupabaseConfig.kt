package com.example.core.network

import com.example.BuildConfig

object SupabaseConfig {
    // Default placeholders to detect unconfigured state
    private const val PLACEHOLDER_URL = "https://your-project.supabase.co"
    private const val PLACEHOLDER_KEY = "your-anon-key"

    var runtimeUrl: String? = null
    var runtimeAnonKey: String? = null

    val isConfigured: Boolean
        get() {
            val url = getActiveUrl()
            val key = getActiveKey()
            return url.isNotBlank() && key.isNotBlank() &&
                    url != PLACEHOLDER_URL && key != PLACEHOLDER_KEY &&
                    url.startsWith("http")
        }

    fun getActiveUrl(): String {
        return runtimeUrl?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.SUPABASE_URL }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: PLACEHOLDER_URL
    }

    fun getActiveKey(): String {
        return runtimeAnonKey?.takeIf { it.isNotBlank() }
            ?: runCatching { BuildConfig.SUPABASE_ANON_KEY }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: PLACEHOLDER_KEY
    }

    fun toInternalAuthEmail(employeeId: String): String {
        val sanitized = employeeId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        return "$sanitized@taskz.internal"
    }
}
