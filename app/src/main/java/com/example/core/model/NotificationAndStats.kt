package com.example.core.model

data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String? = null,
    val taskId: String? = null,
    val groupId: String? = null,
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
    val profilePhotoPath: String? = null,
    val avatarSignedUrl: String? = null
)
