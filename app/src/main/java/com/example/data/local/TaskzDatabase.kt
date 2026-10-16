package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.ScheduledAlarmDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.TaskGroupDao
import com.example.data.local.dao.TaskResponseDao
import com.example.data.local.dao.WorkRoleDao
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.ScheduledAlarmRecordEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskGroupEntity
import com.example.data.local.entity.TaskResponseEntity
import com.example.data.local.entity.WorkRoleEntity

@Database(
    entities = [
        ProfileEntity::class,
        SyncMetadataEntity::class,
        OutboxEventEntity::class,
        TaskEntity::class,
        TaskGroupEntity::class,
        ShiftEntity::class,
        WorkRoleEntity::class,
        NotificationEntity::class,
        TaskResponseEntity::class,
        TaskAcknowledgementEntity::class,
        ScheduledAlarmRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TaskzDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun outboxDao(): OutboxDao
    abstract fun taskDao(): TaskDao
    abstract fun taskGroupDao(): TaskGroupDao
    abstract fun shiftDao(): ShiftDao
    abstract fun workRoleDao(): WorkRoleDao
    abstract fun notificationDao(): NotificationDao
    abstract fun taskResponseDao(): TaskResponseDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao

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
