package com.example.core.model

data class UserProfile(
    val id: String = "",
    val authUserId: String = "",
    val employeeId: String = "",
    val fullName: String = "",
    val mobile: String = "",
    val profilePhotoUrl: String? = null,
    val appRole: String = "employee",
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val isAdmin: Boolean
        get() = appRole.equals("admin", ignoreCase = true)
}
