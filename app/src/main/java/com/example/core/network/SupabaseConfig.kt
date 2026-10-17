package com.example.core.network

import com.example.BuildConfig
import java.security.MessageDigest

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

    /**
     * Normalizes an Employee ID matching the database function:
     * public.normalize_employee_id(p_value text) -> upper(trim(coalesce(p_value, '')))
     */
    fun normalizeEmployeeId(employeeId: String): String {
        return employeeId.trim().uppercase()
    }

    /**
     * Deterministic, collision-free synthetic email mapping:
     * Format: emp<hash24>@taskzapp.org
     * Clean alphanumeric local part to strictly conform to GoTrue and RFC 5322.
     */
    fun toInternalAuthEmail(employeeId: String): String {
        val normalized = normalizeEmployeeId(employeeId)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return "emp$hash@taskzapp.org"
    }

    /**
     * Legacy format to ensure backward compatibility for previously registered employees.
     */
    fun toLegacyAuthEmail(employeeId: String): String {
        val normalized = normalizeEmployeeId(employeeId)
        val readable = normalized.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(16)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val prefix = if (readable.isNotEmpty()) "emp_${readable}_" else "emp_"
        return "$prefix$hash@taskzapp.com"
    }
}
