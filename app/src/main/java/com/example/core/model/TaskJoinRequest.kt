package com.example.core.model

data class TaskJoinRequest(
    val id: String = "",
    val taskId: String = "",
    val taskTitle: String? = null,
    val userId: String = "",
    val roleId: String? = null,
    val message: String? = null,
    val status: String = "pending", // pending, approved, rejected, cancelled
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String = "",
    val userName: String? = null,
    val userEmployeeId: String? = null,
    val roleName: String? = null,
    val profilePhotoPath: String? = null
)
