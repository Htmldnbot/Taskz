package com.example.core.model

data class TaskGroup(
    val id: String = "",
    val title: String = "",
    val code: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String = "active", // draft, active, completed, cancelled, archived
    val visibility: String = "everyone", // 'everyone' or 'restricted'
    val allowSelfJoin: Boolean = false,
    val joinRequiresApproval: Boolean = true,
    val notifyCreatorOnJoin: Boolean = true,
    val maxMembers: Int? = null,
    val createdBy: String = "",
    val creatorName: String? = null,
    val memberCount: Int = 0,
    val shiftCount: Int = 0,
    val taskCount: Int = 0,
    val isCurrentUserMember: Boolean = false,
    val isCurrentUserPending: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
)
