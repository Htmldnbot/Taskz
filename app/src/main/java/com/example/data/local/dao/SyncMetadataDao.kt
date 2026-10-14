package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    fun getSyncMetadataFlow(): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1 LIMIT 1")
    suspend fun getSyncMetadata(): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncMetadata(metadata: SyncMetadataEntity)

    @Query("UPDATE sync_metadata SET lastServerFetch = :timestamp WHERE id = 1")
    suspend fun updateLastServerFetch(timestamp: Long)

    @Query("UPDATE sync_metadata SET lastSuccessfulSync = :timestamp, status = 'SYNCED', statusMessage = null WHERE id = 1")
    suspend fun updateLastSuccessfulSync(timestamp: Long)

    @Query("UPDATE sync_metadata SET status = :status, statusMessage = :message WHERE id = 1")
    suspend fun updateSyncStatus(status: String, message: String?)

    @Query("UPDATE sync_metadata SET pendingSyncCount = :count WHERE id = 1")
    suspend fun updatePendingCount(count: Int)
}
