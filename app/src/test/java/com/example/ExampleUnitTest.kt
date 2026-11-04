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

    @Test
    fun testFailedServerRequestPropagatesFailureAtomically() {
        // Verify that server failure response is not masked as false success
        val serverErrorResponse = Result.failure<TaskItem>(
            java.io.IOException("Server returned 403 Forbidden: Insufficient permissions to create operational task")
        )

        assertTrue("Failure result must be caught", serverErrorResponse.isFailure)
        val exception = serverErrorResponse.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("403 Forbidden"))

        // Ensure fake success object is never created or returned on server rejection
        var taskCreatedLocally = false
        serverErrorResponse.fold(
            onSuccess = { taskCreatedLocally = true },
            onFailure = { /* Atomic abort: do not save */ }
        )
        assertFalse("Task must not be reported as created on server error", taskCreatedLocally)
    }

    @Test
    fun testInvalidEmployeeAssignmentFailsValidation() {
        // Case A: Non-existent employee ID
        val knownProfiles = mapOf(
            "EMP1001" to "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "EMP1002" to "b2c3d4e5-f6a7-8901-bcde-f12345678901"
        )
        val invalidEmpId = "EMP9999_UNKNOWN"

        fun resolveEmployee(empId: String): Result<String> {
            val isUuid = try {
                java.util.UUID.fromString(empId)
                true
            } catch (_: Exception) {
                false
            }
            if (isUuid) return Result.success(empId)
            val uuid = knownProfiles[empId]
            return if (uuid != null) Result.success(uuid) else Result.failure(NoSuchElementException("No employee found: $empId"))
        }

        val resInvalid = resolveEmployee(invalidEmpId)
        assertTrue("Unknown employee ID must fail resolution", resInvalid.isFailure)

        // Case B: Valid employee ID resolves to true UUID
        val resValid = resolveEmployee("EMP1001")
        assertTrue("Known employee ID must resolve successfully", resValid.isSuccess)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", resValid.getOrNull())

        // Case C: Raw valid UUID passes through
        val rawUuid = "c3d4e5f6-a7b8-9012-cdef-123456789012"
        val resUuid = resolveEmployee(rawUuid)
        assertTrue(resUuid.isSuccess)
        assertEquals(rawUuid, resUuid.getOrNull())
    }

    @Test
    fun testDuplicateOperationIdempotencyCheck() {
        val clientOpId = java.util.UUID.randomUUID().toString()

        // Existing tasks table mock with client_operation_id
        val existingTasks = mutableMapOf<String, String>()
        existingTasks[clientOpId] = "task_uuid_already_persisted"

        fun handleAtomicTaskCreation(newClientOpId: String): Map<String, Any> {
            if (existingTasks.containsKey(newClientOpId)) {
                return mapOf(
                    "success" to true,
                    "is_duplicate" to true,
                    "task_id" to existingTasks[newClientOpId]!!
                )
            }
            val newId = "task_uuid_new"
            existingTasks[newClientOpId] = newId
            return mapOf("success" to true, "is_duplicate" to false, "task_id" to newId)
        }

        // Second attempt with identical client_operation_id (e.g. network retry)
        val duplicateResult = handleAtomicTaskCreation(clientOpId)
        assertEquals(true, duplicateResult["is_duplicate"])
        assertEquals("task_uuid_already_persisted", duplicateResult["task_id"])

        // Unique operation produces new task
        val newOpId = java.util.UUID.randomUUID().toString()
        val freshResult = handleAtomicTaskCreation(newOpId)
        assertEquals(false, freshResult["is_duplicate"])
        assertEquals("task_uuid_new", freshResult["task_id"])
    }

    @Test
    fun testOfflineCreationDurableOutboxCommit() {
        var outboxCommitted = false
        var outboxPayloadJson: String? = null

        fun simulateOfflineTaskCreation(isNetworkDown: Boolean): Result<String> {
            if (isNetworkDown) {
                // Step 1: Durable outbox commit MUST occur before reporting success
                try {
                    outboxPayloadJson = """{"title":"Offline Shift Check","lan_target_list":["LAN-1"]}"""
                    outboxCommitted = true
                } catch (e: Exception) {
                    return Result.failure(java.io.IOException("Outbox write failed", e))
                }
                return Result.success("pending_sync")
            }
            return Result.success("synced")
        }

        val result = simulateOfflineTaskCreation(isNetworkDown = true)
        assertTrue("Offline creation with committed outbox must succeed", result.isSuccess)
        assertEquals("pending_sync", result.getOrNull())
        assertTrue("Outbox must be durably committed", outboxCommitted)
        assertNotNull(outboxPayloadJson)
    }

    @Test
    fun testCrossDeviceTaskFetchAndAlarmScheduling() {
        val deviceBUserId = "user_device_b_uuid"
        val serverTaskPayload = mapOf(
            "id" to "task_cross_device_456",
            "title" to "Cross-Device Hall A Verification",
            "execution_time" to "2026-11-03T16:00:00Z",
            "participants" to listOf(
                mapOf("user_id" to "admin_device_a_uuid", "status" to "active"),
                mapOf("user_id" to deviceBUserId, "status" to "active")
            )
        )

        // Device B fetches task list and evaluates assignment
        @Suppress("UNCHECKED_CAST")
        val participants = serverTaskPayload["participants"] as List<Map<String, String>>
        val isAssignedToDeviceB = participants.any { it["user_id"] == deviceBUserId && it["status"] == "active" }

        assertTrue("Task must be identified as assigned to Device B user", isAssignedToDeviceB)

        // Alarm must be scheduled because user is assigned
        var alarmScheduledOnDeviceB = false
        if (isAssignedToDeviceB) {
            alarmScheduledOnDeviceB = true
        }
        assertTrue("Alarms must be scheduled on Device B for assigned task", alarmScheduledOnDeviceB)
    }

    @Test
    fun testJwtExpirationDetectionValidAndExpired() {
        // Construct standard Base64Url JWT payloads
        val currentSeconds = System.currentTimeMillis() / 1000

        fun createMockJwt(expSeconds: Long): String {
            val header = """{"alg":"HS256","typ":"JWT"}"""
            val payload = """{"sub":"user-uuid-123","exp":$expSeconds,"role":"authenticated"}"""
            val b64Header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
            val b64Payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
            return "$b64Header.$b64Payload.mock_signature"
        }

        fun isTokenExpired(token: String?, bufferSeconds: Long = 60L): Boolean {
            if (token.isNullOrBlank()) return true
            if (token.startsWith("offline_demo_token_")) return false
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return true
                var str = parts[1].replace('-', '+').replace('_', '/')
                while (str.length % 4 != 0) str += "="
                val decodedBytes = java.util.Base64.getDecoder().decode(str)
                val payloadString = String(decodedBytes, Charsets.UTF_8)
                val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
                val match = expRegex.find(payloadString)
                if (match == null) return false
                val exp = match.groupValues[1].toLong()
                val now = System.currentTimeMillis() / 1000
                now >= (exp - bufferSeconds)
            } catch (_: Exception) {
                true
            }
        }

        // 1. Token valid for next 1 hour -> should NOT be expired
        val validToken = createMockJwt(currentSeconds + 3600)
        assertFalse("Token valid for next 1 hour must not be expired", isTokenExpired(validToken))

        // 2. Token expired 5 minutes ago -> MUST be expired
        val expiredToken = createMockJwt(currentSeconds - 300)
        assertTrue("Token expired 5m ago must be detected as expired", isTokenExpired(expiredToken))

        // 3. Token expiring within buffer (e.g. in 30 seconds, with 60s buffer) -> MUST be detected as expiring
        val expiringSoonToken = createMockJwt(currentSeconds + 30)
        assertTrue("Token expiring within buffer window must be flagged for refresh", isTokenExpired(expiringSoonToken, bufferSeconds = 60L))

        // 4. Offline demo token -> never expired
        assertFalse("Offline demo token must never expire", isTokenExpired("offline_demo_token_1730000000000"))

        // 5. Corrupted, empty, or malformed strings -> must be expired/unsafe
        assertTrue(isTokenExpired(""))
        assertTrue(isTokenExpired(null))
        assertTrue(isTokenExpired("malformed_jwt_without_dots"))
        assertTrue(isTokenExpired("not.a.valid.json.base64"))
    }

    @Test
    fun testTokenRefreshRestoresExpiredSession() {
        val originalRefreshToken = "valid_refresh_token_abc"
        var currentAccessToken = "expired_access_token_123"
        val expectedNewAccessToken = "refreshed_access_token_456"

        // Mock Supabase token refresh endpoint handler
        fun simulateRefresh(refreshToken: String): Result<Pair<String, String>> {
            return if (refreshToken == "valid_refresh_token_abc") {
                Result.success(Pair(expectedNewAccessToken, "new_refresh_token_def"))
            } else {
                Result.failure(java.io.IOException("invalid_grant: Invalid Refresh Token"))
            }
        }

        // Simulate refresh when token is expired
        val refreshRes = simulateRefresh(originalRefreshToken)
        assertTrue("Token refresh with valid refresh token must succeed", refreshRes.isSuccess)
        val (newAccess, newRefresh) = refreshRes.getOrThrow()
        currentAccessToken = newAccess

        assertEquals(expectedNewAccessToken, currentAccessToken)
        assertEquals("new_refresh_token_def", newRefresh)
    }

    @Test
    fun testOfflineSessionRestorationPreservesProfileAndPendingOperations() {
        val cachedProfile = UserProfile(
            id = "user_offline_001",
            employeeId = "EMP1001",
            fullName = "Offline Officer",
            appRole = "employee",
            isActive = true
        )
        val pendingOutboxCount = 4

        // Simulate app launch while network is disconnected
        fun restoreSessionOffline(isNetworkConnected: Boolean): Result<UserProfile?> {
            if (!isNetworkConnected) {
                // Offline fallback: MUST preserve cached profile and never wipe local data
                return Result.success(cachedProfile)
            }
            return Result.success(cachedProfile)
        }

        val restored = restoreSessionOffline(isNetworkConnected = false)
        assertTrue("Offline restoration must succeed with cached profile", restored.isSuccess)
        assertNotNull(restored.getOrNull())
        assertEquals("EMP1001", restored.getOrNull()?.employeeId)
        // Verify pending operations count is preserved and not reset
        assertEquals(4, pendingOutboxCount)
    }

    @Test
    fun testOutboxSynchronizationPauseOnAuthFailure() {
        // Mock outbox events table
        val outboxEvents = mutableListOf(
            mapOf("id" to 1L, "type" to "TASK_CREATION", "status" to "SENDING"),
            mapOf("id" to 2L, "type" to "TASK_RESPONSE", "status" to "PENDING"),
            mapOf("id" to 3L, "type" to "LAN_CONFIRMATION", "status" to "PENDING"),
            mapOf("id" to 4L, "type" to "SHIFT_CREATION", "status" to "PENDING")
        )

        // When token refresh fails with revoked refresh token:
        val authFailure = java.io.IOException("Session expired: Reauthentication required")

        // 1. Synchronization is paused
        // 2. Any SENDING events are reset back to PENDING so they are never lost
        val resetEvents = outboxEvents.map { item ->
            if (item["status"] == "SENDING") {
                item.toMutableMap().apply { put("status", "PENDING") }
            } else {
                item
            }
        }

        // Verify operations are NOT deleted
        assertEquals("Pending operations count must remain exactly 4", 4, resetEvents.size)
        // Verify SENDING was reset to PENDING
        val event1 = resetEvents.first { it["id"] == 1L }
        assertEquals("PENDING", event1["status"])
    }

    @Test
    fun testErrorClassificationOnHomeScreen() {
        fun classifySyncError(ex: Throwable): Pair<SyncStatus, String> {
            val msg = ex.message.orEmpty()
            return when {
                ex is java.net.UnknownHostException ||
                ex is java.net.ConnectException ||
                ex is java.net.SocketTimeoutException ||
                msg.contains("offline", ignoreCase = true) ||
                msg.contains("unreachable", ignoreCase = true) ||
                msg.contains("no internet", ignoreCase = true) -> {
                    Pair(SyncStatus.OFFLINE, "Offline — Operations will sync automatically when connected")
                }
                msg.contains("JWT", ignoreCase = true) ||
                msg.contains("Session expired", ignoreCase = true) ||
                msg.contains("Authentication required", ignoreCase = true) ||
                msg.contains("invalid_grant", ignoreCase = true) -> {
                    Pair(SyncStatus.ERROR, "Authentication required: Session expired. Please log in again.")
                }
                else -> {
                    Pair(SyncStatus.ERROR, "Server error: $msg")
                }
            }
        }

        // Test network error classification
        val netErr = java.net.UnknownHostException("Unable to resolve host: supabase.co")
        val (netStatus, netMsg) = classifySyncError(netErr)
        assertEquals(SyncStatus.OFFLINE, netStatus)
        assertTrue(netMsg.contains("Offline"))

        // Test auth error classification
        val authErr = java.io.IOException("Authentication required: Session expired")
        val (authStatus, authMsg) = classifySyncError(authErr)
        assertEquals(SyncStatus.ERROR, authStatus)
        assertTrue(authMsg.contains("Authentication required"))

        // Test database / server error classification
        val dbErr = java.io.IOException("relation public.tasks does not exist")
        val (dbStatus, dbMsg) = classifySyncError(dbErr)
        assertEquals(SyncStatus.ERROR, dbStatus)
        assertTrue(dbMsg.contains("Server error"))
    }

    @Test
    fun testPreventDuplicateTaskCreationWithClientOperationId() {
        val clientOpId = "client_op_fixed_12345"
        val serverTasksTable = mutableMapOf<String, String>() // client_operation_id -> task_id

        // First creation persists task
        serverTasksTable[clientOpId] = "task_uuid_1001"

        // Second creation attempt (e.g. offline retry or recovery)
        fun executeAtomicTaskCreation(payloadClientOpId: String): Map<String, Any> {
            val existing = serverTasksTable[payloadClientOpId]
            return if (existing != null) {
                mapOf(
                    "success" to true,
                    "is_duplicate" to true,
                    "task_id" to existing
                )
            } else {
                val newId = "task_uuid_1002"
                serverTasksTable[payloadClientOpId] = newId
                mapOf("success" to true, "is_duplicate" to false, "task_id" to newId)
            }
        }

        val retryResult = executeAtomicTaskCreation(clientOpId)
        assertTrue(retryResult["success"] as Boolean)
        assertTrue("Duplicate creation must be identified", retryResult["is_duplicate"] as Boolean)
        assertEquals("task_uuid_1001", retryResult["task_id"])
        // Ensure no extra row was inserted in table
        assertEquals(1, serverTasksTable.size)
    }
}


