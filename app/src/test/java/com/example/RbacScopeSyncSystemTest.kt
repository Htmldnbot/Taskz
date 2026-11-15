package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AppNotification
import com.example.core.model.AssignedRoleInfo
import com.example.core.model.AuthorizationContext
import com.example.core.model.NotificationCategory
import com.example.core.model.OrgScope
import com.example.core.model.ScopeLevel
import com.example.core.model.SystemIncident
import com.example.core.model.TaskItem
import com.example.core.model.TaskzRole
import com.example.core.model.UserProfile
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.SystemIncidentEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.remote.RealtimeEventType
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHttpException
import com.example.data.remote.TaskzRealtimeManager
import com.example.domain.security.TaskzPermissionManager
import com.example.notifications.DeepLinkTarget
import com.example.notifications.DeviceTokenManager
import com.example.notifications.NotificationDeepLinkRouter
import com.example.notifications.PushNotificationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive verification suite covering Spec Sections 66–80:
 * - Role onboarding restrictions & hierarchy resolution
 * - Organizational scope visibility vs write permissions (Centre/City/State/Zone/Global)
 * - Incident scope & escalation chain ordering
 * - Offline Room cache isolation on role switch & user logout
 * - Durable Outbox queue role/scope stamping, idempotency & sync status transitions
 * - Push notification event-key deduplication & notification categories
 * - Multi-device token fallback/isolation
 * - Deep link routing
 * - Realtime frame parsing & duplicate suppression
 * - HTTP error classification for safe retry/backoff
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RbacScopeSyncSystemTest {

    private lateinit var context: Context
    private lateinit var db: TaskzDatabase
    private lateinit var userPrefs: UserPreferencesDataStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TaskzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPrefs = UserPreferencesDataStore(context)
        context.getSharedPreferences("taskz_auth_session", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("taskz_account_hints", Context.MODE_PRIVATE).edit().clear().apply()
        NotificationDeepLinkRouter.consume()
    }

    @After
    fun tearDown() {
        NotificationDeepLinkRouter.consume()
        db.close()
    }

    // ---- Section 66: Role Tests ----

    @Test
    fun onboardingRolesRestrictedStrictlyToItManagerAndVco() {
        assertEquals(listOf(TaskzRole.IT_MANAGER, TaskzRole.VCO), TaskzRole.onboardingRoles)
        assertTrue(TaskzRole.IT_MANAGER.isOnboardingAllowed)
        assertTrue(TaskzRole.VCO.isOnboardingAllowed)
        assertFalse(TaskzRole.CITY_HEAD.isOnboardingAllowed)
        assertFalse(TaskzRole.ROM.isOnboardingAllowed)
        assertFalse(TaskzRole.ZOM.isOnboardingAllowed)
        assertFalse(TaskzRole.COMMAND_CENTER.isOnboardingAllowed)
        assertFalse(TaskzRole.SUPER_ADMIN.isOnboardingAllowed)
    }

    @Test
    fun activeRoleAndSuperAdminPrivilegeBoundaries() {
        val itCtx = buildContext(
            userId = "user-it-1",
            role = TaskzRole.IT_MANAGER,
            assignedRoles = listOf(TaskzRole.IT_MANAGER, TaskzRole.VCO),
            zoneId = "zone-1",
            stateId = "state-1",
            cityId = "city-1",
            centreId = "centre-a"
        )
        val pm = TaskzPermissionManager(itCtx)
        assertEquals(2, pm.getAssignedRoles().size)
        assertFalse(pm.canManageUsers())
        assertFalse(pm.canManageRoles())
        assertFalse(pm.canManageGroups())
        assertEquals(listOf(ScopeLevel.CENTRE), pm.allowedTaskScopes())

        val superAdminCtx = itCtx.copy(activeRole = TaskzRole.SUPER_ADMIN)
        pm.updateContext(superAdminCtx)
        assertTrue(pm.canManageUsers())
        assertTrue(pm.canManageRoles())
        assertTrue(pm.canManageGroups())
        assertEquals(
            listOf(ScopeLevel.CENTRE, ScopeLevel.CITY, ScopeLevel.STATE, ScopeLevel.ZONE, ScopeLevel.GLOBAL),
            pm.allowedTaskScopes()
        )
    }

    // ---- Section 67: Organizational Scope Visibility vs Write Distinction ----

    @Test
    fun roleBasedAllowedScopesFollowHierarchy() {
        val pm = TaskzPermissionManager()

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.IT_MANAGER))
        assertEquals(listOf(ScopeLevel.CENTRE), pm.allowedTaskScopes())

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.VCO))
        assertEquals(listOf(ScopeLevel.CENTRE, ScopeLevel.CITY), pm.allowedTaskScopes())

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.CITY_HEAD))
        assertEquals(listOf(ScopeLevel.CENTRE, ScopeLevel.CITY), pm.allowedTaskScopes())

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.ROM))
        assertEquals(listOf(ScopeLevel.CENTRE, ScopeLevel.CITY, ScopeLevel.STATE), pm.allowedTaskScopes())

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.ZOM))
        assertEquals(listOf(ScopeLevel.CENTRE, ScopeLevel.CITY, ScopeLevel.STATE, ScopeLevel.ZONE), pm.allowedTaskScopes())

        pm.updateContext(buildContext(userId = "u1", role = TaskzRole.COMMAND_CENTER))
        assertEquals(
            listOf(ScopeLevel.CENTRE, ScopeLevel.CITY, ScopeLevel.STATE, ScopeLevel.ZONE, ScopeLevel.GLOBAL),
            pm.allowedTaskScopes()
        )
    }

    @Test
    fun centreIsolationBlocksUnrelatedCentreAndGivesCityHeadReadOnlyOversight() {
        val centreATask = TaskItem(
            id = "task-c-a",
            title = "Check LAN 1 at Centre A",
            createdBy = "user-it-a",
            scopeLevel = ScopeLevel.CENTRE,
            zoneId = "zone-north",
            stateId = "state-dl",
            cityId = "city-del",
            centreId = "centre-a",
            targetRoleCodes = listOf("IT_MANAGER")
        )

        // IT Manager at Centre B must NOT view Centre A task
        val itCentreB = TaskzPermissionManager(
            buildContext(
                userId = "user-it-b",
                role = TaskzRole.IT_MANAGER,
                zoneId = "zone-north",
                stateId = "state-dl",
                cityId = "city-del",
                centreId = "centre-b"
            )
        )
        assertFalse(itCentreB.canViewTask(centreATask))
        assertFalse(itCentreB.canEditTask(centreATask))

        // City Head in city-del CAN view Centre A's IT_MANAGER task for oversight,
        // but CANNOT edit, delete, or complete it when not targeted/created by them.
        val cityHeadSameCity = TaskzPermissionManager(
            buildContext(
                userId = "user-ch-del",
                role = TaskzRole.CITY_HEAD,
                zoneId = "zone-north",
                stateId = "state-dl",
                cityId = "city-del",
                centreId = null
            )
        )
        assertTrue(cityHeadSameCity.canViewTask(centreATask))
        assertFalse(cityHeadSameCity.canEditTask(centreATask))
        assertFalse(cityHeadSameCity.canDeleteTask(centreATask))
        assertFalse(cityHeadSameCity.canCompleteTask(centreATask))

        // City Head in unrelated city MUST NOT see Centre A's task
        val cityHeadOtherCity = TaskzPermissionManager(
            buildContext(
                userId = "user-ch-mum",
                role = TaskzRole.CITY_HEAD,
                zoneId = "zone-west",
                stateId = "state-mh",
                cityId = "city-mum",
                centreId = null
            )
        )
        assertFalse(cityHeadOtherCity.canViewTask(centreATask))

        // Super Admin always has full view + edit + delete permissions
        val superAdmin = TaskzPermissionManager(
            buildContext(userId = "user-sa", role = TaskzRole.SUPER_ADMIN)
        )
        assertTrue(superAdmin.canViewTask(centreATask))
        assertTrue(superAdmin.canEditTask(centreATask))
        assertTrue(superAdmin.canDeleteTask(centreATask))
    }

    // ---- Section 68: Incident Scope & Escalation Chain ----

    @Test
    fun incidentScopeVisibilityAndEscalationChainAreEnforced() {
        assertEquals(
            listOf(
                TaskzRole.IT_MANAGER,
                TaskzRole.VCO,
                TaskzRole.CITY_HEAD,
                TaskzRole.ROM,
                TaskzRole.ZOM,
                TaskzRole.SUPER_ADMIN
            ),
            TaskzRole.escalationChain
        )

        val openIncident = SystemIncident(
            id = "inc-1",
            createdBy = "user-it-a",
            scopeLevel = "CENTRE",
            zoneId = "zone-north",
            stateId = "state-dl",
            cityId = "city-del",
            centreId = "centre-a",
            status = "OPEN",
            escalationLevel = "IT_MANAGER"
        )

        val vcoSameCity = TaskzPermissionManager(
            buildContext(
                userId = "user-vco-del",
                role = TaskzRole.VCO,
                zoneId = "zone-north",
                stateId = "state-dl",
                cityId = "city-del"
            )
        )
        assertTrue(vcoSameCity.canViewIncident(openIncident))
        assertTrue(vcoSameCity.canEditIncident(openIncident))
        assertTrue(vcoSameCity.canEscalateIncident(openIncident))

        val romOtherState = TaskzPermissionManager(
            buildContext(
                userId = "user-rom-up",
                role = TaskzRole.ROM,
                zoneId = "zone-north",
                stateId = "state-up"
            )
        )
        assertFalse(romOtherState.canViewIncident(openIncident))

        val resolvedIncident = openIncident.copy(status = "RESOLVED")
        assertFalse(vcoSameCity.canEscalateIncident(resolvedIncident))
        assertFalse(vcoSameCity.canResolveIncident(resolvedIncident))
    }

    // ---- Section 57 & 17: Role Switch vs User Logout Cache Isolation ----

    @Test
    fun roleSwitchClearsCachedViewsWhilePreservingOutbox_logoutClearsEverythingForUserB() = runBlocking {
        // Seed User A profile, task, incident, notification, and pending offline mutation
        db.profileDao().insertProfile(
            ProfileEntity.fromDomain(
                UserProfile(
                    id = "user-a",
                    employeeId = "EMP001",
                    fullName = "User Alpha",
                    mobile = "+919999999991",
                    appRole = "employee",
                    activeRoleCode = "IT_MANAGER",
                    primaryRoleCode = "IT_MANAGER",
                    assignedRoleCodes = listOf("IT_MANAGER", "VCO"),
                    isActive = true,
                    mustChangePassword = false,
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z"
                )
            )
        )
        db.taskDao().insertTask(
            TaskEntity.fromDomain(
                TaskItem(
                    id = "task-a-1",
                    groupId = "grp-1",
                    title = "Alpha Centre Task",
                    createdBy = "user-a",
                    centreId = "centre-a"
                )
            )
        )
        db.systemIncidentDao().insertIncident(
            SystemIncidentEntity.fromDomain(
                SystemIncident(
                    id = "inc-a-1",
                    createdBy = "user-a",
                    centreId = "centre-a",
                    reason = "System Hang"
                )
            )
        )
        db.notificationDao().insertNotifications(
            listOf(
                NotificationEntity.fromDomain(
                    AppNotification(
                        id = "notif-a-1",
                        userId = "user-a",
                        type = "task_assigned",
                        category = "task_assignment",
                        title = "New Task",
                        isRead = false,
                        createdAt = "2026-01-01T00:00:00Z"
                    )
                )
            )
        )
        db.outboxDao().enqueueEvent(
            OutboxEventEntity(
                eventId = "op-user-a-1",
                userId = "user-a",
                taskId = "task-a-1",
                operationType = "TASK_RESPONSE",
                payloadJson = """{"responseValue":"COMPLETED"}""",
                activeRole = "IT_MANAGER",
                scopeLevel = "CENTRE",
                scopeId = "centre-a"
            )
        )

        // Role switch clears tasks/incidents/notifications but keeps pending outbox queue safe
        db.clearRoleScopeCacheForSwitch()
        assertNull(db.taskDao().getTaskById("task-a-1"))
        assertNull(db.systemIncidentDao().getIncidentById("inc-a-1"))
        assertEquals(0, db.notificationDao().getUnreadCountFlow("user-a").first())
        assertEquals(1, db.outboxDao().getPendingEventsForUser("user-a").size)

        // Re-insert task to simulate User A active session before logout
        db.taskDao().insertTask(
            TaskEntity.fromDomain(
                TaskItem(id = "task-a-2", groupId = "grp-1", title = "Alpha Task 2", createdBy = "user-a")
            )
        )

        // Logout clears ALL user-scoped tables so User B cannot see User A's cached data
        db.clearAllUserScopedTables()
        assertNull(db.profileDao().getCachedProfile())
        assertTrue(db.taskDao().getTaskIdsSnapshot().isEmpty())
        assertEquals(0, db.systemIncidentDao().getPendingCountFlow().first())
        assertEquals(0, db.outboxDao().getPendingEvents().size)
    }

    // ---- Section 75 & 76: Offline Outbox Queue & Role/Scope Stamping ----

    @Test
    fun offlineOutboxPreservesRoleScopeMetadataAndMarksSyncedOnServerAck() = runBlocking {
        val insertedId = db.outboxDao().enqueueEvent(
            OutboxEventEntity(
                eventId = "idem-op-123",
                userId = "user-1",
                taskId = "task-99",
                operationType = "TASK_ACKNOWLEDGEMENT",
                payloadJson = """{"deviceTimestamp":"2026-11-15T10:00:00Z"}""",
                activeRole = "VCO",
                scopeLevel = "CITY",
                scopeId = "city-del"
            )
        )

        val pending = db.outboxDao().getPendingEventsForUser("user-1")
        assertEquals(1, pending.size)
        assertEquals("VCO", pending.first().activeRole)
        assertEquals("CITY", pending.first().scopeLevel)
        assertEquals("city-del", pending.first().scopeId)
        assertEquals("PENDING", pending.first().syncStatus)

        db.outboxDao().markEventSynced(insertedId, "ACK_CONFIRMED", System.currentTimeMillis())
        assertTrue(db.outboxDao().getPendingEventsForUser("user-1").isEmpty())
        val synced = db.outboxDao().getEventByEventId("idem-op-123")
        assertNotNull(synced)
        assertEquals("SYNCED", synced?.syncStatus)
        assertEquals("ACK_CONFIRMED", synced?.serverAcknowledgement)
    }

    // ---- Section 25 & 54: Duplicate Notification Protection & Categorization ----

    @Test
    fun pushNotificationManagerDeduplicatesAcrossInstancesViaPersistentEventKeys() = runBlocking {
        val notifManager1 = PushNotificationManager(context, db.authorizationDao())
        val notif = AppNotification(
            id = "n-1",
            userId = "user-1",
            type = "task_assigned",
            category = "task_assignment",
            title = "New Task Assigned",
            body = "Verify LAN connectivity",
            taskId = "task-42",
            eventKey = "task_assigned:task-42:user-1",
            deepLink = "taskz://task/task-42",
            isRead = false
        )

        val firstDispatch = notifManager1.dispatchIfUnique(notif)
        assertTrue("First dispatch for unique event_key must succeed", firstDispatch)

        val secondDispatchSameMgr = notifManager1.dispatchIfUnique(notif)
        assertFalse("Duplicate dispatch on same manager must be suppressed", secondDispatchSameMgr)

        // Fresh manager instance simulates process restart reading persisted Room event keys
        val notifManager2 = PushNotificationManager(context, db.authorizationDao())
        val thirdDispatchAfterRestart = notifManager2.dispatchIfUnique(notif)
        assertFalse("Duplicate dispatch after restart must remain suppressed", thirdDispatchAfterRestart)

        val readNotif = notif.copy(id = "n-2", eventKey = "task_assigned:task-43:user-1", isRead = true)
        assertFalse("Already-read notifications must not trigger alert", notifManager2.dispatchIfUnique(readNotif))

        assertEquals(NotificationCategory.INCIDENT_ESCALATION, NotificationCategory.fromRaw("incident_escalation"))
        assertTrue(NotificationCategory.INCIDENT_ESCALATION.urgent)
        assertFalse(NotificationCategory.TASK_ASSIGNMENT.urgent)
    }

    // ---- Section 52 & 77: Per-Device Identity & Token Fallback ----

    @Test
    fun deviceTokenManagerMaintainsStableDeviceIdAndFallbackToken() = runBlocking {
        val tokenMgr = DeviceTokenManager(context, userPrefs, SupabaseClient())
        val firstDeviceId = tokenMgr.getDeviceId()
        val secondDeviceId = tokenMgr.getDeviceId()
        assertFalse(firstDeviceId.isBlank())
        assertEquals(firstDeviceId, secondDeviceId)
        assertEquals("local:$firstDeviceId", tokenMgr.getCurrentOrFallbackToken())

        userPrefs.savePushToken("fcm-token-device-1")
        assertEquals("fcm-token-device-1", tokenMgr.getCurrentOrFallbackToken())
    }

    // ---- Section 53: Deep Link Routing ----

    @Test
    fun notificationDeepLinkRouterResolvesTaskIncidentProfileAndAdminTargets() {
        val taskTarget = NotificationDeepLinkRouter.parse(
            deepLink = "taskz://task/t-100",
            notificationId = "notif-1"
        )
        assertEquals(DeepLinkTarget.TaskDetails("t-100", "notif-1"), taskTarget)

        val incTarget = NotificationDeepLinkRouter.parse(deepLink = "taskz://incident/inc-55")
        assertEquals(DeepLinkTarget.IncidentDetails("inc-55", null), incTarget)

        val profileTarget = NotificationDeepLinkRouter.parse(deepLink = "taskz://profile")
        assertEquals(DeepLinkTarget.ProfileRoles(null), profileTarget)

        val adminTarget = NotificationDeepLinkRouter.parse(deepLink = "taskz://admin")
        assertEquals(DeepLinkTarget.AdminControls(null), adminTarget)

        val intent = Intent().apply {
            putExtra(PushNotificationManager.EXTRA_TASK_ID, "t-intent-7")
            putExtra(PushNotificationManager.EXTRA_NOTIFICATION_ID, "n-intent-7")
        }
        NotificationDeepLinkRouter.handleIntent(intent)
        assertEquals(
            DeepLinkTarget.TaskDetails("t-intent-7", "n-intent-7"),
            NotificationDeepLinkRouter.pendingTarget.value
        )
    }

    // ---- Section 70 & 61: Realtime Frame Parsing & Duplicate Event Protection ----

    @Test
    fun realtimeManagerParsesChangesRoutesRoleUpdatesAndDropsDuplicateFrames() {
        val rt = TaskzRealtimeManager()
        val taskFrame = """
            ["1","2","realtime:tasks_IT_MANAGER","postgres_changes",{"data":{"table":"tasks","type":"UPDATE","commit_timestamp":"2026-11-15T12:00:00Z","record":{"id":"task-77"}}}]
        """.trimIndent()
        val firstSignal = rt.handleIncomingFrame(taskFrame)
        assertNotNull(firstSignal)
        assertEquals(RealtimeEventType.TASK_CHANGED, firstSignal?.type)
        assertEquals("task-77", firstSignal?.recordId)

        val duplicateSignal = rt.handleIncomingFrame(taskFrame)
        assertNull("Identical realtime postgres_changes frame must be deduplicated", duplicateSignal)

        val roleFrame = """
            ["1","3","realtime:user_roles_u1","postgres_changes",{"data":{"table":"user_roles","type":"UPDATE","commit_timestamp":"2026-11-15T12:01:00Z","record":{"id":"ur-1"}}}]
        """.trimIndent()
        val roleSignal = rt.handleIncomingFrame(roleFrame)
        assertEquals(RealtimeEventType.ROLE_OR_SCOPE_CHANGED, roleSignal?.type)
    }

    // ---- Section 63 & 79: Error Handling Classification ----

    @Test
    fun supabaseHttpExceptionClassifiesAuthConflictAndRetryableErrors() {
        val unauth = SupabaseHttpException(401, "JWT expired")
        assertTrue(unauth.isAuthFailure)
        assertFalse(unauth.isRetryable)

        val forbidden = SupabaseHttpException(403, "Forbidden")
        assertTrue(forbidden.isAuthFailure)
        assertFalse(forbidden.isRetryable)

        val conflict = SupabaseHttpException(409, "Conflict")
        assertTrue(conflict.isConflict)
        assertFalse(conflict.isRetryable)

        val rateLimit = SupabaseHttpException(429, "Too Many Requests")
        assertTrue(rateLimit.isRetryable)
        assertFalse(rateLimit.isAuthFailure)

        val serverError = SupabaseHttpException(503, "Service Unavailable")
        assertTrue(serverError.isRetryable)

        val badReq = SupabaseHttpException(400, "Bad Request")
        assertFalse(badReq.isRetryable)
        assertFalse(badReq.isAuthFailure)
    }

    private fun buildContext(
        userId: String,
        role: TaskzRole,
        assignedRoles: List<TaskzRole> = listOf(role),
        zoneId: String? = "zone-1",
        stateId: String? = "state-1",
        cityId: String? = "city-1",
        centreId: String? = "centre-1"
    ): AuthorizationContext {
        return AuthorizationContext(
            userId = userId,
            employeeId = "EMP-$userId",
            fullName = "User $userId",
            isActive = true,
            activeRole = role,
            primaryRole = assignedRoles.first(),
            assignedRoles = assignedRoles.mapIndexed { idx, r ->
                AssignedRoleInfo(
                    roleId = "role-${r.code}",
                    roleCode = r.code,
                    roleName = r.displayName,
                    hierarchyLevel = r.hierarchyRank,
                    defaultScopeLevel = r.defaultScope.code,
                    isPrimary = idx == 0
                )
            },
            scope = OrgScope(
                effectiveScopeLevel = role.defaultScope,
                zoneId = zoneId,
                stateId = stateId,
                cityId = cityId,
                centreId = centreId,
                centreCode = centreId?.uppercase()
            )
        )
    }
}
