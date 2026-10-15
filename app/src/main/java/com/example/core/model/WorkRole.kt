package com.example.core.model

data class WorkRole(
    val id: String = "",
    val name: String = "",
    val code: String? = null,
    val description: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
