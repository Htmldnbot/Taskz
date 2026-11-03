package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SystemIncidentAuditEntity
import com.example.data.local.entity.SystemIncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemIncidentDao {

    @Query("SELECT * FROM system_incidents ORDER BY createdAt DESC")
    fun getAllIncidentsFlow(): Flow<List<SystemIncidentEntity>>

    @Query("SELECT * FROM system_incidents WHERE createdBy = :userId ORDER BY createdAt DESC")
    fun getIncidentsForUserFlow(userId: String): Flow<List<SystemIncidentEntity>>

    @Query("SELECT * FROM system_incidents WHERE centreId = :centreId ORDER BY createdAt DESC")
    fun getIncidentsForCentreFlow(centreId: String): Flow<List<SystemIncidentEntity>>

    @Query("SELECT * FROM system_incidents WHERE id = :id LIMIT 1")
    suspend fun getIncidentById(id: String): SystemIncidentEntity?

    @Query("SELECT * FROM system_incidents WHERE submissionId = :submissionId LIMIT 1")
    suspend fun getIncidentBySubmissionId(submissionId: String): SystemIncidentEntity?

    @Query("SELECT * FROM system_incidents WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingIncidents(): List<SystemIncidentEntity>

    @Query("SELECT COUNT(*) FROM system_incidents WHERE syncStatus = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    /**
     * Get the most recent successfully saved IT Manager name for a given user and centre.
     */
    @Query("SELECT itManagerName FROM system_incidents WHERE createdBy = :userId AND centreId = :centreId AND itManagerName != '' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestItManagerName(userId: String, centreId: String): String?

    /**
     * Get the most recent LAN name for a given user and centre.
     */
    @Query("SELECT lanName FROM system_incidents WHERE createdBy = :userId AND centreId = :centreId AND lanName != '' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestLanName(userId: String, centreId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(entity: SystemIncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidents(entities: List<SystemIncidentEntity>)

    @Update
    suspend fun updateIncident(entity: SystemIncidentEntity)

    @Query("UPDATE system_incidents SET syncStatus = :status, lastSyncError = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, error: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM system_incidents WHERE id = :id")
    suspend fun deleteIncident(id: String)

    // Audit logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: SystemIncidentAuditEntity)

    @Query("SELECT * FROM system_incident_audits WHERE incidentId = :incidentId ORDER BY changedAt DESC")
    fun getAuditsForIncidentFlow(incidentId: String): Flow<List<SystemIncidentAuditEntity>>

    @Query("SELECT * FROM system_incident_audits WHERE incidentId = :incidentId ORDER BY changedAt DESC")
    suspend fun getAuditsForIncident(incidentId: String): List<SystemIncidentAuditEntity>
}
