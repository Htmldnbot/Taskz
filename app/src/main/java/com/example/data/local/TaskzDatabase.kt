package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        ProfileEntity::class,
        SyncMetadataEntity::class,
        OutboxEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TaskzDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var INSTANCE: TaskzDatabase? = null

        fun getInstance(context: Context): TaskzDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskzDatabase::class.java,
                    "taskz_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
