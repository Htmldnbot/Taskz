package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.TaskAlarmReceiver
import com.example.alarm.TaskAlarmScheduler
import com.example.core.model.AlarmType
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.network.SupabaseConfig
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.OutboxEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileMobileSyncAndAlarmTest {

    private lateinit var context: Context
    private lateinit var db: TaskzDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TaskzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mobileFormatCandidates_ordersCanonicalThenLocalFormats() {
        assertEquals(
            listOf("+917550448793", "7550448793", "917550448793"),
            SupabaseConfig.mobileFormatCandidates("+917550448793")
        )

        assertEquals("+917550448793", SupabaseConfig.normalizeMobile("7550448793"))
        assertEquals("+917550448793", SupabaseConfig.normalizeMobile("07550448793"))
        assertEquals(
            listOf("+917550448793", "7550448793", "917550448793"),
            SupabaseConfig.mobileFormatCandidates("7550448793")
        )

        val usCandidates = SupabaseConfig.mobileFormatCandidates("+15551234567")
        assertEquals("+15551234567", usCandidates.first())
    }

    @Test
    fun outboxDao_scopesPendingCountsByUserAndCleansLegacyRows() = runTest {
        val dao = db.outboxDao()
        val activeUid = "11111111-1111-4111-8111-111111111111"
        val otherUid = "22222222-2222-4222-8222-222222222222"

        dao.enqueueEvent(
            OutboxEventEntity(
                eventId = "evt-active-pending",
                userId = activeUid,
                taskId = "task-1",
                operationType = "TASK_RESPONSE",
                syncStatus = "PENDING"
            )
        )
        dao.enqueueEvent(
            OutboxEventEntity(
                eventId = "evt-active-perm-fail",
                userId = activeUid,
                taskId = "task-1",
                operationType = "TASK_RESPONSE",
                syncStatus = "FAILED_PERMANENT",
                lastError = "invalid input syntax"
            )
        )
        dao.enqueueEvent(
            OutboxEventEntity(
                eventId = "evt-other-user",
                userId = otherUid,
                taskId = "task-2",
                operationType = "TASK_CREATION",
                syncStatus = "PENDING"
            )
        )
        dao.enqueueEvent(
            OutboxEventEntity(
                eventId = "evt-legacy-firebase",
                userId = "firebase_old_user",
                taskId = "task-3",
                operationType = "TASK_RESPONSE",
                syncStatus = "PENDING"
            )
        )
        dao.enqueueEvent(
            OutboxEventEntity(
                eventId = "evt-legacy-offline",
                userId = "offline_temp_user",
                taskId = "task-4",
                operationType = "TASK_ACKNOWLEDGEMENT",
                syncStatus = "PENDING"
            )
        )

        // User-scoped flows exclude other users and legacy users
        val activeUnsynced = dao.getUnsyncedEventsForUserFlow(activeUid).first()
        assertEquals(2, activeUnsynced.size)
        assertEquals(1, dao.getPendingCountFlowForUser(activeUid).first())
        assertEquals(1, dao.getPermanentFailedCountFlowForUser(activeUid).first())

        // Clean legacy pre-migration rows without touching activeUid or otherUid
        dao.deleteLegacyPreMigrationEvents(activeUid)
        val remainingGlobal = dao.getPendingEvents()
        assertFalse(remainingGlobal.any { it.userId.startsWith("firebase_") || it.userId.startsWith("offline_") })
        assertTrue(remainingGlobal.any { it.userId == otherUid })
        assertTrue(remainingGlobal.any { it.userId == activeUid })

        // Clear permanent failed for active user preserves PENDING event
        dao.clearPermanentFailedForUser(activeUid)
        assertEquals(0, dao.getPermanentFailedCountFlowForUser(activeUid).first())
        assertEquals(1, dao.getUnsyncedEventsForUserFlow(activeUid).first().size)

        // Reset unsynced to pending transitions SENDING/FAILED_RETRYABLE/FAILED_PERMANENT cleanly
        val activeEventId = dao.getUnsyncedEventsForUserFlow(activeUid).first().first().id
        dao.markEventFailed(activeEventId, "timeout", "FAILED_RETRYABLE", System.currentTimeMillis())
        dao.resetAllUnsyncedToPendingForUser(activeUid)
        assertEquals("PENDING", dao.getUnsyncedEventsForUserFlow(activeUid).first().first().syncStatus)
    }

    @Test
    fun notificationChannels_configureAlarmAudioAndPublicLockscreenVisibility() {
        TaskAlarmReceiver.createNotificationChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val criticalChannel = nm.getNotificationChannel(TaskAlarmReceiver.CHANNEL_ID_CRITICAL_ALARM)
        assertNotNull(criticalChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, criticalChannel!!.importance)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, criticalChannel.lockscreenVisibility)
        assertEquals(AudioAttributes.USAGE_ALARM, criticalChannel.audioAttributes.usage)
        assertTrue(criticalChannel.shouldVibrate())

        val highChannel = nm.getNotificationChannel(TaskAlarmReceiver.CHANNEL_ID_HIGH_ALARM)
        assertNotNull(highChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, highChannel!!.importance)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, highChannel.lockscreenVisibility)
        assertEquals(AudioAttributes.USAGE_ALARM, highChannel.audioAttributes.usage)
        assertTrue(highChannel.shouldVibrate())
    }

    @Test
    fun alarmScheduler_usesAlarmClockForHighAndCriticalTasks() = runTest {
        val scheduler = TaskAlarmScheduler(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)

        val futureIso = java.time.Instant.now().plusSeconds(3600).toString()
        val highTask = TaskItem(
            id = "task-high-alarm-test",
            title = "High Priority Gate Verification",
            taskType = TaskType.TIME_SPECIFIC,
            importance = TaskImportance.HIGH,
            alarmType = AlarmType.FULL_SCREEN_ALARM,
            executionTime = futureIso,
            advanceRemindersMinutes = listOf(0)
        )

        scheduler.scheduleAlarmsForTask(highTask, userId = "11111111-1111-4111-8111-111111111111")

        val alarmClock = alarmManager.nextAlarmClock
        assertNotNull("HIGH priority task must schedule setAlarmClock for Samsung Doze & lockscreen reliability", alarmClock)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        val savedAlarms = TaskzDatabase.getInstance(context).scheduledAlarmDao().getAlarmsForTask(highTask.id)
        assertEquals(1, savedAlarms.size)
        assertEquals("HIGH", savedAlarms.first().importance)

        scheduler.cancelAlarmsForTask(highTask.id)
    }
}
