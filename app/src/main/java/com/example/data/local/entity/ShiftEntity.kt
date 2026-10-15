package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.Shift

@Entity(tableName = "cached_shifts")
data class ShiftEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val shiftNumber: Int?,
    val startsAt: String,
    val endsAt: String,
    val notes: String?,
    val status: String,
    val createdBy: String
) {
    fun toDomain(): Shift = Shift(
        id = id,
        groupId = groupId,
        name = name,
        shiftNumber = shiftNumber,
        startsAt = startsAt,
        endsAt = endsAt,
        notes = notes,
        status = status,
        createdBy = createdBy
    )

    companion object {
        fun fromDomain(domain: Shift): ShiftEntity = ShiftEntity(
            id = domain.id,
            groupId = domain.groupId,
            name = domain.name,
            shiftNumber = domain.shiftNumber,
            startsAt = domain.startsAt,
            endsAt = domain.endsAt,
            notes = domain.notes,
            status = domain.status,
            createdBy = domain.createdBy
        )
    }
}
