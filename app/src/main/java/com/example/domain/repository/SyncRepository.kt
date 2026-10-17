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
import com.example.data.local.entity.ProfileEntity
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
     * Manual refresh operation.
     * Strictly adheres to the rule:
     * - Checks connectivity; never marks success merely because internet exists.
     * - Validates session and account status with the server.
     * - Calls real server RPC record_server_fetch() to log fetch in user_sync_state.
     * - Updates Last Server Fetch.
     * - Triggers WorkManager outbox sync.
     * - "Last Successful Sync" only updates when server acknowledgement of EVERY operation is confirmed.
     */
    suspend fun manualSync(): Result<SyncMetadata> {
        val now = System.currentTimeMillis()

        if (!networkMonitor.isCurrentlyConnected()) {
            syncMetadataDao.updateSyncStatus(SyncStatus.OFFLINE.name, "No internet connection")
            return Result.failure(IOException("You are currently offline. Check your internet connection."))
        }

        // Set status to SYNCING
        syncMetadataDao.updateSyncStatus(SyncStatus.SYNCING.name, "Synchronizing with server...")

        val session = userPrefs.getUserSession()
        val token = session.accessToken
        val userId = session.userId

        if (token.isNullOrBlank() || userId.isNullOrBlank()) {
            syncMetadataDao.updateSyncStatus(SyncStatus.ERROR.name, "No active session")
            return Result.failure(IOException("Session not active"))
        }

        // 1. Validate session & profile against Supabase
        val remoteProfileRes = supabaseClient.fetchProfileById(token, userId)

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

        // Record fetch in Supabase server sync state via RPC
        supabaseClient.recordServerFetch(token)

        // Cache fresh profile
        val signedUrl = if (!remoteProfile.profilePhotoPath.isNullOrBlank()) {
            supabaseClient.getSignedProfilePhotoUrl(token, remoteProfile.profilePhotoPath).getOrNull()
        } else {
            null
        }
        profileDao.insertProfile(ProfileEntity.fromDomain(remoteProfile.copy(avatarSignedUrl = signedUrl)))

        // 2. Trigger WorkManager to flush outbox events for this active user
        SyncWorker.enqueueSync(context)

        val pendingCount = outboxDao.getPendingEventsForUser(userId).size

        if (pendingCount > 0) {
            syncMetadataDao.updateSyncStatus(SyncStatus.PENDING.name, "$pendingCount pending operations awaiting sync")
        } else {
            syncMetadataDao.updateLastSuccessfulSync(now)
            syncMetadataDao.updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
        }

        val updated = syncMetadataDao.getSyncMetadata()?.toDomain() ?: SyncMetadata(
            lastServerFetch = now,
            lastSuccessfulSync = if (pendingCount == 0) now else null,
            pendingSyncCount = pendingCount,
            status = if (pendingCount > 0) SyncStatus.PENDING else SyncStatus.SYNCED
        )
        return Result.success(updated)
    }

    /**
     * Enqueue a durable local action in outbox.
     * Never transition to SYNCED without real server ACK.
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

        // Trigger WorkManager if network is connected
        if (networkMonitor.isCurrentlyConnected()) {
            SyncWorker.enqueueSync(context)
        }
        return id
    }
}
