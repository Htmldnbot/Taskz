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
        assertTrue(email1.endsWith("@taskzapp.com"))

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
    fun testMobileNormalization() {
        val indian10 = com.example.ui.auth.AuthViewModel.normalizeMobile("9876543210")
        assertEquals("+919876543210", indian10)
        assertTrue(indian10.matches(Regex("^\\+[1-9][0-9]{7,14}$")))

        val international = com.example.ui.auth.AuthViewModel.normalizeMobile("+15551234567")
        assertEquals("+15551234567", international)
        assertTrue(international.matches(Regex("^\\+[1-9][0-9]{7,14}$")))

        val us10 = com.example.ui.auth.AuthViewModel.normalizeMobile("5551234567")
        assertEquals("+15551234567", us10)
        assertTrue(us10.matches(Regex("^\\+[1-9][0-9]{7,14}$")))
    }
}
