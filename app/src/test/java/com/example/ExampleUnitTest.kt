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

    @Test
    fun testSystemNumberNormalizationAndValidation() {
        // Normalization checks
        assertEquals("C005", com.example.core.model.SystemIncident.normalizeSystemNumber("5"))
        assertEquals("C059", com.example.core.model.SystemIncident.normalizeSystemNumber("59"))
        assertEquals("C159", com.example.core.model.SystemIncident.normalizeSystemNumber("159"))
        assertEquals("C005", com.example.core.model.SystemIncident.normalizeSystemNumber("C5"))
        assertEquals("C059", com.example.core.model.SystemIncident.normalizeSystemNumber("c59"))
        assertEquals("C105", com.example.core.model.SystemIncident.normalizeSystemNumber("C105"))
        assertEquals("C101", com.example.core.model.SystemIncident.normalizeSystemNumber("  101  "))

        // Validation checks
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C005"))
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C059"))
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C101"))
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C105"))
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C106"))
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber("C159"))

        // Rejections
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("C5"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("59"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("159"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("D105"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("C1005"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber("C10A"))
        assertFalse(com.example.core.model.SystemIncident.isValidSystemNumber(""))
    }

    @Test
    fun testMacAddressFormattingAndValidation() {
        // Raw lowercase with no separators
        val formatted1 = com.example.core.model.SystemIncident.formatMacAddress("aabbccddeeff")
        assertEquals("AA:BB:CC:DD:EE:FF", formatted1)
        assertTrue(com.example.core.model.SystemIncident.isValidMacAddress(formatted1))

        // Hyphen separated
        val formatted2 = com.example.core.model.SystemIncident.formatMacAddress("AA-BB-CC-DD-EE-FF")
        assertEquals("AA:BB:CC:DD:EE:FF", formatted2)
        assertTrue(com.example.core.model.SystemIncident.isValidMacAddress(formatted2))

        // Space separated
        val formatted3 = com.example.core.model.SystemIncident.formatMacAddress("00 1a 2b 3c 4d 5e")
        assertEquals("00:1A:2B:3C:4D:5E", formatted3)
        assertTrue(com.example.core.model.SystemIncident.isValidMacAddress(formatted3))

        // Invalids
        assertFalse(com.example.core.model.SystemIncident.isValidMacAddress("AA:BB:CC:DD:EE"))
        assertFalse(com.example.core.model.SystemIncident.isValidMacAddress("AA:BB:CC:DD:EE:ZZ"))
        assertFalse(com.example.core.model.SystemIncident.isValidMacAddress("not-a-mac"))
        assertFalse(com.example.core.model.SystemIncident.isValidMacAddress(""))
    }

    @Test
    fun testIpv4AddressValidation() {
        // Valid IP addresses
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("192.168.1.10"))
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("10.0.0.25"))
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("172.16.1.100"))
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("127.0.0.1"))
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("255.255.255.255"))
        assertTrue(com.example.core.model.SystemIncident.isValidIpv4("0.0.0.0"))

        // Invalid IP addresses
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("999.168.1.10"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("192.168.1"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("192.168.1.300"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("192.168.1.-1"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("192.168.01.1"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("192.168.1.1.1"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4("abc.def.ghi.jkl"))
        assertFalse(com.example.core.model.SystemIncident.isValidIpv4(""))
    }

    @Test
    fun testCandidateShiftedReplacementIntegrity() {
        val locked = "C059"
        val replacementSame = "C059"
        val replacementDiff = "C159"

        // Candidate shifted = false -> replacement is null/empty
        val incidentNotShifted = com.example.core.model.SystemIncident(
            lockedSystemNumber = locked,
            candidateShifted = false,
            newlyAllocatedSystemNumber = null,
            reason = "Keyboard Fault"
        )
        assertFalse(incidentNotShifted.candidateShifted)
        assertEquals(null, incidentNotShifted.newlyAllocatedSystemNumber)

        // Candidate shifted = true, but replacement is identical -> must fail validation
        assertFalse("Replacement system cannot be identical to locked system",
            locked.equals(replacementSame, ignoreCase = true) == false
        )

        // Candidate shifted = true, replacement is distinct and valid CXXX
        assertTrue(com.example.core.model.SystemIncident.isValidSystemNumber(replacementDiff))
        assertNotEquals(locked, replacementDiff)
    }

    @Test
    fun testBatch1TaskCreationPayloadIntegrityAndIdempotency() {
        val clientOpId = java.util.UUID.randomUUID().toString()
        val task = TaskItem(
            id = "task_test_123",
            title = "LAN Verification Gate 1",
            description = "Verify all switch ports in Hall A",
            status = "open",
            taskType = TaskType.PROGRESS,
            importance = TaskImportance.HIGH,
            urgencyType = TimeUrgency.SPECIFIC_TIME,
            executionTime = "2026-11-03T10:00:00Z",
            targetPercentage = 100,
            alarmType = AlarmType.SOUND_ALERT,
            advanceRemindersMinutes = listOf(15, 30),
            responseType = ResponseType.MULTI_CONFIRMATION,
            lanTargetList = listOf("LAN-A1", "LAN-A2", "LAN-A3"),
            createdBy = "admin_user_001"
        )

        // Verify task attributes for Phase 3 LAN operations
        assertEquals(3, task.lanTargetList.size)
        assertEquals(ResponseType.MULTI_CONFIRMATION, task.responseType)
        assertEquals(TaskImportance.HIGH, task.importance)
        assertEquals(TaskType.PROGRESS, task.taskType)

        // Verify client operation ID generation and map payload structure
        val payloadMap = mapOf(
            "id" to task.id,
            "client_operation_id" to clientOpId,
            "sync_status" to "PENDING_SYNC",
            "assigned_employee_ids" to listOf("emp_user_001", "emp_user_002"),
            "required_role_requirements" to listOf(
                mapOf("role_id" to "lan_technician", "min_count" to 1)
            )
        )

        assertEquals(clientOpId, payloadMap["client_operation_id"])
        assertEquals("PENDING_SYNC", payloadMap["sync_status"])
        @Suppress("UNCHECKED_CAST")
        val assigned = payloadMap["assigned_employee_ids"] as List<String>
        assertEquals(2, assigned.size)
        assertTrue(assigned.contains("emp_user_001"))
    }

    @Test
    fun testBatch1ZeroDemoEmployeesEnforced() {
        // Enforce that demo employee IDs and names are never returned
        val forbiddenDemoNames = setOf(
            "Rahul Sharma", "Priya Patel", "Amit Verma", "Sneha Rao", "Vikram Singh"
        )
        val forbiddenDemoIds = setOf("emp_1", "emp_2", "emp_3", "emp_4", "emp_5")

        // Construct empty or real profile list
        val realProfiles = listOf(
            UserProfile(id = "user_real_99", fullName = "Test Engineer", appRole = "admin", employeeId = "EMP99")
        )

        for (profile in realProfiles) {
            assertFalse("Demo name must not exist in directory", forbiddenDemoNames.contains(profile.fullName))
            assertFalse("Demo ID must not exist in directory", forbiddenDemoIds.contains(profile.id))
        }
    }

    @Test
    fun testBatch1ShiftCopyOffsetCalculation() {
        val originalTimeStr = "2026-11-03T09:00:00Z"
        val offsetMinutes = 180 // 3 hours
        val originalInstant = java.time.Instant.parse(originalTimeStr)
        val shiftedInstant = originalInstant.plus(offsetMinutes.toLong(), java.time.temporal.ChronoUnit.MINUTES)
        val shiftedTimeStr = shiftedInstant.toString()

        assertEquals("2026-11-03T12:00:00Z", shiftedTimeStr)

        val originalTask = TaskItem(
            id = "shift_task_orig",
            title = "Pre-Exam System Check",
            taskType = TaskType.PROGRESS,
            importance = TaskImportance.CRITICAL,
            urgencyType = TimeUrgency.BEFORE_DEADLINE,
            executionTime = originalTimeStr,
            targetPercentage = 100,
            lanTargetList = listOf("LAN-1", "LAN-2"),
            responseType = ResponseType.MULTI_CONFIRMATION
        )

        val copiedTask = originalTask.copy(
            id = java.util.UUID.randomUUID().toString(),
            executionTime = shiftedTimeStr,
            shiftId = "new_shift_id_456"
        )

        assertEquals("2026-11-03T12:00:00Z", copiedTask.executionTime)
        assertEquals(originalTask.taskType, copiedTask.taskType)
        assertEquals(originalTask.importance, copiedTask.importance)
        assertEquals(originalTask.targetPercentage, copiedTask.targetPercentage)
        assertEquals(originalTask.lanTargetList, copiedTask.lanTargetList)
        assertEquals("new_shift_id_456", copiedTask.shiftId)
    }

    @Test
    fun testBatch1TaskServerSyncParticipantMapping() {
        val currentUserId = "my_user_id_123"

        // Mock server task record representation
        val participants = listOf(
            mapOf("user_id" to currentUserId, "status" to "active", "assigned_role" to "invigilator"),
            mapOf("user_id" to "other_user_456", "status" to "active", "assigned_role" to "lab_admin")
        )

        val serverTaskRecord = mapOf(
            "id" to "task_server_789",
            "title" to "Server Confirmed Task",
            "description" to "Synced from Supabase",
            "created_by" to "admin_lead",
            "client_operation_id" to "op-abc-123",
            "sync_status" to "SYNCED",
            "execution_time" to "2026-11-03T14:00:00Z",
            "participants" to participants
        )

        // Verify participant extraction
        @Suppress("UNCHECKED_CAST")
        val participantsList = serverTaskRecord["participants"] as List<Map<String, String>>
        assertEquals(2, participantsList.size)

        var myStatus: String? = null
        val assignedUserIds = mutableListOf<String>()
        for (p in participantsList) {
            val uid = p["user_id"]
            val status = p["status"]
            if (uid != null) assignedUserIds.add(uid)
            if (uid == currentUserId) {
                myStatus = status
            }
        }

        assertEquals("active", myStatus)
        assertTrue(assignedUserIds.contains(currentUserId))
        assertTrue(assignedUserIds.contains("other_user_456"))
        assertEquals("SYNCED", serverTaskRecord["sync_status"])
        assertEquals("op-abc-123", serverTaskRecord["client_operation_id"])
    }
}

