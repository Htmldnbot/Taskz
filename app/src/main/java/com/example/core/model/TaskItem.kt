package com.example.core.model

data class TaskItem(
    val id: String = "",
    val groupId: String = "",
    val groupTitle: String? = null,
    val shiftId: String? = null,
    val shiftName: String? = null,
    val title: String = "",
    val description: String? = null,
    val location: String? = null,
    val startsAt: String? = null,
    val dueAt: String? = null,
    val priority: String = "normal", // low, normal, high, urgent
    val status: String = "open", // draft, open, in_progress, completed, cancelled, archived
    val visibility: String = "everyone", // everyone, restricted
    val allowSelfJoin: Boolean = false,
    val joinRequiresApproval: Boolean = true,
    val notifyCreatorOnJoin: Boolean = true,
    val requiresCompletionConfirmation: Boolean = false,
    val maxParticipants: Int? = null,
    val createdBy: String = "",
    val creatorName: String? = null,
    val totalRequired: Int = 0,
    val totalAssigned: Int = 0,
    val totalVacant: Int = 0,
    val currentUserRoleName: String? = null,
    val currentUserParticipantStatus: String? = null, // "active", "left", "removed" or null
    val currentUserJoinStatus: String? = null, // "pending", "approved", "rejected" or null
    val isPendingCompletionConfirmation: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val isAssignedToMe: Boolean
        get() = currentUserParticipantStatus == "active"

    val isPendingApproval: Boolean
        get() = currentUserJoinStatus == "pending"
}
