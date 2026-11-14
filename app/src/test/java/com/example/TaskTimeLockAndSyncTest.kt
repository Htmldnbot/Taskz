package com.example

import com.example.core.model.TaskItem
import com.example.core.model.TaskStatus
import com.example.core.model.TaskType
import com.example.core.network.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TaskTimeLockAndSyncTest {

    @Test
    fun futureScheduledTaskLocksAcknowledgeAndConfirmationUntilScheduledTime() {
        val now = 1_780_000_000_000L
        val futureIso = Instant.ofEpochMilli(now + 45 * 60_000L).toString()
        val pastOrCurrentIso = Instant.ofEpochMilli(now).toString()

        val futureTask = TaskItem(
            id = "task-future-1",
            title = "Server Verification",
            taskType = TaskType.TIME_SPECIFIC,
            startsAt = futureIso,
            executionTime = futureIso,
            dueAt = futureIso,
            status = "open"
        )

        assertTrue(
            "Task before scheduled time must lock Acknowledge & Confirmation controls",
            futureTask.isBeforeScheduledExecution(now)
        )
        assertTrue(
            "Task 1ms before scheduled time must still be locked",
            futureTask.isBeforeScheduledExecution(now + 45 * 60_000L - 1L)
        )
        assertFalse(
            "Task must unlock right at the scheduled execution time",
            futureTask.isBeforeScheduledExecution(now + 45 * 60_000L)
        )

        val reachedTask = futureTask.copy(
            startsAt = pastOrCurrentIso,
            executionTime = pastOrCurrentIso,
            dueAt = pastOrCurrentIso
        )
        assertFalse(
            "Task whose scheduled time has arrived must not be locked",
            reachedTask.isBeforeScheduledExecution(now)
        )

        val immediateTask = futureTask.copy(taskType = TaskType.IMMEDIATE)
        assertFalse(
            "Immediate tasks must never be locked behind a future timer",
            immediateTask.isBeforeScheduledExecution(now)
        )
    }

    @Test
    fun displayStatusRespectsFutureExecutionTimeEvenIfStartsAtWasEarlier() {
        val now = System.currentTimeMillis()
        val earlierIso = Instant.ofEpochMilli(now - 10 * 60_000L).toString()
        val twoHoursAheadIso = Instant.ofEpochMilli(now + 120 * 60_000L).toString()
        val tenMinutesAheadIso = Instant.ofEpochMilli(now + 10 * 60_000L).toString()

        val scheduledFar = TaskItem(
            id = "task-sched-far",
            title = "Scheduled Later",
            taskType = TaskType.TIME_SPECIFIC,
            startsAt = earlierIso,
            executionTime = twoHoursAheadIso,
            dueAt = twoHoursAheadIso,
            status = "open"
        )
        assertEquals(TaskStatus.SCHEDULED, scheduledFar.displayStatus)

        val upcomingSoon = scheduledFar.copy(
            executionTime = tenMinutesAheadIso,
            dueAt = tenMinutesAheadIso
        )
        assertEquals(TaskStatus.UPCOMING, upcomingSoon.displayStatus)
    }

    @Test
    fun uuidValidationRejectsNullAndSyntheticGroupIdentifiers() {
        assertTrue(SupabaseConfig.isValidUuid("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(SupabaseConfig.isValidUuid(null))
        assertFalse(SupabaseConfig.isValidUuid(""))
        assertFalse(SupabaseConfig.isValidUuid("null"))
        assertFalse(SupabaseConfig.isValidUuid("centre_141"))
    }
}
