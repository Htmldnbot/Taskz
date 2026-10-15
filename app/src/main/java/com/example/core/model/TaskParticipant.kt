package com.example.core.model

data class TaskParticipant(
    val id: String = "",
    val taskId: String = "",
    val userId: String = "",
    val roleId: String? = null,
    val source: String = "assigned", // assigned, self_join, approved_join, copied_assignment
    val status: String = "active", // active, completed, left, removed
    val assignedBy: String? = null,
    val joinedAt: String = "",
    val leftAt: String? = null,
    val leftReason: String? = null,
    val userName: String? = null,
    val userEmployeeId: String? = null,
    val roleName: String? = null,
    val profilePhotoPath: String? = null
)
