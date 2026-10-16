package com.example.core.model

data class TaskResponseItem(
    val id: String = "",
    val taskId: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val employeeName: String? = null,
    val lanNumber: String? = null,
    val responseType: ResponseType = ResponseType.COMPLETED_NOT_COMPLETED,
    val responseValue: String? = null,
    val percentageValue: Int? = null,
    val numericValue1: Double? = null,
    val numericValue2: Double? = null,
    val textValue: String? = null,
    val submittedAt: String = "",
    val isPendingSync: Boolean = false
)

data class TaskAcknowledgementRecord(
    val id: String = "",
    val taskId: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val eventId: String = "",
    val deviceTimestamp: Long = 0L,
    val acknowledgedAt: String = "",
    val isPendingSync: Boolean = false
)

data class LanProgressItem(
    val lanNumber: String,
    val isConfirmed: Boolean,
    val percentage: Int?,
    val numeric1: Double?,
    val numeric2: Double?,
    val employeeName: String?,
    val employeeId: String?,
    val submittedAt: String?
)
