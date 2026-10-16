package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM cached_shifts ORDER BY startsAt ASC")
    fun getAllShiftsFlow(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM cached_shifts WHERE groupId = :groupId ORDER BY startsAt ASC")
    fun getShiftsForGroupFlow(groupId: String): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM cached_shifts WHERE id = :shiftId LIMIT 1")
    fun getShiftByIdFlow(shiftId: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM cached_shifts WHERE id = :shiftId LIMIT 1")
    suspend fun getShiftById(shiftId: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShifts(shifts: List<ShiftEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("DELETE FROM cached_shifts WHERE id = :shiftId")
    suspend fun deleteShift(shiftId: String)

    @Query("DELETE FROM cached_shifts")
    suspend fun clearAll()
}
