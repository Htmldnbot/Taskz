package com.example.core.model

data class DailyDutyCheckin(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val employeeId: String = "",
    val date: String = "",
    val centreCode: String = "",
    val centreName: String = "",
    val role: String = "IT_MANAGER", // "IT_MANAGER" or "VCO"
    val lan: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
