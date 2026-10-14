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
    @Query("SELECT * FROM outbox_events ORDER BY createdAt ASC")
    fun getAllEventsFlow(): Flow<List<OutboxEventEntity>>

    @Query("SELECT * FROM outbox_events WHERE status IN ('PENDING', 'SENT') ORDER BY createdAt ASC")
    suspend fun getPendingEvents(): List<OutboxEventEntity>

    @Query("SELECT COUNT(*) FROM outbox_events WHERE status != 'SYNCED'")
    fun getPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueEvent(event: OutboxEventEntity): Long

    @Update
    suspend fun updateEvent(event: OutboxEventEntity)

    @Query("UPDATE outbox_events SET status = :status, serverAcknowledgement = :ack WHERE transactionId = :transactionId")
    suspend fun updateEventStatus(transactionId: String, status: String, ack: String?)

    @Query("DELETE FROM outbox_events WHERE status = 'SYNCED'")
    suspend fun clearSyncedEvents()
}
