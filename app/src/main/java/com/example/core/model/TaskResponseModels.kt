package com.example.core.model

import java.util.UUID

enum class FormFieldType(val label: String) {
    YES_NO("Yes / No"),
    SINGLE_CHOICE("Single Choice"),
    MULTIPLE_CHOICE("Multiple Choice"),
    NUMBER("Number"),
    PERCENTAGE("Percentage"),
    TEXT("Short Text"),
    LONG_TEXT("Long Text"),
    CONFIRMATION_BUTTON("Confirmation Button")
}

data class ResponseFormField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val fieldType: FormFieldType = FormFieldType.TEXT,
    val isRequired: Boolean = false,
    val defaultValue: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val allowedOptions: List<String> = emptyList(),
    val validationMessage: String? = null
)

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
    val dynamicValues: Map<String, String> = emptyMap(),
    val submittedAt: String = "",
    val isPendingSync: Boolean = false,
    val isLate: Boolean = false,
    val delaySeconds: Int = 0
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
    val submittedAt: String?,
    val isOverdue: Boolean = false
)

data class TaskOperationsDashboardData(
    val taskId: String,
    val title: String,
    val taskType: String,
    val importance: String,
    val status: String,
    val completionPolicy: String,
    val executionTime: String?,
    val deadlineTime: String?,
    val currentProgress: Int,
    val totalLans: Int,
    val completedLans: Int,
    val pendingLans: Int,
    val overdueLans: Int,
    val totalParticipants: Int,
    val acknowledgedCount: Int,
    val lanStatuses: List<LanStatusItem>,
    val recentResponses: List<TaskResponseItem>
)

data class LanStatusItem(
    val lanNumber: String,
    val status: String, // pending, in_progress, completed, overdue
    val progress: Int,
    val employeeName: String?,
    val employeeId: String?,
    val lastResponseTime: String?,
    val completedAt: String?,
    val isOverdue: Boolean
)
