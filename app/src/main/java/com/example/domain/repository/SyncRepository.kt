package com.example.domain.repository

import android.content.Context
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.core.network.NetworkMonitor
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.remote.SupabaseClient
import com.example.sync.SyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.util.UUID

class SyncRepository(
    private val supabaseClient: SupabaseClient,
    private val syncMetadataDao: SyncMetadataDao,
    private val outboxDao: OutboxDao,
    private val profileDao: ProfileDao,
    private val userPrefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor,
    private val context: Context = TaskzApplication.instance
) {
    val syncMetadataFlow: Flow<SyncMetadata> = combine(
        syncMetadataDao.getSyncMetadataFlow(),
        outboxDao.getPendingCountFlow()
    ) { entity, pendingCount ->
        if (entity != null) {
            val base = entity.toDomain().copy(pendingSyncCount = pendingCount)
            if (pendingCount > 0 && base.status == SyncStatus.SYNCED) {
                base.copy(status = SyncStatus.PENDING)
            } else {
                base
            }
        } else {
            SyncMetadata(
                pendingSyncCount = pendingCount,
                status = if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED
            )
        }
    }

    /**
     * Initializes default sync metadata record if absent.
     */
    suspend fun initializeSyncState() {
        val existing = syncMetadataDao.getSyncMetadata()
        if (existing == null) {
            syncMetadataDao.setSyncMetadata(
                SyncMetadataEntity(
                    id = 1,
                    lastServerFetch = null,
                    lastSuccessfulSync = null,
                    pendingSyncCount = 0,
                    status = SyncStatus.SYNCED.name,
                    statusMessage = null
                )
            )
        }
    }

    /**
     * Manual refresh operation against Supabase (single authoritative backend).
     */
    suspend fun manualSync(): Result<SyncMetadata> {
        val now = System.currentTimeMillis()

        if (!networkMonitor.isCurrentlyConnected()) {
            syncMetadataDao.updateSyncStatus(
                SyncStatus.OFFLINE.name,
                "Offline — Operations will sync automatically when connected"
            )
            return Result.failure(IOException("You are currently offline. Check your internet connection."))
        }

        syncMetadataDao.updateSyncStatus(SyncStatus.SYNCING.name, "Synchronizing with server...")

        val session = userPrefs.getUserSession()
        val userId = session.userId

        if (userId.isNullOrBlank() || !session.isAuthenticated) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "Authentication required: No active session")
            return Result.failure(IOException("Session not active"))
        }

        val authRepo = TaskzApplication.instance.authRepository

        // 1. Validate or refresh Supabase access token before sending server calls
        val tokenRes = authRepo.getValidAccessToken()
        if (tokenRes.isFailure) {
            val err = tokenRes.exceptionOrNull()!!
            if (authRepo.isNetworkFailure(err)) {
                syncMetadataDao.updateSyncStatus(
                    SyncStatus.OFFLINE.name,
                    "Offline — Unable to reach server, operations preserved"
                )
            } else {
                syncMetadataDao.updateSyncStatus(
                    SyncStatus.ERROR.name,
                    "Authentication required: Session expired. Please log in again."
                )
            }
            return Result.failure(err)
        }

        val token = tokenRes.getOrThrow()

        val cachedProfile = profileDao.getCachedProfile()?.toDomain()
        if (cachedProfile != null && !cachedProfile.isActive) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "Account is inactive")
            userPrefs.clearSession()
            profileDao.clearProfile()
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        // 2. Verify live connection & record fetch timestamp with Supabase
        val serverFetchRes = supabaseClient.recordServerFetch(token)
        if (serverFetchRes.isFailure) {
            val ex = serverFetchRes.exceptionOrNull()
            android.util.Log.w("TaskzSync", "Server fetch check warning: ${ex?.message}")
        }

        // 3. Push pending offline operations to Supabase, then pull authoritative server state
        try {
            val taskRepo = TaskzApplication.instance.taskRepository
            val incidentRepo = TaskzApplication.instance.systemIncidentRepository

            taskRepo.syncPendingTasks()
            incidentRepo.syncPendingIncidents()

            taskRepo.syncGroupsFromServer(token)
            taskRepo.syncShiftsFromServer(token)
            taskRepo.syncTasksFromServer(token)
            incidentRepo.syncIncidentsFromServer()
        } catch (e: Exception) {
            android.util.Log.w("TaskzSync", "Data sync warning: ${e.message}")
        }

        val pendingCount = outboxDao.getPendingEventsForUser(userId).size
        syncMetadataDao.updateLastServerFetch(now)
        if (pendingCount > 0) {
            syncMetadataDao.updateSyncStatus(SyncStatus.PENDING.name, "$pendingCount pending operations awaiting sync")
        } else {
            syncMetadataDao.updateLastSuccessfulSync(now)
            syncMetadataDao.updateSyncStatus(SyncStatus.SYNCED.name, "Verified with Server")
        }

        val updated = syncMetadataDao.getSyncMetadata()?.toDomain() ?: SyncMetadata(
            lastServerFetch = now,
            lastSuccessfulSync = if (pendingCount == 0) now else null,
            pendingSyncCount = pendingCount,
            status = if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED,
            statusMessage = if (pendingCount > 0) "$pendingCount pending operations awaiting sync" else "Verified with Server"
        )
        return Result.success(updated)
    }

    /**
     * Enqueue a durable local action in outbox.
     */
    suspend fun enqueueLocalAction(
        userId: String,
        taskId: String,
        operationType: String,
        payloadJson: String,
        eventId: String = UUID.randomUUID().toString()
    ): Long {
        val event = OutboxEventEntity(
            eventId = eventId,
            userId = userId,
            taskId = taskId,
            operationType = operationType,
            payloadJson = payloadJson,
            syncStatus = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        val id = outboxDao.enqueueEvent(event)
        syncMetadataDao.updateSyncStatus(SyncStatus.PENDING.name, "Pending operations")

        if (networkMonitor.isCurrentlyConnected()) {
            SyncWorker.enqueueSync(context)
        }
        return id
    }
}
