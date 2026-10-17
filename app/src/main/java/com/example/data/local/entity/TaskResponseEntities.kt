package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.ResponseType
import com.example.core.model.TaskAcknowledgementRecord
import com.example.core.model.TaskResponseItem
import org.json.JSONObject

@Entity(tableName = "cached_task_responses")
data class TaskResponseEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val userId: String,
    val employeeId: String,
    val employeeName: String?,
    val lanNumber: String?,
    val responseType: String,
    val responseValue: String?,
    val percentageValue: Int?,
    val numericValue1: Double?,
    val numericValue2: Double?,
    val textValue: String?,
    val dynamicValuesJson: String = "{}",
    val submittedAt: String,
    val syncStatus: String = "SYNCED", // "PENDING_SYNC", "SYNCED"
    val isLate: Boolean = false,
    val delaySeconds: Int = 0
) {
    fun toDomain(): TaskResponseItem = TaskResponseItem(
        id = id,
        taskId = taskId,
        userId = userId,
        employeeId = employeeId,
        employeeName = employeeName,
        lanNumber = lanNumber,
        responseType = runCatching { ResponseType.valueOf(responseType) }.getOrDefault(ResponseType.COMPLETED_NOT_COMPLETED),
        responseValue = responseValue,
        percentageValue = percentageValue,
        numericValue1 = numericValue1,
        numericValue2 = numericValue2,
        textValue = textValue,
        dynamicValues = parseDynamicValues(dynamicValuesJson),
        submittedAt = submittedAt,
        isPendingSync = syncStatus == "PENDING_SYNC",
        isLate = isLate,
        delaySeconds = delaySeconds
    )

    companion object {
        fun fromDomain(domain: TaskResponseItem, syncStatus: String = if (domain.isPendingSync) "PENDING_SYNC" else "SYNCED"): TaskResponseEntity = TaskResponseEntity(
            id = domain.id,
            taskId = domain.taskId,
            userId = domain.userId,
            employeeId = domain.employeeId,
            employeeName = domain.employeeName,
            lanNumber = domain.lanNumber,
            responseType = domain.responseType.name,
            responseValue = domain.responseValue,
            percentageValue = domain.percentageValue,
            numericValue1 = domain.numericValue1,
            numericValue2 = domain.numericValue2,
            textValue = domain.textValue,
            dynamicValuesJson = JSONObject(domain.dynamicValues).toString(),
            submittedAt = domain.submittedAt,
            syncStatus = syncStatus,
            isLate = domain.isLate,
            delaySeconds = domain.delaySeconds
        )

        private fun parseDynamicValues(json: String): Map<String, String> {
            return try {
                val map = mutableMapOf<String, String>()
                val obj = JSONObject(json)
                obj.keys().forEach { key ->
                    map[key] = obj.optString(key, "")
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}

@Entity(tableName = "cached_task_acknowledgements")
data class TaskAcknowledgementEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val userId: String,
    val employeeId: String,
    val eventId: String,
    val deviceTimestamp: Long,
    val acknowledgedAt: String,
    val syncStatus: String = "SYNCED"
) {
    fun toDomain(): TaskAcknowledgementRecord = TaskAcknowledgementRecord(
        id = id,
        taskId = taskId,
        userId = userId,
        employeeId = employeeId,
        eventId = eventId,
        deviceTimestamp = deviceTimestamp,
        acknowledgedAt = acknowledgedAt,
        isPendingSync = syncStatus == "PENDING_SYNC"
    )

    companion object {
        fun fromDomain(domain: TaskAcknowledgementRecord, syncStatus: String = if (domain.isPendingSync) "PENDING_SYNC" else "SYNCED"): TaskAcknowledgementEntity = TaskAcknowledgementEntity(
            id = domain.id,
            taskId = domain.taskId,
            userId = domain.userId,
            employeeId = domain.employeeId,
            eventId = domain.eventId,
            deviceTimestamp = domain.deviceTimestamp,
            acknowledgedAt = domain.acknowledgedAt,
            syncStatus = syncStatus
        )
    }
}

/**
 * Account-specific alarm record.
 * Alarms belong to the specific authenticated userId.
 */
@Entity(tableName = "scheduled_alarm_records")
data class ScheduledAlarmRecordEntity(
    @PrimaryKey val alarmId: Int,
    val userId: String = "",
    val taskId: String,
    val taskTitle: String,
    val triggerAtMillis: Long,
    val alarmType: String,
    val importance: String,
    val reminderLabel: String,
    val isFired: Boolean = false,
    val isCancelled: Boolean = false
)
