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
     * public.normalize_employee_id(p_value text) -> upper(trim(split_part(coalesce(p_value, ''), '@', 1)))
     */
    fun normalizeEmployeeId(employeeId: String): String {
        val beforeAt = employeeId.trim().substringBefore("@")
        return beforeAt.trim().uppercase()
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

    /**
     * Normalizes mobile input to E.164 matching public.normalize_mobile and profiles_mobile_check.
     */
    fun normalizeMobile(input: String): String {
        val cleaned = input.trim().replace(Regex("[^0-9+]"), "")
        if (cleaned.startsWith("+")) {
            val digits = cleaned.drop(1).filter { it.isDigit() }
            return if (digits.isNotEmpty()) "+$digits" else ""
        }
        val rawDigits = cleaned.filter { it.isDigit() }
        val digits = if (rawDigits.length == 11 && rawDigits.startsWith("0")) {
            rawDigits.drop(1)
        } else {
            rawDigits
        }
        return when {
            digits.isEmpty() -> ""
            digits.length == 10 && digits[0] in '6'..'9' -> "+91$digits"
            digits.length == 10 -> "+1$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> "+$digits"
        }
    }

    /**
     * Ordered mobile format candidates starting with canonical E.164 (+91XXXXXXXXXX),
     * followed by local 10-digit / 12-digit formats in case a legacy DB constraint rejects E.164.
     */
    fun mobileFormatCandidates(input: String): List<String> {
        val canonical = normalizeMobile(input)
        val rawDigits = input.filter { it.isDigit() }
        val list = linkedSetOf<String>()
        if (canonical.isNotBlank()) list.add(canonical)
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) list.add(trimmed)
        if (canonical.startsWith("+91") && canonical.length == 13) {
            val ten = canonical.drop(3)
            list.add(ten)
            list.add("91$ten")
        } else if (rawDigits.length >= 10) {
            list.add(rawDigits.takeLast(10))
            list.add(rawDigits)
        }
        return list.toList()
    }

    fun isValidUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return try {
            java.util.UUID.fromString(value.trim())
            true
        } catch (_: Exception) {
            false
        }
    }
}
