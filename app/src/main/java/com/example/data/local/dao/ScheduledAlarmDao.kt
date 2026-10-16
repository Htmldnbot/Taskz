package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ScheduledAlarmRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarm_records WHERE isCancelled = 0 AND isFired = 0 ORDER BY triggerAtMillis ASC")
    fun getActiveAlarmsFlow(): Flow<List<ScheduledAlarmRecordEntity>>

    @Query("SELECT * FROM scheduled_alarm_records WHERE isCancelled = 0 AND isFired = 0 AND triggerAtMillis >= :nowMillis ORDER BY triggerAtMillis ASC")
    suspend fun getUpcomingActiveAlarms(nowMillis: Long): List<ScheduledAlarmRecordEntity>

    @Query("SELECT * FROM scheduled_alarm_records WHERE taskId = :taskId")
    suspend fun getAlarmsForTask(taskId: String): List<ScheduledAlarmRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ScheduledAlarmRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<ScheduledAlarmRecordEntity>)

    @Query("UPDATE scheduled_alarm_records SET isFired = 1 WHERE alarmId = :alarmId")
    suspend fun markAlarmFired(alarmId: Int)

    @Query("UPDATE scheduled_alarm_records SET isCancelled = 1 WHERE taskId = :taskId")
    suspend fun cancelAlarmsForTask(taskId: String)

    @Query("DELETE FROM scheduled_alarm_records WHERE taskId = :taskId")
    suspend fun deleteAlarmsForTask(taskId: String)
}
