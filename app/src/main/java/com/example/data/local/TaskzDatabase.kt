package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.example.data.local.dao.AuthorizationDao
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
import com.example.data.local.entity.CachedAuthContextEntity
import com.example.data.local.entity.CachedOrgNodeEntity
import com.example.data.local.entity.DispatchedNotificationKeyEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.ScheduledAlarmRecordEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.dao.SystemIncidentDao
import com.example.data.local.entity.SystemIncidentAuditEntity
import com.example.data.local.entity.SystemIncidentEntity
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
        ScheduledAlarmRecordEntity::class,
        SystemIncidentEntity::class,
        SystemIncidentAuditEntity::class,
        CachedAuthContextEntity::class,
        CachedOrgNodeEntity::class,
        DispatchedNotificationKeyEntity::class
    ],
    version = 6,
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
    abstract fun systemIncidentDao(): SystemIncidentDao
    abstract fun authorizationDao(): AuthorizationDao

    suspend fun clearAllUserScopedTables() {
        withTransaction {
            authorizationDao().clearAuthContext()
            authorizationDao().clearDispatchedNotificationKeys()
            profileDao().clearProfile()
            taskDao().clearAll()
            taskGroupDao().clearAll()
            shiftDao().clearAll()
            notificationDao().clearAll()
            taskResponseDao().clearAllResponses()
            taskResponseDao().clearAllAcknowledgements()
            scheduledAlarmDao().clearAll()
            systemIncidentDao().clearAll()
            systemIncidentDao().clearAllAudits()
            outboxDao().clearAll()
        }
    }

    suspend fun clearRoleScopeCacheForSwitch() {
        withTransaction {
            taskDao().clearAll()
            notificationDao().clearAll()
            systemIncidentDao().clearAll()
            systemIncidentDao().clearAllAudits()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskzDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
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
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN completionPolicy TEXT NOT NULL DEFAULT 'SINGLE_ASSIGNEE'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN responseFormSchemaJson TEXT NULL")
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN dynamicValuesJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN isLate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_task_responses ADD COLUMN delaySeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scheduled_alarm_records ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS system_incidents (
                        id TEXT PRIMARY KEY NOT NULL,
                        submissionId TEXT NOT NULL,
                        centreId TEXT,
                        examSessionId TEXT,
                        stateName TEXT NOT NULL,
                        districtName TEXT NOT NULL,
                        centreCode TEXT NOT NULL,
                        centreName TEXT NOT NULL,
                        examDate TEXT NOT NULL,
                        examName TEXT NOT NULL,
                        shiftName TEXT NOT NULL,
                        lanName TEXT NOT NULL,
                        macAddress TEXT NOT NULL,
                        ipAddress TEXT NOT NULL,
                        lockedSystemNumber TEXT NOT NULL,
                        newlyAllocatedSystemNumber TEXT,
                        candidateShifted INTEGER NOT NULL,
                        incidentTime TEXT NOT NULL,
                        incidentTimestampMillis INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        itManagerName TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        creatorName TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        updatedBy TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        lastSyncError TEXT
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_system_incidents_submissionId ON system_incidents (submissionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_centreId ON system_incidents (centreId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_centreCode ON system_incidents (centreCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_examDate ON system_incidents (examDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_lanName ON system_incidents (lanName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_syncStatus ON system_incidents (syncStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incidents_createdAt ON system_incidents (createdAt)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS system_incident_audits (
                        id TEXT PRIMARY KEY NOT NULL,
                        incidentId TEXT NOT NULL,
                        changedBy TEXT NOT NULL,
                        changedByName TEXT,
                        changedAt INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        previousValues TEXT,
                        newValues TEXT
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incident_audits_incidentId ON system_incident_audits (incidentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_system_incident_audits_changedAt ON system_incident_audits (changedAt)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN email TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN activeRoleCode TEXT NOT NULL DEFAULT 'IT_MANAGER'")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN primaryRoleCode TEXT NOT NULL DEFAULT 'IT_MANAGER'")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN assignedRoleCodesCsv TEXT NOT NULL DEFAULT 'IT_MANAGER'")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN zoneId TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN zoneName TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN stateId TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN stateName TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN cityId TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN cityName TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN centreId TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN centreCode TEXT NULL")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN roleVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE cached_profile ADD COLUMN scopeVersion INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN scopeLevel TEXT NOT NULL DEFAULT 'CENTRE'")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN zoneId TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN stateId TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN cityId TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN centreId TEXT NULL")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN targetRoleCodesCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE cached_tasks ADD COLUMN localSyncState TEXT NOT NULL DEFAULT 'SYNCED'")

                db.execSQL("ALTER TABLE system_incidents ADD COLUMN scopeLevel TEXT NOT NULL DEFAULT 'CENTRE'")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN zoneId TEXT NULL")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN stateId TEXT NULL")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN cityId TEXT NULL")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN escalationLevel TEXT NOT NULL DEFAULT 'IT_MANAGER'")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN assignedTo TEXT NULL")
                db.execSQL("ALTER TABLE system_incidents ADD COLUMN version INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE cached_notifications ADD COLUMN category TEXT NOT NULL DEFAULT 'task_update'")
                db.execSQL("ALTER TABLE cached_notifications ADD COLUMN incidentId TEXT NULL")
                db.execSQL("ALTER TABLE cached_notifications ADD COLUMN eventKey TEXT NULL")
                db.execSQL("ALTER TABLE cached_notifications ADD COLUMN deepLink TEXT NULL")
                db.execSQL("ALTER TABLE cached_notifications ADD COLUMN scopeLevel TEXT NULL")

                db.execSQL("ALTER TABLE outbox_events ADD COLUMN activeRole TEXT NOT NULL DEFAULT 'IT_MANAGER'")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN scopeLevel TEXT NOT NULL DEFAULT 'CENTRE'")
                db.execSQL("ALTER TABLE outbox_events ADD COLUMN scopeId TEXT NULL")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_auth_context (
                        userId TEXT PRIMARY KEY NOT NULL,
                        employeeId TEXT NOT NULL,
                        fullName TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        activeRoleCode TEXT NOT NULL,
                        primaryRoleCode TEXT NOT NULL,
                        assignedRolesJson TEXT NOT NULL,
                        effectiveScopeLevel TEXT NOT NULL,
                        zoneId TEXT,
                        zoneName TEXT,
                        stateId TEXT,
                        stateName TEXT,
                        cityId TEXT,
                        cityName TEXT,
                        centreId TEXT,
                        centreCode TEXT,
                        roleVersion INTEGER NOT NULL,
                        scopeVersion INTEGER NOT NULL,
                        lastUpdatedMillis INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_org_nodes (
                        id TEXT PRIMARY KEY NOT NULL,
                        nodeType TEXT NOT NULL,
                        code TEXT NOT NULL,
                        name TEXT NOT NULL,
                        parentZoneId TEXT,
                        parentStateId TEXT,
                        parentCityId TEXT,
                        externalCode TEXT,
                        districtName TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dispatched_notification_keys (
                        eventKey TEXT PRIMARY KEY NOT NULL,
                        userId TEXT NOT NULL,
                        dispatchedAtMillis INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): TaskzDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskzDatabase::class.java,
                    "taskz_local.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
