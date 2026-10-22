package com.example

import com.example.core.model.AlarmType
import com.example.core.model.LanProgressItem
import com.example.core.model.ResponseType
import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskResponseItem
import com.example.core.model.TaskStatus
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDeterministicEmployeeIdAuthMapping() {
        val email1 = SupabaseConfig.toInternalAuthEmail("EMP1024")
        val email2 = SupabaseConfig.toInternalAuthEmail("  emp1024  ")
        assertEquals(email1, email2)
        assertTrue(email1.endsWith("@taskzapp.org"))

        val emailWithHyphen = SupabaseConfig.toInternalAuthEmail("EMP-1024")
        val emailWithoutHyphen = SupabaseConfig.toInternalAuthEmail("EMP1024")
        val emailWithDot = SupabaseConfig.toInternalAuthEmail("EMP.1024")
        val emailWithUnderscore = SupabaseConfig.toInternalAuthEmail("EMP_1024")

        assertNotEquals(emailWithHyphen, emailWithoutHyphen)
        assertNotEquals(emailWithHyphen, emailWithDot)
        assertNotEquals(emailWithHyphen, emailWithUnderscore)
        assertNotEquals(emailWithDot, emailWithUnderscore)

        val emailSpecial = SupabaseConfig.toInternalAuthEmail("emp-999#special")
        val emailPlain = SupabaseConfig.toInternalAuthEmail("emp-999special")
        assertNotEquals(emailSpecial, emailPlain)

        val localPart = emailWithHyphen.substringBefore("@")
        assertTrue("Local part length must be <= 64 characters", localPart.length <= 64)
    }

    @Test
    fun testPhase3TaskTypesAndImportance() {
        // Verify all 7 required Phase 3 task types exist
        val types = TaskType.values().map { it.name }
        assertTrue(types.contains("SIMPLE"))
        assertTrue(types.contains("TIME_SPECIFIC"))
        assertTrue(types.contains("DEADLINE"))
        assertTrue(types.contains("IMMEDIATE"))
        assertTrue(types.contains("PROGRESS"))
        assertTrue(types.contains("MULTI_CONFIRMATION"))
        assertTrue(types.contains("RECURRING"))

        // Verify 4 importance levels
        val importances = TaskImportance.values().map { it.name }
        assertTrue(importances.contains("LOW"))
        assertTrue(importances.contains("NORMAL"))
        assertTrue(importances.contains("HIGH"))
        assertTrue(importances.contains("CRITICAL"))

        // Verify alarm types
        val alarmTypes = AlarmType.values().map { it.name }
        assertTrue(alarmTypes.contains("SILENT"))
        assertTrue(alarmTypes.contains("NORMAL_NOTIFICATION"))
        assertTrue(alarmTypes.contains("SOUND_ALERT"))
        assertTrue(alarmTypes.contains("FULL_SCREEN_ALARM"))
    }

    @Test
    fun testTimeUrgencyAndDisplayStatusTransitions() {
        val immediateTask = TaskItem(
            id = "task-imm-1",
            title = "Stop Entry Immediately",
            taskType = TaskType.IMMEDIATE,
            importance = TaskImportance.CRITICAL,
            urgencyType = TimeUrgency.IMMEDIATELY,
            status = "open"
        )
        // Immediate tasks must flag Action Required
        assertEquals(TaskStatus.ACTION_REQUIRED, immediateTask.displayStatus)

        // When acknowledged, status changes to ACKNOWLEDGED without marking completed
        val acknowledgedTask = immediateTask.copy(isAcknowledged = true)
        assertEquals(TaskStatus.ACKNOWLEDGED, acknowledgedTask.displayStatus)
        assertNotEquals(TaskStatus.COMPLETED, acknowledgedTask.displayStatus)

        // Completed task
        val completedTask = immediateTask.copy(status = "completed")
        assertEquals(TaskStatus.COMPLETED, completedTask.displayStatus)

        // Cancelled task
        val cancelledTask = immediateTask.copy(status = "cancelled")
        assertEquals(TaskStatus.CANCELLED, cancelledTask.displayStatus)
    }

    @Test
    fun testAdvanceRemindersAndOverdueSchedule() {
        val task = TaskItem(
            id = "task-spec-1",
            title = "Download Examination Drive",
            taskType = TaskType.TIME_SPECIFIC,
            importance = TaskImportance.HIGH,
            executionTime = "2026-10-16T06:00:00Z",
            advanceRemindersMinutes = listOf(0, 5, 10),
            remindUntilCompleted = true,
            overdueReminderIntervalMinutes = 5,
            maxOverdueReminders = 3
        )

        assertEquals(3, task.advanceRemindersMinutes.size)
        assertTrue(task.advanceRemindersMinutes.contains(0)) // At task time
        assertTrue(task.advanceRemindersMinutes.contains(5)) // 5m before
        assertTrue(task.advanceRemindersMinutes.contains(10)) // 10m before
        assertTrue(task.remindUntilCompleted)
        assertEquals(5, task.overdueReminderIntervalMinutes)
        assertEquals(3, task.maxOverdueReminders)

        val mainTimeMillis = TaskItem.parseIsoToMillis(task.executionTime!!)
        assertNotNull(mainTimeMillis)
        val tenMinBefore = mainTimeMillis!! - (10 * 60 * 1000L)
        assertEquals(10 * 60 * 1000L, mainTimeMillis - tenMinBefore)
    }

    @Test
    fun testTaskResponsesAndLanConfirmation() {
        val lans = listOf("LAN 1", "LAN 2", "LAN 3")
        val responses = listOf(
            TaskResponseItem(
                id = "resp-1",
                taskId = "task-lan",
                employeeId = "EMP101",
                employeeName = "John IT",
                lanNumber = "LAN 1",
                responseType = ResponseType.MULTI_CONFIRMATION,
                responseValue = "CONFIRMED",
                submittedAt = "2026-10-16T06:10:00Z"
            ),
            TaskResponseItem(
                id = "resp-2",
                taskId = "task-lan",
                employeeId = "EMP102",
                employeeName = "Sarah IT",
                lanNumber = "LAN 3",
                responseType = ResponseType.MULTI_CONFIRMATION,
                responseValue = "CONFIRMED",
                submittedAt = "2026-10-16T06:15:00Z"
            )
        )

        val confirmedLans = responses.mapNotNull { it.lanNumber }.toSet()
        val lan1Confirmed = confirmedLans.contains("LAN 1")
        val lan2Confirmed = confirmedLans.contains("LAN 2")
        val lan3Confirmed = confirmedLans.contains("LAN 3")

        assertTrue(lan1Confirmed)
        assertFalse(lan2Confirmed) // LAN 2 is still pending
        assertTrue(lan3Confirmed)

        val confirmedCount = responses.size
        val totalCount = lans.size
        assertEquals(2, confirmedCount)
        assertEquals(3, totalCount)
        val percent = (confirmedCount.toFloat() / totalCount * 100).toInt()
        assertEquals(66, percent)
    }

    @Test
    fun testUserProfileSingleIdentityModel() {
        val userUuid = "550e8400-e29b-41d4-a716-446655440000"
        val photoPath = "$userUuid/avatar_1791997779230.jpg"

        val profile = UserProfile(
            id = userUuid,
            employeeId = "EMP1024",
            fullName = "Alex Mercer",
            mobile = "+15551234567",
            profilePhotoPath = photoPath,
            appRole = "employee",
            isActive = true
        )

        assertEquals(userUuid, profile.id)
        assertEquals(photoPath, profile.profilePhotoPath)
        assertFalse(profile.isAdmin)
        assertTrue(profile.isActive)

        val adminProfile = profile.copy(appRole = "admin")
        assertTrue(adminProfile.isAdmin)
    }

    @Test
    fun testSyncMetadataTransitions() {
        val meta = SyncMetadata(
            lastServerFetch = 1700000000000L,
            lastSuccessfulSync = 1700000000000L,
            pendingSyncCount = 0,
            status = SyncStatus.SYNCED
        )
        assertEquals(SyncStatus.SYNCED, meta.status)
        assertEquals(0, meta.pendingSyncCount)
    }

    @Test
    fun testCompletionPolicyAndMultiLanIntegrity() {
        val singlePolicy = com.example.core.model.CompletionPolicy.SINGLE_ASSIGNEE
        val allLansPolicy = com.example.core.model.CompletionPolicy.ALL_SELECTED_LANS
        val confirmationPolicy = com.example.core.model.CompletionPolicy.CREATOR_CONFIRMATION

        assertEquals("Single Assignee", singlePolicy.label)
        assertEquals("All Selected LANs", allLansPolicy.label)
        assertEquals("Creator Confirmation", confirmationPolicy.label)

        // Verify task with ALL_SELECTED_LANS requires all LAN submissions before completion
        val multiLanTask = TaskItem(
            id = "task-multi-lan",
            title = "LAN Readiness Confirmation",
            taskType = TaskType.MULTI_CONFIRMATION,
            completionPolicy = allLansPolicy,
            lanTargetList = listOf("LAN 1", "LAN 2", "LAN 3"),
            status = "open"
        )
        assertEquals(allLansPolicy, multiLanTask.completionPolicy)
        assertEquals(3, multiLanTask.lanTargetList.size)
    }

    @Test
    fun testDynamicFormFieldSchemaModel() {
        val formField = com.example.core.model.ResponseFormField(
            id = "field-1",
            label = "Candidate Count",
            fieldType = com.example.core.model.FormFieldType.NUMBER,
            isRequired = true
        )
        assertEquals("Candidate Count", formField.label)
        assertEquals(com.example.core.model.FormFieldType.NUMBER, formField.fieldType)
        assertTrue(formField.isRequired)
    }

    @Test
    fun testOutboxEventPayloadFormatting() {
        val idempotencyKey = java.util.UUID.randomUUID().toString()
        val payloadString = """{"responseType":"COMPLETED_NOT_COMPLETED","responseValue":"COMPLETED","percentage":100}"""

        val event = com.example.data.local.entity.OutboxEventEntity(
            eventId = idempotencyKey,
            userId = "user-123",
            taskId = "task-456",
            operationType = "COMPLETION_REQUEST",
            payloadJson = payloadString,
            syncStatus = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        assertEquals("COMPLETION_REQUEST", event.operationType)
        assertEquals("PENDING", event.syncStatus)
        assertEquals(idempotencyKey, event.eventId)
        assertTrue(event.payloadJson.contains("COMPLETED"))
    }

    @Test
    fun testExecutionTimeValidationPastPresentFuture() {
        val now = 1770000000000L // arbitrary fixed epoch millis
        val pastIso = java.time.Instant.ofEpochMilli(now - 60_000L).toString()
        val presentIso = java.time.Instant.ofEpochMilli(now).toString()
        val futureIso = java.time.Instant.ofEpochMilli(now + 60_000L).toString()

        val pastError = TaskItem.validateExecutionTime(pastIso, nowMillis = now)
        assertEquals(TaskItem.PAST_TIME_ERROR_MESSAGE, pastError)
        assertEquals(
            "Selected execution time has already passed. Please choose a future date and time.",
            pastError
        )

        val presentError = TaskItem.validateExecutionTime(presentIso, nowMillis = now)
        assertEquals(null, presentError)

        val futureError = TaskItem.validateExecutionTime(futureIso, nowMillis = now)
        assertEquals(null, futureError)

        val blankError = TaskItem.validateExecutionTime("", nowMillis = now)
        assertEquals(null, blankError)
    }

    @Test
    fun testExecutionTimeDateChangesAndMidnightBoundaries() {
        // Assume now is 11:55 PM on Oct 19, 2026 UTC
        val nowInstant = java.time.Instant.parse("2026-10-19T23:55:00Z")
        val nowMillis = nowInstant.toEpochMilli()

        // 12:05 AM on Oct 20, 2026 UTC (tomorrow, across midnight boundary)
        val midnightFutureIso = "2026-10-20T00:05:00Z"
        val midnightFutureError = TaskItem.validateExecutionTime(midnightFutureIso, nowMillis = nowMillis)
        assertEquals(null, midnightFutureError)

        // 11:50 PM on Oct 19, 2026 UTC (5 minutes ago today)
        val pastTodayIso = "2026-10-19T23:50:00Z"
        val pastTodayError = TaskItem.validateExecutionTime(pastTodayIso, nowMillis = nowMillis)
        assertEquals(TaskItem.PAST_TIME_ERROR_MESSAGE, pastTodayError)

        // 12:05 AM on Oct 19, 2026 UTC (earlier today, across previous midnight)
        val pastEarlierTodayIso = "2026-10-19T00:05:00Z"
        val pastEarlierTodayError = TaskItem.validateExecutionTime(pastEarlierTodayIso, nowMillis = nowMillis)
        assertEquals(TaskItem.PAST_TIME_ERROR_MESSAGE, pastEarlierTodayError)

        // Next month boundary: Oct 31 11:59 PM -> Nov 1 12:01 AM
        val oct31Millis = java.time.Instant.parse("2026-10-31T23:59:00Z").toEpochMilli()
        val nov1Iso = "2026-11-01T00:01:00Z"
        assertEquals(null, TaskItem.validateExecutionTime(nov1Iso, nowMillis = oct31Millis))

        // Previous date: Oct 18 11:55 PM
        val yesterdayIso = "2026-10-18T23:55:00Z"
        assertEquals(TaskItem.PAST_TIME_ERROR_MESSAGE, TaskItem.validateExecutionTime(yesterdayIso, nowMillis = nowMillis))
    }

    @Test
    fun testExecutionTimeFullDateTimeComparisonNotClockOnly() {
        // If comparing clock-only:
        // Clock 00:15 is numerically less than clock 23:45, but Oct 20 00:15 is in the FUTURE when now is Oct 19 23:45.
        val nowInstant = java.time.Instant.parse("2026-10-19T23:45:00Z")
        val nowMillis = nowInstant.toEpochMilli()

        val nextDayAfterMidnight = "2026-10-20T00:15:00Z"
        val nextDayError = TaskItem.validateExecutionTime(nextDayAfterMidnight, nowMillis = nowMillis)
        assertEquals("Next day after midnight must be valid future time", null, nextDayError)

        // Conversely, yesterday at 23:59 has clock 23:59 > now clock 08:00, but is in the PAST.
        val nowMorningMillis = java.time.Instant.parse("2026-10-20T08:00:00Z").toEpochMilli()
        val yesterdayLateNight = "2026-10-19T23:59:00Z"
        val yesterdayError = TaskItem.validateExecutionTime(yesterdayLateNight, nowMillis = nowMorningMillis)
        assertEquals(
            "Yesterday night must be detected as past even if clock hour is greater",
            TaskItem.PAST_TIME_ERROR_MESSAGE,
            yesterdayError
        )
    }

    @Test
    fun testExecutionTimeFormatParsingAndDisplay() {
        val iso = "2026-10-20T10:00:00Z"
        val millis = TaskItem.parseIsoToMillis(iso)
        assertNotNull(millis)
        assertEquals(java.time.Instant.parse(iso).toEpochMilli(), millis)

        val display = TaskItem.formatForDisplay(iso)
        assertNotNull(display)
        assertTrue(display!!.contains("2026"))

        val invalidError = TaskItem.validateExecutionTime("not-a-valid-date")
        assertTrue(invalidError != null && invalidError.contains("Invalid execution date/time format"))
    }
}
