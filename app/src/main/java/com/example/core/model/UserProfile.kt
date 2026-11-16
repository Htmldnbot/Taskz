package com.example.core.model

data class UserProfile(
    val id: String = "", // UUID matching auth.users(id) - ONE identity model
    val employeeId: String = "",
    val fullName: String = "",
    val mobile: String = "",
    val email: String? = null,
    val profilePhotoPath: String? = null, // Storage path e.g. "USER_UUID/avatar_123.jpg"
    val avatarSignedUrl: String? = null, // Runtime signed URL for private image display
    val appRole: String = "employee", // 'employee' or 'admin'
    val activeRoleCode: String = "IT_MANAGER",
    val primaryRoleCode: String = "IT_MANAGER",
    val assignedRoleCodes: List<String> = listOf("IT_MANAGER"),
    val zoneId: String? = null,
    val zoneName: String? = null,
    val stateId: String? = null,
    val stateName: String? = null,
    val cityId: String? = null,
    val cityName: String? = null,
    val centreId: String? = null,
    val centreCode: String? = null,
    val roleVersion: Long = 1L,
    val scopeVersion: Long = 1L,
    val needsScopeReview: Boolean = false,
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val isAdmin: Boolean
        get() = appRole.equals("admin", ignoreCase = true) ||
            activeRoleCode.equals("SUPER_ADMIN", ignoreCase = true) ||
            assignedRoleCodes.any { it.equals("SUPER_ADMIN", ignoreCase = true) }

    val resolvedActiveRole: TaskzRole
        get() = TaskzRole.fromCode(
            activeRoleCode.ifBlank {
                if (appRole.equals("admin", ignoreCase = true)) "SUPER_ADMIN" else "IT_MANAGER"
            }
        )
}
