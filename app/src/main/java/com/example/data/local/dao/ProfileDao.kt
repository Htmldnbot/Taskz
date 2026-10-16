package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM cached_profile LIMIT 1")
    fun getCachedProfileFlow(): Flow<ProfileEntity?>

    @Query("SELECT * FROM cached_profile LIMIT 1")
    suspend fun getCachedProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>)

    @Query("SELECT * FROM cached_profile WHERE isActive = 1 ORDER BY fullName ASC")
    fun getAllProfilesFlow(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM cached_profile WHERE fullName LIKE '%' || :query || '%' OR employeeId LIKE '%' || :query || '%'")
    suspend fun searchProfiles(query: String): List<ProfileEntity>

    @Query("DELETE FROM cached_profile")
    suspend fun clearProfile()
}
