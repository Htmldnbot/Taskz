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

    @Query("DELETE FROM cached_profile")
    suspend fun clearProfile()
}
