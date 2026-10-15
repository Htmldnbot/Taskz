package com.example

import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDeterministicEmployeeIdAuthMapping() {
        // Casing and whitespace normalize identically
        val email1 = SupabaseConfig.toInternalAuthEmail("EMP1024")
        val email2 = SupabaseConfig.toInternalAuthEmail("  emp1024  ")
        assertEquals(email1, email2)
        assertTrue(email1.endsWith("@taskz.internal"))

        // Punctuation differences must NEVER collide
        val emailWithHyphen = SupabaseConfig.toInternalAuthEmail("EMP-1024")
        val emailWithoutHyphen = SupabaseConfig.toInternalAuthEmail("EMP1024")
        val emailWithDot = SupabaseConfig.toInternalAuthEmail("EMP.1024")
        val emailWithUnderscore = SupabaseConfig.toInternalAuthEmail("EMP_1024")

        assertNotEquals(emailWithHyphen, emailWithoutHyphen)
        assertNotEquals(emailWithHyphen, emailWithDot)
        assertNotEquals(emailWithHyphen, emailWithUnderscore)
        assertNotEquals(emailWithDot, emailWithUnderscore)

        // Special characters never collide with alphanumeric equivalents
        val emailSpecial = SupabaseConfig.toInternalAuthEmail("emp-999#special")
        val emailPlain = SupabaseConfig.toInternalAuthEmail("emp-999special")
        assertNotEquals(emailSpecial, emailPlain)

        // RFC 5321 length constraint: local part must be under 64 characters
        val localPart = emailWithHyphen.substringBefore("@")
        assertTrue("Local part length must be <= 64 characters", localPart.length <= 64)
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

        // Verify status enum values match strict lifecycle
        val statuses = SyncStatus.values().map { it.name }
        assertTrue(statuses.contains("PENDING"))
        assertTrue(statuses.contains("SYNCING"))
        assertTrue(statuses.contains("SYNCED"))
        assertTrue(statuses.contains("OFFLINE"))
        assertTrue(statuses.contains("ERROR"))
    }
}
