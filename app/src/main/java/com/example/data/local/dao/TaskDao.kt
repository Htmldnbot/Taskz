package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM cached_tasks ORDER BY startsAt ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM cached_tasks WHERE groupId = :groupId ORDER BY startsAt ASC")
    fun getTasksForGroupFlow(groupId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM cached_tasks WHERE shiftId = :shiftId ORDER BY startsAt ASC")
    fun getTasksForShiftFlow(shiftId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM cached_tasks WHERE id = :taskId LIMIT 1")
    fun getTaskByIdFlow(taskId: String): Flow<TaskEntity?>

    @Query("SELECT * FROM cached_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE cached_tasks SET isAcknowledged = :isAcknowledged, acknowledgedAt = :acknowledgedAt WHERE id = :taskId")
    suspend fun markTaskAcknowledged(taskId: String, isAcknowledged: Boolean, acknowledgedAt: String?)

    @Query("UPDATE cached_tasks SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String)

    @Query("UPDATE cached_tasks SET currentProgress = :progress, lastProgressUpdate = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskProgress(taskId: String, progress: Int, updatedAt: String)

    @Query("DELETE FROM cached_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM cached_tasks")
    suspend fun clearAll()
}
