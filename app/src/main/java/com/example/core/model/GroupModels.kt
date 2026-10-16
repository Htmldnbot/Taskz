package com.example.core.model

data class GroupMember(
    val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val employeeId: String? = null,
    val fullName: String? = null,
    val profilePhotoPath: String? = null,
    val groupRole: String = "member", // owner, admin, member
    val joinedAt: String = ""
)

data class GroupJoinRequest(
    val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val employeeId: String? = null,
    val fullName: String? = null,
    val profilePhotoPath: String? = null,
    val status: String = "pending", // pending, approved, rejected, cancelled
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String = ""
)
