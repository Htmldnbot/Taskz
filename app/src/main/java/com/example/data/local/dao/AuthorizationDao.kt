package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.CachedAuthContextEntity
import com.example.data.local.entity.CachedOrgNodeEntity
import com.example.data.local.entity.DispatchedNotificationKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorizationDao {
    @Query("SELECT * FROM cached_auth_context WHERE userId = :userId LIMIT 1")
    fun getAuthContextFlow(userId: String): Flow<CachedAuthContextEntity?>

    @Query("SELECT * FROM cached_auth_context WHERE userId = :userId LIMIT 1")
    suspend fun getAuthContext(userId: String): CachedAuthContextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthContext(entity: CachedAuthContextEntity)

    @Query("DELETE FROM cached_auth_context")
    suspend fun clearAuthContext()

    @Query("SELECT * FROM cached_org_nodes ORDER BY nodeType, name")
    fun getAllOrgNodesFlow(): Flow<List<CachedOrgNodeEntity>>

    @Query("SELECT * FROM cached_org_nodes ORDER BY nodeType, name")
    suspend fun getAllOrgNodes(): List<CachedOrgNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrgNodes(nodes: List<CachedOrgNodeEntity>)

    @Query("DELETE FROM cached_org_nodes")
    suspend fun clearOrgNodes()

    @Query("SELECT EXISTS(SELECT 1 FROM dispatched_notification_keys WHERE eventKey = :eventKey)")
    suspend fun hasDispatchedNotification(eventKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordDispatchedNotification(entry: DispatchedNotificationKeyEntity)

    @Query("DELETE FROM dispatched_notification_keys WHERE dispatchedAtMillis < :cutoffMillis")
    suspend fun pruneOldNotificationKeys(cutoffMillis: Long)

    @Query("DELETE FROM dispatched_notification_keys")
    suspend fun clearDispatchedNotificationKeys()
}
