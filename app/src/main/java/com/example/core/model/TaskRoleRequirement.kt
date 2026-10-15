package com.example.core.model

data class TaskRoleRequirement(
    val id: String = "",
    val taskId: String = "",
    val roleId: String = "",
    val requiredCount: Int = 1,
    val notes: String? = null,
    val roleName: String? = null,
    val filledCount: Int = 0
) {
    val vacantCount: Int
        get() = maxOf(0, requiredCount - filledCount)

    val isFull: Boolean
        get() = filledCount >= requiredCount
}
