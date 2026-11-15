package com.example.core.model

data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val type: String = "",
    val category: String = "task_update",
    val title: String = "",
    val body: String? = null,
    val taskId: String? = null,
    val incidentId: String? = null,
    val groupId: String? = null,
    val eventKey: String? = null,
    val deepLink: String? = null,
    val scopeLevel: String? = null,
    val isRead: Boolean = false,
    val readAt: String? = null,
    val createdAt: String = ""
)

data class DashboardStats(
    val myTasksToday: Int = 0,
    val upcomingTasks: Int = 0,
    val pendingRequests: Int = 0,
    val vacancies: Int = 0
)

data class EmployeeSummary(
    val id: String = "",
    val employeeId: String = "",
    val fullName: String = "",
    val email: String? = null,
    val mobile: String? = null,
    val activeRoleCode: String = "IT_MANAGER",
    val assignedRoles: List<String> = emptyList(),
    val cityName: String? = null,
    val centreCode: String? = null,
    val profilePhotoPath: String? = null,
    val avatarSignedUrl: String? = null
)
