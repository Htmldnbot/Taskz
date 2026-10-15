package com.example.core.model

data class UserProfile(
    val id: String = "", // UUID matching auth.users(id) - ONE identity model
    val employeeId: String = "",
    val fullName: String = "",
    val mobile: String = "",
    val profilePhotoPath: String? = null, // Storage path e.g. "USER_UUID/avatar_123.jpg"
    val avatarSignedUrl: String? = null, // Runtime signed URL for private image display
    val appRole: String = "employee", // 'employee' or 'admin'
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val isAdmin: Boolean
        get() = appRole.equals("admin", ignoreCase = true)
}
