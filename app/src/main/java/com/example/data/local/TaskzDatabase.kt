package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 4,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2: Base schema additions
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN taskType TEXT NOT NULL DEFAULT 'SIMPLE'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN importance TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN urgencyType TEXT NOT NULL DEFAULT 'BEFORE_DEADLINE'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN executionTime TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN targetPercentage INTEGER NULL DEFAULT 100")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN repeatIntervalMinutes INTEGER NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN alarmType TEXT NOT NULL DEFAULT 'SOUND_ALERT'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN advanceRemindersMinutesCsv TEXT NOT NULL DEFAULT '0,5'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN remindUntilCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN overdueReminderIntervalMinutes INTEGER NULL DEFAULT 5")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN maxOverdueReminders INTEGER NULL DEFAULT 3")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN responseType TEXT NOT NULL DEFAULT 'COMPLETED_NOT_COMPLETED'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN lanTargetListCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN currentProgress INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN lastProgressUpdate TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN isAcknowledged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN acknowledgedAt TEXT NULL")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_task_responses (
                        id TEXT PRIMARY KEY NOT NULL,
                        taskId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        employeeName TEXT,
                        lanNumber TEXT,
                        responseType TEXT NOT NULL,
                        responseValue TEXT,
                        percentageValue INTEGER,
                        numericValue1 REAL,
                        numericValue2 REAL,
                        textValue TEXT,
                        submittedAt TEXT NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_task_acknowledgements (
                        id TEXT PRIMARY KEY NOT NULL,
                        taskId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        deviceTimestamp INTEGER NOT NULL,
                        acknowledgedAt TEXT NOT NULL,
                        syncStatus TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scheduled_alarm_records (
                        alarmId INTEGER PRIMARY KEY NOT NULL,
                        taskId TEXT NOT NULL,
                        taskTitle TEXT NOT NULL,
                        triggerAtMillis INTEGER NOT NULL,
                        alarmType TEXT NOT NULL,
                        importance TEXT NOT NULL,
                        reminderLabel TEXT NOT NULL,
                        isFired INTEGER NOT NULL DEFAULT 0,
                        isCancelled INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add completionPolicy and responseFormSchemaJson to cached_tasks
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN completionPolicy TEXT NOT NULL DEFAULT 'SINGLE_ASSIGNEE'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN responseFormSchemaJson TEXT NULL")

                // Add dynamic values, isLate, and delaySeconds to cached_task_responses
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN dynamicValuesJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN isLate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN delaySeconds INTEGER NOT NULL DEFAULT 0")

                // Add userId to scheduled_alarm_records for account-specific alarm isolation
                db.execSQL("ALTER TABLE scheduled_alarm_records ADD COLUMN userId TEXT NOT NULL DEFAULT ''")

                // Add durable outbox columns to outbox_events
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN eventId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN taskId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN operationType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN lastAttemptAt INTEGER NULL")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN lastError TEXT NULL")
            }
        }

        fun getInstance(context: Context): TaskzDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskzDatabase::class.java,
                    "taskz_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
