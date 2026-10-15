package com.example.core.model

data class Shift(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val shiftNumber: Int? = null,
    val startsAt: String = "",
    val endsAt: String = "",
    val notes: String? = null,
    val status: String = "scheduled", // scheduled, active, completed, cancelled
    val createdBy: String = "",
    val taskCount: Int = 0,
    val requiredManpower: Int = 0,
    val assignedManpower: Int = 0,
    val vacantManpower: Int = 0
)
