package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Foundation for Phase 2 offline task operations queue.
 * Satisfies the requirement:
 * 1. operation created locally
 * 2. operation sent
 * 3. server acknowledged
 * 4. operation fully synchronized
 */
@Entity(tableName = "outbox_events")
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val eventType: String,
    val payloadJson: String,
    val status: String = "PENDING", // PENDING, SENT, ACKNOWLEDGED, SYNCED
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val serverAcknowledgement: String? = null
)
