package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SystemIncidentAudit

@Entity(
    tableName = "system_incident_audits",
    indices = [
        Index(value = ["incidentId"]),
        Index(value = ["changedAt"])
    ]
)
data class SystemIncidentAuditEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val changedBy: String,
    val changedByName: String?,
    val changedAt: Long,
    val action: String,
    val previousValues: String?,
    val newValues: String?
) {
    fun toDomain(): SystemIncidentAudit {
        return SystemIncidentAudit(
            id = id,
            incidentId = incidentId,
            changedBy = changedBy,
            changedByName = changedByName,
            changedAt = changedAt,
            action = action,
            previousValues = previousValues,
            newValues = newValues
        )
    }

    companion object {
        fun fromDomain(domain: SystemIncidentAudit): SystemIncidentAuditEntity {
            return SystemIncidentAuditEntity(
                id = domain.id,
                incidentId = domain.incidentId,
                changedBy = domain.changedBy,
                changedByName = domain.changedByName,
                changedAt = domain.changedAt,
                action = domain.action,
                previousValues = domain.previousValues,
                newValues = domain.newValues
            )
        }
    }
}
