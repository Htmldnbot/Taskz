package com.example.core.model

data class TaskCompletionRequest(
    val id: String = "",
    val taskId: String = "",
    val requestedBy: String = "",
    val requesterName: String? = null,
    val note: String? = null,
    val status: String = "pending", // pending, confirmed, rejected, cancelled
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String = ""
)

data class TaskActivityLog(
    val id: Long = 0,
    val taskId: String = "",
    val actorUserId: String? = null,
    val actorName: String? = null,
    val targetUserId: String? = null,
    val eventType: String = "",
    val message: String? = null,
    val createdAt: String = ""
)
