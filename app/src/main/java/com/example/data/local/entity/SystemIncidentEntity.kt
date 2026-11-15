package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SystemIncident

@Entity(
    tableName = "system_incidents",
    indices = [
        Index(value = ["submissionId"], unique = true),
        Index(value = ["centreId"]),
        Index(value = ["centreCode"]),
        Index(value = ["examDate"]),
        Index(value = ["lanName"]),
        Index(value = ["syncStatus"]),
        Index(value = ["createdAt"])
    ]
)
data class SystemIncidentEntity(
    @PrimaryKey val id: String,
    val submissionId: String,
    val centreId: String?,
    val examSessionId: String?,
    val stateName: String,
    val districtName: String,
    val centreCode: String,
    val centreName: String,
    val examDate: String,
    val examName: String,
    val shiftName: String,
    val lanName: String,
    val macAddress: String,
    val ipAddress: String,
    val lockedSystemNumber: String,
    val newlyAllocatedSystemNumber: String?,
    val candidateShifted: Boolean,
    val incidentTime: String,
    val incidentTimestampMillis: Long,
    val reason: String,
    val itManagerName: String,
    val createdBy: String,
    val creatorName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String?,
    val syncStatus: String = "PENDING",
    val lastSyncError: String? = null,
    val scopeLevel: String = "CENTRE",
    val zoneId: String? = null,
    val stateId: String? = null,
    val cityId: String? = null,
    val status: String = "OPEN",
    val escalationLevel: String = "IT_MANAGER",
    val assignedTo: String? = null,
    val version: Long = 1L
) {
    fun toDomain(): SystemIncident {
        return SystemIncident(
            id = id,
            submissionId = submissionId,
            centreId = centreId,
            examSessionId = examSessionId,
            stateName = stateName,
            districtName = districtName,
            centreCode = centreCode,
            centreName = centreName,
            examDate = examDate,
            examName = examName,
            shiftName = shiftName,
            lanName = lanName,
            macAddress = macAddress,
            ipAddress = ipAddress,
            lockedSystemNumber = lockedSystemNumber,
            newlyAllocatedSystemNumber = newlyAllocatedSystemNumber,
            candidateShifted = candidateShifted,
            incidentTime = incidentTime,
            incidentTimestampMillis = incidentTimestampMillis,
            reason = reason,
            itManagerName = itManagerName,
            createdBy = createdBy,
            creatorName = creatorName,
            createdAt = createdAt,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            syncStatus = syncStatus,
            lastSyncError = lastSyncError,
            scopeLevel = scopeLevel,
            zoneId = zoneId,
            stateId = stateId,
            cityId = cityId,
            status = status,
            escalationLevel = escalationLevel,
            assignedTo = assignedTo,
            version = version
        )
    }

    companion object {
        fun fromDomain(domain: SystemIncident): SystemIncidentEntity {
            return SystemIncidentEntity(
                id = domain.id,
                submissionId = domain.submissionId,
                centreId = domain.centreId,
                examSessionId = domain.examSessionId,
                stateName = domain.stateName,
                districtName = domain.districtName,
                centreCode = domain.centreCode,
                centreName = domain.centreName,
                examDate = domain.examDate,
                examName = domain.examName,
                shiftName = domain.shiftName,
                lanName = domain.lanName,
                macAddress = domain.macAddress,
                ipAddress = domain.ipAddress,
                lockedSystemNumber = domain.lockedSystemNumber,
                newlyAllocatedSystemNumber = domain.newlyAllocatedSystemNumber,
                candidateShifted = domain.candidateShifted,
                incidentTime = domain.incidentTime,
                incidentTimestampMillis = domain.incidentTimestampMillis,
                reason = domain.reason,
                itManagerName = domain.itManagerName,
                createdBy = domain.createdBy,
                creatorName = domain.creatorName,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt,
                updatedBy = domain.updatedBy,
                syncStatus = domain.syncStatus,
                lastSyncError = domain.lastSyncError,
                scopeLevel = domain.scopeLevel,
                zoneId = domain.zoneId,
                stateId = domain.stateId,
                cityId = domain.cityId,
                status = domain.status,
                escalationLevel = domain.escalationLevel,
                assignedTo = domain.assignedTo,
                version = domain.version
            )
        }
    }
}
