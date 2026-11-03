package com.example.core.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Domain model for System Incident Register.
 * Captures hardware and software technical failures at exam centres.
 */
data class SystemIncident(
    val id: String = UUID.randomUUID().toString(),
    val submissionId: String = UUID.randomUUID().toString(),
    val centreId: String? = null,
    val examSessionId: String? = null,
    val stateName: String = "",
    val districtName: String = "",
    val centreCode: String = "",
    val centreName: String = "",
    val examDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val examName: String = "",
    val shiftName: String = "",
    val lanName: String = "",
    val macAddress: String = "",
    val ipAddress: String = "",
    val lockedSystemNumber: String = "",
    val newlyAllocatedSystemNumber: String? = null,
    val candidateShifted: Boolean = false,
    val incidentTime: String = SimpleDateFormat("hh:mm a", Locale.US).format(Date()),
    val incidentTimestampMillis: Long = System.currentTimeMillis(),
    val reason: String = "",
    val itManagerName: String = "",
    val createdBy: String = "",
    val creatorName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String? = null,
    val syncStatus: String = "PENDING", // SYNCED, PENDING, ERROR
    val lastSyncError: String? = null
) {
    val displayReplacementSystem: String
        get() = if (candidateShifted && !newlyAllocatedSystemNumber.isNullOrBlank()) {
            newlyAllocatedSystemNumber
        } else {
            "NA"
        }

    val displayCentreSummary: String
        get() = if (centreCode.isNotBlank() && centreName.isNotBlank()) {
            "$centreCode · $centreName"
        } else if (centreName.isNotBlank()) {
            centreName
        } else {
            "Select Centre"
        }

    val displayLocationSummary: String
        get() = listOf(districtName, stateName).filter { it.isNotBlank() }.joinToString(", ")

    companion object {
        const val SYSTEM_NUMBER_REGEX = "^C[0-9]{3}$"
        const val MAC_ADDRESS_REGEX = "^([0-9A-F]{2}:){5}[0-9A-F]{2}$"

        val STANDARD_REASONS = listOf(
            "System Hang",
            "System Lock",
            "Network Disconnection",
            "Keyboard Fault",
            "Mouse Fault",
            "Power Failure",
            "Hardware Failure",
            "Software/Application Error",
            "System Restart",
            "Other"
        )

        /**
         * Normalizes raw digit input into CXXX format.
         * Example:
         * "5" -> "C005"
         * "59" -> "C059"
         * "159" -> "C159"
         * "C159" -> "C159"
         */
        fun normalizeSystemNumber(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val clean = if (trimmed.startsWith("C", ignoreCase = true)) {
                trimmed.substring(1)
            } else {
                trimmed
            }
            val digitsOnly = clean.filter { it.isDigit() }.take(3)
            if (digitsOnly.isEmpty()) return ""
            val num = digitsOnly.toIntOrNull() ?: return ""
            return String.format(Locale.US, "C%03d", num)
        }

        fun isValidSystemNumber(value: String): Boolean {
            return value.matches(Regex(SYSTEM_NUMBER_REGEX))
        }

        /**
         * Formats raw text into standard 48-bit MAC address: XX:XX:XX:XX:XX:XX
         */
        fun formatMacAddress(raw: String): String {
            val hexOnly = raw.filter { (it in '0'..'9') || (it in 'a'..'f') || (it in 'A'..'F') }.uppercase(Locale.US).take(12)
            val chunks = mutableListOf<String>()
            for (i in hexOnly.indices step 2) {
                val end = minOf(i + 2, hexOnly.length)
                chunks.add(hexOnly.substring(i, end))
            }
            return chunks.joinToString(":")
        }

        fun isValidMacAddress(value: String): Boolean {
            return value.matches(Regex(MAC_ADDRESS_REGEX))
        }

        /**
         * Validates standard IPv4 address (4 octets, 0..255).
         */
        fun isValidIpv4(ip: String): Boolean {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return false
            return parts.all { part ->
                if (part.isEmpty() || part.length > 3) return@all false
                if (part.length > 1 && part.startsWith("0")) return@all false // no leading zeros like 01
                val num = part.toIntOrNull() ?: return@all false
                num in 0..255
            }
        }
    }
}

data class SystemIncidentAudit(
    val id: String = UUID.randomUUID().toString(),
    val incidentId: String,
    val changedBy: String,
    val changedByName: String? = null,
    val changedAt: Long = System.currentTimeMillis(),
    val action: String, // INSERT, UPDATE, DELETE
    val previousValues: String? = null,
    val newValues: String? = null
)
