package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.OutboxEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_events WHERE userId = :userId ORDER BY createdAt ASC")
    fun getAllEventsFlowForUser(userId: String): Flow<List<OutboxEventEntity>>

    @Query("SELECT * FROM outbox_events WHERE userId = :userId AND syncStatus IN ('PENDING', 'SENDING', 'FAILED_RETRYABLE') ORDER BY createdAt ASC")
    suspend fun getPendingEventsForUser(userId: String): List<OutboxEventEntity>

    @Query("SELECT * FROM outbox_events WHERE syncStatus IN ('PENDING', 'SENDING', 'FAILED_RETRYABLE') ORDER BY createdAt ASC")
    suspend fun getPendingEvents(): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE userId = :userId AND syncStatus != 'SYNCED'")
    fun getPendingCountFlowForUser(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE syncStatus != 'SYNCED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM outbox_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventByEventId(eventId: String): OutboxEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueEvent(event: OutboxEventEntity): Long

    @Update
    suspend fun updateEvent(event: OutboxEventEntity)

    @Query("UPDATE outbox_events SET syncStatus = 'SENDING', lastAttemptAt = :now, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun markEventSending(id: Long, now: Long)

    @Query("UPDATE outbox_events SET syncStatus = 'SYNCED', serverAcknowledgement = :ack, lastAttemptAt = :now WHERE id = :id")
    suspend fun markEventSynced(id: Long, ack: String, now: Long)

    @Query("UPDATE outbox_events SET syncStatus = :status, lastError = :error, lastAttemptAt = :now WHERE id = :id")
    suspend fun markEventFailed(id: Long, error: String, status: String, now: Long)

    @Query("UPDATE outbox_events SET syncStatus = :status, serverAcknowledgement = :ack WHERE transactionId = :transactionId")
    suspend fun updateEventStatus(transactionId: String, status: String, ack: String?)

    @Query("DELETE FROM outbox_events WHERE userId = :userId AND syncStatus = 'SYNCED'")
    suspend fun clearSyncedEventsForUser(userId: String)

    @Query("DELETE FROM outbox_events WHERE syncStatus = 'SYNCED'")
    suspend fun clearSyncedEvents()
}
