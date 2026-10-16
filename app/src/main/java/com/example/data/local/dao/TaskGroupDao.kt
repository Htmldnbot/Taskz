package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskGroupDao {
    @Query("SELECT * FROM cached_task_groups ORDER BY createdAt DESC")
    fun getAllGroupsFlow(): Flow<List<TaskGroupEntity>>

    @Query("SELECT * FROM cached_task_groups WHERE id = :groupId LIMIT 1")
    fun getGroupByIdFlow(groupId: String): Flow<TaskGroupEntity?>

    @Query("SELECT * FROM cached_task_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): TaskGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<TaskGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: TaskGroupEntity)

    @Update
    suspend fun updateGroup(group: TaskGroupEntity)

    @Query("DELETE FROM cached_task_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM cached_task_groups")
    suspend fun clearAll()
}
