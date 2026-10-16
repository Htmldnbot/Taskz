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
     *
     * Why this design:
     * 1. Employees log in exclusively with Employee ID + Password (no email UI).
     * 2. Supabase Auth requires an email format by default.
     * 3. Naive character stripping causes collisions (e.g. "EMP-1024" vs "EMP1024", or IDs with punctuation).
     * 4. This mapping computes the uppercase-normalized Employee ID, extracts an alphanumeric prefix for readability,
     *    and appends a SHA-256 cryptographic digest of the exact normalized ID.
     *    Because SHA-256 is collision-resistant, IDs that differ in any character (including punctuation)
     *    will NEVER map to the same Auth identifier.
     * 5. The total local-part length is strictly under 64 characters (RFC compliant).
     *
     * IMPORTANT SUPABASE CONFIGURATION:
     * Because internal synthetic emails (@taskz.internal) do not receive inbox emails,
     * "Confirm email" MUST be toggled OFF in the Supabase Dashboard
     * (Authentication -> Providers -> Email -> "Confirm email" = disabled).
     */
    fun toInternalAuthEmail(employeeId: String): String {
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
