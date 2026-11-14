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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    @OptIn(ExperimentalCoroutinesApi::class)
    private val userPendingEventsFlow: Flow<List<OutboxEventEntity>> =
        userPrefs.userSessionFlow
            .map { it.userId.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList())
                else outboxDao.getUnsyncedEventsForUserFlow(uid)
            }

    val syncMetadataFlow: Flow<SyncMetadata> = combine(
        syncMetadataDao.getSyncMetadataFlow(),
        userPendingEventsFlow
    ) { entity, unsyncedEvents ->
        val pendingCount = unsyncedEvents.count {
            it.syncStatus in listOf("PENDING", "SENDING", "FAILED_RETRYABLE")
        }
        val latestError = unsyncedEvents.firstNotNullOfOrNull { it.lastError?.takeIf { e -> e.isNotBlank() } }
        val permFailedCount = unsyncedEvents.count { it.syncStatus == "FAILED_PERMANENT" }

        if (entity != null) {
            val base = entity.toDomain().copy(pendingSyncCount = pendingCount)
            when {
                pendingCount > 0 && base.status == SyncStatus.SYNCED -> {
                    val msg = latestError?.let { "$pendingCount pending — $it" }
                        ?: "$pendingCount pending operations awaiting sync"
                    base.copy(status = SyncStatus.PENDING, statusMessage = msg)
                }
                pendingCount == 0 && permFailedCount > 0 && base.status == SyncStatus.SYNCED -> {
                    base.copy(
                        status = SyncStatus.ERROR,
                        statusMessage = latestError ?: "$permFailedCount item(s) could not sync"
                    )
                }
                pendingCount > 0 && latestError != null && base.status == SyncStatus.PENDING -> {
                    base.copy(statusMessage = "$pendingCount pending — $latestError")
                }
                else -> base
            }
        } else {
            SyncMetadata(
                pendingSyncCount = pendingCount,
                status = if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED,
                statusMessage = latestError
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

    suspend fun retryStuckAndFailedEvents(): Result<SyncMetadata> {
        val userId = userPrefs.getUserSession().userId
        if (!userId.isNullOrBlank()) {
            outboxDao.resetAllUnsyncedToPendingForUser(userId)
        }
        return manualSync()
    }

    suspend fun clearPermanentFailedEvents() {
        val userId = userPrefs.getUserSession().userId
        if (!userId.isNullOrBlank()) {
            outboxDao.clearPermanentFailedForUser(userId)
            val remaining = outboxDao.getPendingEventsForUser(userId)
            if (remaining.isEmpty()) {
                syncMetadataDao.updateSyncStatus(SyncStatus.SYNCED.name, "Verified with Server")
            }
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

        outboxDao.deleteLegacyPreMigrationEvents(userId)

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

        val pendingEvents = outboxDao.getPendingEventsForUser(userId)
        val pendingCount = pendingEvents.size
        val latestError = pendingEvents.firstNotNullOfOrNull { it.lastError?.takeIf { e -> e.isNotBlank() } }
        syncMetadataDao.updateLastServerFetch(now)
        val statusMsg = if (pendingCount > 0) {
            if (latestError != null) "$pendingCount pending — $latestError"
            else "$pendingCount pending operations awaiting sync"
        } else {
            "Verified with Server"
        }

        if (pendingCount > 0) {
            syncMetadataDao.updateSyncStatus(SyncStatus.PENDING.name, statusMsg)
        } else {
            syncMetadataDao.updateLastSuccessfulSync(now)
            syncMetadataDao.updateSyncStatus(SyncStatus.SYNCED.name, statusMsg)
        }

        val updated = syncMetadataDao.getSyncMetadata()?.toDomain() ?: SyncMetadata(
            lastServerFetch = now,
            lastSuccessfulSync = if (pendingCount == 0) now else null,
            pendingSyncCount = pendingCount,
            status = if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED,
            statusMessage = statusMsg
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
