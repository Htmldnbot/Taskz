package com.example.core.model

data class PasswordResetRequest(
    val id: String = "",
    val employeeId: String = "",
    val mobile: String = "",
    val message: String? = null,
    val status: String = "pending", // pending, resolved, rejected
    val createdAt: String = "",
    val resolvedAt: String? = null,
    val resolvedBy: String? = null,
    val employeeName: String? = null // matched from profiles for admin UI
) {
    val isPending: Boolean
        get() = status.equals("pending", ignoreCase = true)
}
