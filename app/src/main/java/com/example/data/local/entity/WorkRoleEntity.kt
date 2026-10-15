package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.WorkRole

@Entity(tableName = "cached_work_roles")
data class WorkRoleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String?,
    val description: String?,
    val isActive: Boolean,
    val sortOrder: Int
) {
    fun toDomain(): WorkRole = WorkRole(
        id = id,
        name = name,
        code = code,
        description = description,
        isActive = isActive,
        sortOrder = sortOrder
    )

    companion object {
        fun fromDomain(domain: WorkRole): WorkRoleEntity = WorkRoleEntity(
            id = domain.id,
            name = domain.name,
            code = domain.code,
            description = domain.description,
            isActive = domain.isActive,
            sortOrder = domain.sortOrder
        )
    }
}
