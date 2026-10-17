package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.data.local.entity.TaskResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskResponseDao {
    @Query("SELECT * FROM cached_task_responses WHERE taskId = :taskId ORDER BY submittedAt DESC")
    fun getResponsesForTaskFlow(taskId: String): Flow<List<TaskResponseEntity>>

    @Query("SELECT * FROM cached_task_responses WHERE taskId = :taskId ORDER BY submittedAt DESC")
    suspend fun getResponsesForTask(taskId: String): List<TaskResponseEntity>

    @Query("SELECT * FROM cached_task_responses WHERE taskId = :taskId AND userId = :userId ORDER BY submittedAt DESC")
    suspend fun getResponsesForTaskAndUser(taskId: String, userId: String): List<TaskResponseEntity>

    @Query("SELECT * FROM cached_task_responses WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncResponses(): List<TaskResponseEntity>

    @Query("SELECT * FROM cached_task_responses WHERE userId = :userId AND syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncResponsesForUser(userId: String): List<TaskResponseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponse(response: TaskResponseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResponses(responses: List<TaskResponseEntity>)

    @Query("UPDATE cached_task_responses SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markResponseSynced(id: String)

    // Acknowledgements
    @Query("SELECT * FROM cached_task_acknowledgements WHERE taskId = :taskId")
    fun getAcknowledgementsForTaskFlow(taskId: String): Flow<List<TaskAcknowledgementEntity>>

    @Query("SELECT * FROM cached_task_acknowledgements WHERE taskId = :taskId AND (employeeId = :employeeId OR userId = :userId) LIMIT 1")
    suspend fun getAcknowledgement(taskId: String, employeeId: String, userId: String = ""): TaskAcknowledgementEntity?

    @Query("SELECT * FROM cached_task_acknowledgements WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAcknowledgements(): List<TaskAcknowledgementEntity>

    @Query("SELECT * FROM cached_task_acknowledgements WHERE userId = :userId AND syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAcknowledgementsForUser(userId: String): List<TaskAcknowledgementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcknowledgement(ack: TaskAcknowledgementEntity)

    @Query("UPDATE cached_task_acknowledgements SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAcknowledgementSynced(id: String)
}
