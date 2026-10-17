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
    @Query("SELECT * FROM scheduled_alarm_records WHERE userId = :userId AND isCancelled = 0 AND isFired = 0 ORDER BY triggerAtMillis ASC")
    fun getActiveAlarmsFlowForUser(userId: String): Flow<List<ScheduledAlarmRecordEntity>>

    @Query("SELECT * FROM scheduled_alarm_records WHERE isCancelled = 0 AND isFired = 0 ORDER BY triggerAtMillis ASC")
    fun getActiveAlarmsFlow(): Flow<List<ScheduledAlarmRecordEntity>>

    @Query("SELECT * FROM scheduled_alarm_records WHERE userId = :userId AND isCancelled = 0 AND isFired = 0 AND triggerAtMillis >= :nowMillis ORDER BY triggerAtMillis ASC")
    suspend fun getUpcomingActiveAlarmsForUser(userId: String, nowMillis: Long): List<ScheduledAlarmRecordEntity>

    @Query("SELECT * FROM scheduled_alarm_records WHERE isCancelled = 0 AND isFired = 0 AND triggerAtMillis >= :nowMillis ORDER BY triggerAtMillis ASC")
    suspend fun getUpcomingActiveAlarms(nowMillis: Long): List<ScheduledAlarmRecordEntity>

    @Query("SELECT * FROM scheduled_alarm_records WHERE taskId = :taskId")
    suspend fun getAlarmsForTask(taskId: String): List<ScheduledAlarmRecordEntity>

    @Query("SELECT * FROM scheduled_alarm_records WHERE taskId = :taskId AND userId = :userId")
    suspend fun getAlarmsForTaskAndUser(taskId: String, userId: String): List<ScheduledAlarmRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: ScheduledAlarmRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarms(alarms: List<ScheduledAlarmRecordEntity>)

    @Query("UPDATE scheduled_alarm_records SET isFired = 1 WHERE alarmId = :alarmId")
    suspend fun markAlarmFired(alarmId: Int)

    @Query("UPDATE scheduled_alarm_records SET isCancelled = 1 WHERE taskId = :taskId")
    suspend fun cancelAlarmsForTask(taskId: String)

    @Query("UPDATE scheduled_alarm_records SET isCancelled = 1 WHERE taskId = :taskId AND userId = :userId")
    suspend fun cancelAlarmsForTaskAndUser(taskId: String, userId: String)

    @Query("UPDATE scheduled_alarm_records SET isCancelled = 1 WHERE userId = :userId")
    suspend fun cancelAllAlarmsForUser(userId: String)

    @Query("DELETE FROM scheduled_alarm_records WHERE taskId = :taskId")
    suspend fun deleteAlarmsForTask(taskId: String)
}
