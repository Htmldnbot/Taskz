package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Phase 4 Durable Outbox Entity for Reliable Offline Synchronization.
 * Supports:
 * - PENDING, SENDING, PENDING_VERIFICATION, SYNCED, FAILED_RETRYABLE, FAILED_PERMANENT
 * - User isolation (never syncs user A events under user B)
 * - Unique stable idempotency key (eventId)
 */
@Entity(tableName = "outbox_events")
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val taskId: String = "",
    val operationType: String = "", // TASK_ACKNOWLEDGEMENT, TASK_RESPONSE, PROGRESS_UPDATE, LAN_CONFIRMATION, COMPLETION_REQUEST
    val payloadJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val syncStatus: String = "PENDING", // PENDING, SENDING, PENDING_VERIFICATION, SYNCED, FAILED_RETRYABLE, FAILED_PERMANENT
    val lastError: String? = null,
    val serverAcknowledgement: String? = null,
    val transactionId: String = eventId
)
