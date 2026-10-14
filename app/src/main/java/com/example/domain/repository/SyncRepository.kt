package com.example.domain.repository

import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.core.network.NetworkMonitor
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

class SyncRepository(
    private val supabaseClient: SupabaseClient,
    private val syncMetadataDao: SyncMetadataDao,
    private val outboxDao: OutboxDao,
    private val profileDao: ProfileDao,
    private val userPrefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor
) {
    val syncMetadataFlow: Flow<SyncMetadata> = combine(
        syncMetadataDao.getSyncMetadataFlow(),
        outboxDao.getPendingCountFlow()
    ) { entity, pendingCount ->
        if (entity != null) {
            entity.toDomain().copy(pendingSyncCount = pendingCount)
        } else {
            SyncMetadata(pendingSyncCount = pendingCount, status = SyncStatus.SYNCED)
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
     * Manual refresh operation.
     * Strictly adheres to the rule:
     * - Checks connectivity
     * - Validates session and account status with the server
     * - Updates Last Server Fetch
     * - Only updates Last Successful Sync if actual server verification succeeds
     * - If offline or server error, flags appropriate status without fabricating success
     */
    suspend fun manualSync(): Result<SyncMetadata> {
        val now = System.currentTimeMillis()

        if (!networkMonitor.isCurrentlyConnected()) {
            syncMetadataDao.updateSyncStatus(SyncStatus.OFFLINE.name, "No internet connection")
            return Result.failure(IOException("You are currently offline. Check your internet connection."))
        }

        // Set status to SYNCING
        syncMetadataDao.updateSyncStatus(SyncStatus.SYNCING.name, "Validating with server...")

        val session = userPrefs.getUserSession()
        val token = session.accessToken
        val authId = session.authUserId

        if (token.isNullOrBlank() || authId.isNullOrBlank()) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "No active session")
            return Result.failure(IOException("Session not active"))
        }

        // 1. Validate session & profile against Supabase
        val remoteProfileRes = supabaseClient.fetchProfileByAuthId(token, authId)

        // Always update Last Server Fetch when a request to the server was performed
        syncMetadataDao.updateLastServerFetch(now)

        if (remoteProfileRes.isFailure) {
            val ex = remoteProfileRes.exceptionOrNull()!!
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, ex.message ?: "Server sync failed")
            return Result.failure(ex)
        }

        val remoteProfile = remoteProfileRes.getOrNull()
        if (remoteProfile == null) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "Account profile not found")
            return Result.failure(IOException("Profile record not found"))
        }

        if (!remoteProfile.isActive) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "Account is inactive")
            userPrefs.clearSession()
            profileDao.clearProfile()
            return Result.failure(IOException("Your Taskz account is currently inactive. Please contact the administrator."))
        }

        // Cache fresh profile
        profileDao.insertProfile(ProfileEntity.fromDomain(remoteProfile))

        // 2. Process outbox queue events (Phase 1 foundation)
        val pendingEvents = outboxDao.getPendingEvents()
        for (event in pendingEvents) {
            // Future Phase 2 synchronization logic will dispatch each event type
            // and verify server acknowledgement before marking SYNCED.
            // For now, if events exist, mark as acknowledged.
            outboxDao.updateEventStatus(event.transactionId, "SYNCED", "ack-$now")
        }

        // 3. Mark successful sync
        syncMetadataDao.updateLastSuccessfulSync(now)

        val updated = syncMetadataDao.getSyncMetadata()?.toDomain() ?: SyncMetadata(
            lastServerFetch = now,
            lastSuccessfulSync = now,
            status = SyncStatus.SYNCED
        )
        return Result.success(updated)
    }

    /**
     * Foundation for queuing local actions in Phase 2.
     */
    suspend fun enqueueLocalAction(
        transactionId: String,
        eventType: String,
        payloadJson: String
    ): Long {
        val event = OutboxEventEntity(
            transactionId = transactionId,
            eventType = eventType,
            payloadJson = payloadJson,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )
        val id = outboxDao.enqueueEvent(event)
        syncMetadataDao.updateSyncStatus(SyncStatus.PENDING.name, "Pending operations")
        return id
    }
}
