package com.example

import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testInternalAuthEmailMapping() {
        val email1 = SupabaseConfig.toInternalAuthEmail("EMP1024")
        assertEquals("emp1024@taskz.internal", email1)

        val email2 = SupabaseConfig.toInternalAuthEmail("  ADMIN_01  ")
        assertEquals("admin_01@taskz.internal", email2)

        val email3 = SupabaseConfig.toInternalAuthEmail("emp-999#special")
        assertEquals("emp-999special@taskz.internal", email3)
    }

    @Test
    fun testUserProfileAdminRole() {
        val adminUser = UserProfile(
            employeeId = "ADMIN01",
            appRole = "admin"
        )
        assertTrue(adminUser.isAdmin)

        val employeeUser = UserProfile(
            employeeId = "EMP1024",
            appRole = "employee"
        )
        assertFalse(employeeUser.isAdmin)
    }

    @Test
    fun testSyncMetadataState() {
        val meta = SyncMetadata(
            lastServerFetch = 1700000000000L,
            lastSuccessfulSync = 1700000000000L,
            pendingSyncCount = 0,
            status = SyncStatus.SYNCED
        )
        assertEquals(SyncStatus.SYNCED, meta.status)
        assertEquals(0, meta.pendingSyncCount)
    }
}
