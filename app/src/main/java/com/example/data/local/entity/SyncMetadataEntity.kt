package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1, // Singleton row
    val lastServerFetch: Long?,
    val lastSuccessfulSync: Long?,
    val pendingSyncCount: Int,
    val status: String,
    val statusMessage: String?
) {
    fun toDomain(): SyncMetadata = SyncMetadata(
        lastServerFetch = lastServerFetch,
        lastSuccessfulSync = lastSuccessfulSync,
        pendingSyncCount = pendingSyncCount,
        status = try {
            SyncStatus.valueOf(status)
        } catch (_: Exception) {
            SyncStatus.SYNCED
        },
        statusMessage = statusMessage
    )

    companion object {
        fun fromDomain(domain: SyncMetadata): SyncMetadataEntity = SyncMetadataEntity(
            id = 1,
            lastServerFetch = domain.lastServerFetch,
            lastSuccessfulSync = domain.lastSuccessfulSync,
            pendingSyncCount = domain.pendingSyncCount,
            status = domain.status.name,
            statusMessage = domain.statusMessage
        )
    }
}
