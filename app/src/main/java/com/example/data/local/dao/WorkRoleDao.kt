package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.WorkRoleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRoleDao {
    @Query("SELECT * FROM cached_work_roles ORDER BY sortOrder ASC, name ASC")
    fun getAllWorkRolesFlow(): Flow<List<WorkRoleEntity>>

    @Query("SELECT * FROM cached_work_roles WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    fun getActiveWorkRolesFlow(): Flow<List<WorkRoleEntity>>

    @Query("SELECT * FROM cached_work_roles WHERE id = :roleId LIMIT 1")
    suspend fun getWorkRoleById(roleId: String): WorkRoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkRoles(roles: List<WorkRoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkRole(role: WorkRoleEntity)

    @Update
    suspend fun updateWorkRole(role: WorkRoleEntity)

    @Query("DELETE FROM cached_work_roles WHERE id = :roleId")
    suspend fun deleteWorkRole(roleId: String)

    @Query("DELETE FROM cached_work_roles")
    suspend fun clearAll()
}
