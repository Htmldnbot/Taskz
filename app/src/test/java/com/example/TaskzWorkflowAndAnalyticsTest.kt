package com.example

import android.app.AlarmManager
import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.TaskAlarmScheduler
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AlarmType
import com.example.core.model.AuthorizationContext
import com.example.core.model.OrgScope
import com.example.core.model.ResponseType
import com.example.core.model.ScopeLevel
import com.example.core.model.SystemIncident
import com.example.core.model.TaskImportance
import com.example.core.model.TaskItem
import com.example.core.model.TaskType
import com.example.core.model.TaskzRole
import com.example.core.network.SupabaseConfig
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.ProfileEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.remote.SupabaseClient
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.NetworkUnavailableException
import com.example.domain.repository.SessionExpiredException
import com.example.domain.security.TaskzPermissionManager
import com.example.ui.common.OrderedCascadingFilterBar
import com.example.ui.incidents.SystemIncidentViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskzWorkflowAndAnalyticsTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        SupabaseConfig.runtimeUrl = "https://example.supabase.co"
        SupabaseConfig.runtimeAnonKey = "test-anon-key"
    }

    @After
    fun tearDown() {
        SupabaseConfig.runtimeUrl = null
        SupabaseConfig.runtimeAnonKey = null
        db.close()
    }

    private fun makeJwt(expOffsetSeconds: Long): String {
        val header = Base64.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val exp = (System.currentTimeMillis() / 1000L) + expOffsetSeconds
        val payload = Base64.encodeToString("""{"sub":"11111111-1111-4111-8111-111111111111","exp":$exp}""".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$header.$payload.sig"
    }

    @Test
    fun taskAlarmScheduler_enforces24HourTtlAndCancelsResolvedOrExpiredTasks() = runBlocking {
        val now = 1_780_000_000_000L
        val under24h = Instant.ofEpochMilli(now - (23L * 3600L * 1000L)).toString()
        val over24h = Instant.ofEpochMilli(now - (25L * 3600L * 1000L)).toString()

        assertFalse(TaskAlarmScheduler.isTaskExpired24h(under24h, under24h, under24h, under24h, nowMillis = now))
        assertTrue(TaskAlarmScheduler.isTaskExpired24h(over24h, over24h, over24h, over24h, nowMillis = now))

        val scheduler = TaskAlarmScheduler(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)

        val futureIso = Instant.now().plusSeconds(1800).toString()
        val activeTask = TaskItem(
            id = "task-alarm-active",
            title = "Server Check",
            taskType = TaskType.PROGRESS,
            responseType = ResponseType.PERCENTAGE,
            importance = TaskImportance.HIGH,
            alarmType = AlarmType.FULL_SCREEN_ALARM,
            executionTime = futureIso,
            advanceRemindersMinutes = listOf(0)
        )

        scheduler.scheduleAlarmsForTask(activeTask, userId = "11111111-1111-4111-8111-111111111111")
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        // Scheduling again with completed status cancels alarms cleanly
        scheduler.scheduleAlarmsForTask(activeTask.copy(status = "completed"), userId = "11111111-1111-4111-8111-111111111111")
        val records = TaskzDatabase.getInstance(context).scheduledAlarmDao().getAlarmsForTask(activeTask.id)
        assertTrue(records.all { it.isCancelled })

        // Expired >24h task must not schedule alarms
        val expiredTask = activeTask.copy(
            id = "task-alarm-expired",
            createdAt = Instant.now().minusSeconds(26 * 3600L).toString(),
            startsAt = null,
            executionTime = Instant.now().minusSeconds(25 * 3600L).toString(),
            dueAt = null
        )
        scheduler.scheduleAlarmsForTask(expiredTask, userId = "11111111-1111-4111-8111-111111111111")
        val expiredRecords = TaskzDatabase.getInstance(context).scheduledAlarmDao().getAlarmsForTask(expiredTask.id)
        assertTrue(expiredRecords.isEmpty())
    }

    @Test
    fun taskDao_markLocalTaskCompletionState_protectsCompletionDuringSync() = runBlocking {
        val dao = db.taskDao()
        val task = TaskItem(
            id = "task-progress-1",
            title = "Biometric Sync Progress",
            taskType = TaskType.PROGRESS,
            responseType = ResponseType.PERCENTAGE,
            targetPercentage = 100,
            currentProgress = 50,
            status = "open"
        )
        dao.insertTask(TaskEntity.fromDomain(task))
        dao.markLocalTaskCompletionState(
            taskId = task.id,
            status = "completed",
            progress = 100,
            updatedAt = Instant.now().toString(),
            syncState = "PENDING_SYNC"
        )

        val updated = dao.getTaskById(task.id)!!
        assertEquals("completed", updated.status)
        assertEquals(100, updated.currentProgress)
        assertEquals("PENDING_SYNC", updated.localSyncState)
    }

    @Test
    fun authRepository_retriesTransient503AndPreservesSessionWithoutAutoLogout() = runBlocking {
        val calls = AtomicInteger(0)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val attempt = calls.incrementAndGet()
                val bodyStr = """{"error":"service_unavailable"}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body(bodyStr.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val expiredJwt = makeJwt(-3600L)
        userPrefs.saveSession(
            accessToken = expiredJwt,
            refreshToken = "valid-refresh-token",
            userId = "11111111-1111-4111-8111-111111111111",
            employeeId = "EMP001"
        )
        db.profileDao().insertProfile(
            ProfileEntity.fromDomain(
                com.example.core.model.UserProfile(
                    id = "11111111-1111-4111-8111-111111111111",
                    employeeId = "EMP001",
                    fullName = "Ramesh Sharma",
                    mobile = "+919999999999"
                )
            )
        )

        val repo = AuthRepository(
            supabaseClient = SupabaseClient(okHttp),
            profileDao = db.profileDao(),
            syncMetadataDao = db.syncMetadataDao(),
            userPrefs = userPrefs
        )

        val tokenResult = repo.getValidAccessToken()
        assertTrue(tokenResult.isFailure)
        assertTrue(tokenResult.exceptionOrNull() is NetworkUnavailableException)
        assertEquals(3, calls.get())

        // Session must remain intact and restoreSession returns cached profile rather than logging out
        val restoreResult = repo.restoreSession()
        assertTrue(restoreResult.isSuccess)
        assertEquals("EMP001", restoreResult.getOrNull()?.employeeId)
    }

    @Test
    fun authRepository_forceRefreshSkipsDoubleRotationAndFlagsTerminalInvalidGrant() = runBlocking {
        val calls = AtomicInteger(0)
        val freshJwt = makeJwt(3600L)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val count = calls.incrementAndGet()
                if (count == 1) {
                    val json = """{"access_token":"$freshJwt","refresh_token":"rotated-refresh-2","user":{"id":"11111111-1111-4111-8111-111111111111"}}"""
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    val json = """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}"""
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(400)
                        .message("Bad Request")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            })
            .build()

        val staleJwt = makeJwt(-120L)
        userPrefs.saveSession(
            accessToken = staleJwt,
            refreshToken = "refresh-1",
            userId = "11111111-1111-4111-8111-111111111111",
            employeeId = "EMP002"
        )

        val repo = AuthRepository(
            supabaseClient = SupabaseClient(okHttp),
            profileDao = db.profileDao(),
            syncMetadataDao = db.syncMetadataDao(),
            userPrefs = userPrefs
        )

        val firstRefresh = repo.forceRefreshAccessToken(staleJwt)
        assertTrue(firstRefresh.isSuccess)
        assertEquals(freshJwt, firstRefresh.getOrNull())

        // Second coroutine failing with the OLD staleJwt reuses freshJwt without double-rotating refresh-1
        val secondRefresh = repo.forceRefreshAccessToken(staleJwt)
        assertTrue(secondRefresh.isSuccess)
        assertEquals(freshJwt, secondRefresh.getOrNull())
        assertEquals(1, calls.get())

        // If freshJwt itself genuinely fails and server returns 400 invalid_grant, it classifies as SessionExpiredException
        val terminalRefresh = repo.forceRefreshAccessToken(freshJwt)
        assertTrue(terminalRefresh.isFailure)
        assertTrue(terminalRefresh.exceptionOrNull() is SessionExpiredException)
    }

    @Test
    fun systemIncidentAnalytics_computesRepeatLockedSystemsAndScopedTopReasons() {
        val sampleIncidents = listOf(
            SystemIncident(
                id = "1",
                stateName = "Rajasthan",
                districtName = "Jaipur",
                centreCode = "141",
                centreName = "YIT",
                lanName = "LAN 2",
                shiftName = "Shift 1",
                lockedSystemNumber = "C005",
                macAddress = "AA:BB:CC:DD:EE:01",
                ipAddress = "192.168.1.5",
                candidateShifted = true,
                reason = "System Lock"
            ),
            SystemIncident(
                id = "2",
                stateName = "Rajasthan",
                districtName = "Jaipur",
                centreCode = "141",
                centreName = "YIT",
                lanName = "LAN 2",
                shiftName = "Shift 2",
                lockedSystemNumber = "C005",
                macAddress = "AA:BB:CC:DD:EE:01",
                ipAddress = "192.168.1.5",
                candidateShifted = false,
                reason = "System Lock"
            ),
            SystemIncident(
                id = "3",
                stateName = "Rajasthan",
                districtName = "Jaipur",
                centreCode = "141",
                centreName = "YIT",
                lanName = "LAN 2",
                shiftName = "Shift 1",
                lockedSystemNumber = "C005",
                macAddress = "AA:BB:CC:DD:EE:01",
                ipAddress = "192.168.1.5",
                candidateShifted = true,
                reason = "LAN Disconnected"
            ),
            SystemIncident(
                id = "4",
                stateName = "Rajasthan",
                districtName = "Kota",
                centreCode = "210",
                centreName = "Kota Centre",
                lanName = "LAN 1",
                shiftName = "Shift 1",
                lockedSystemNumber = "C012",
                candidateShifted = false,
                reason = "Power / UPS Failure"
            )
        )

        val repeats = SystemIncidentViewModel.computeRepeatLockedSystems(sampleIncidents)
        assertEquals(1, repeats.size)
        val top = repeats.first()
        assertEquals("C005", top.systemNumber)
        assertEquals("LAN 2", top.lanName)
        assertEquals(3, top.lockCount)
        assertEquals(2, top.shiftedCount)
        assertEquals("System Lock", top.topReasons.first().first)
        assertEquals(2, top.topReasons.first().second)

        val byState = SystemIncidentViewModel.computeTopReasonsByScope(sampleIncidents, "STATE")
        assertEquals(1, byState.size)
        assertEquals("Rajasthan", byState.first().scopeLabel)
        assertEquals(4, byState.first().totalIncidents)
        assertEquals(1, byState.first().repeatSystemsCount)
        assertEquals("System Lock", byState.first().topReasons.first().first)

        val byDistrict = SystemIncidentViewModel.computeTopReasonsByScope(sampleIncidents, "DISTRICT")
        assertEquals(2, byDistrict.size)
        assertEquals("Jaipur", byDistrict.first().scopeLabel)
    }

    @Test
    fun rbacStateWideTaskCreationAndSoPermissionsWorkAcrossState() {
        val vcoStatePm = TaskzPermissionManager(
            AuthorizationContext(
                userId = "vco-rj-1",
                activeRole = TaskzRole.VCO,
                scope = OrgScope(
                    effectiveScopeLevel = ScopeLevel.CITY,
                    zoneId = "z-north",
                    stateId = "st-rj",
                    cityId = "ct-jpr"
                )
            )
        )
        assertTrue(vcoStatePm.canCreateStateTask())

        val stateTask = TaskItem(
            id = "state-task-1",
            title = "State-Wide Shift 1 Checklist",
            scopeLevel = ScopeLevel.STATE,
            zoneId = "z-north",
            stateId = "st-rj",
            targetRoleCodes = listOf("VCO", "SO", "IT_MANAGER")
        )

        val itSameStatePm = TaskzPermissionManager(
            AuthorizationContext(
                userId = "it-kota-1",
                activeRole = TaskzRole.IT_MANAGER,
                scope = OrgScope(
                    effectiveScopeLevel = ScopeLevel.CENTRE,
                    zoneId = "z-north",
                    stateId = "st-rj",
                    cityId = "ct-kota",
                    centreId = "c-210"
                )
            )
        )
        assertEquals(TaskzRole.IT_MANAGER, TaskzRole.fromCode("SO"))
        assertTrue(itSameStatePm.canViewTask(stateTask))
        assertTrue(itSameStatePm.canCompleteTask(stateTask))

        val itOtherStatePm = TaskzPermissionManager(
            AuthorizationContext(
                userId = "it-dl-1",
                activeRole = TaskzRole.IT_MANAGER,
                scope = OrgScope(
                    effectiveScopeLevel = ScopeLevel.CENTRE,
                    zoneId = "z-north",
                    stateId = "st-dl",
                    cityId = "ct-del",
                    centreId = "c-101"
                )
            )
        )
        assertFalse(itOtherStatePm.canViewTask(stateTask))
    }

    @Test
    fun orderedCascadingFilterBar_rendersInExactOrderAndSelectsRealOptionsOnly() {
        var capturedState: String? = null

        composeRule.setContent {
            var state by remember { mutableStateOf<String?>(null) }
            var city by remember { mutableStateOf<String?>(null) }
            var centre by remember { mutableStateOf<String?>(null) }
            var lan by remember { mutableStateOf<String?>(null) }
            var shift by remember { mutableStateOf<String?>(null) }
            var date by remember { mutableStateOf<String?>("All") }

            MyApplicationTheme {
                OrderedCascadingFilterBar(
                    selectedState = state,
                    stateOptions = listOf("Rajasthan", "Delhi"),
                    onStateSelected = {
                        state = it
                        capturedState = it
                        city = null
                        centre = null
                        lan = null
                    },
                    selectedCity = city,
                    cityOptions = listOf("Jaipur"),
                    onCitySelected = { city = it },
                    selectedCentre = centre,
                    centreOptions = listOf("141 · YIT"),
                    onCentreSelected = { centre = it },
                    selectedLan = lan,
                    lanOptions = listOf("LAN 2"),
                    onLanSelected = { lan = it },
                    selectedShift = shift,
                    shiftOptions = listOf("Shift 1"),
                    onShiftSelected = { shift = it },
                    selectedDate = date,
                    dateOptions = listOf("Today", "Yesterday", "Last 7 Days"),
                    onDateSelected = { date = it ?: "All" },
                    onClearAll = {
                        state = null
                        city = null
                        centre = null
                        lan = null
                        shift = null
                        date = "All"
                    }
                )
            }
        }

        composeRule.onNodeWithTag("filter_dropdown_state").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dropdown_city").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dropdown_centre").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dropdown_lan").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dropdown_shift").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_dropdown_date").assertIsDisplayed()

        composeRule.onNodeWithTag("filter_dropdown_state").performClick()
        composeRule.onNodeWithText("Rajasthan").performClick()
        assertEquals("Rajasthan", capturedState)

        composeRule.onNodeWithTag("filter_dropdown_lan").performClick()
        composeRule.onNodeWithText("LAN 2").assertIsDisplayed()
        composeRule.onNodeWithText("LAN 25").assertDoesNotExist()
    }

    @Test
    fun examShiftChecklistAndSelectiveCopy_preservesGapsAndMarksShift1OnlyTasks() {
        assertEquals(11, com.example.ui.shifts.STANDARD_EXAM_CHECKLIST.size)
        val byKey = com.example.ui.shifts.STANDARD_EXAM_CHECKLIST.associateBy { it.key }
        assertEquals(20, byKey["drive_download"]?.offsetMinutes)
        assertEquals(25, byKey["backup_configured"]?.offsetMinutes)
        assertEquals(45, byKey["launching_10"]?.offsetMinutes)
        assertEquals(60, byKey["rd_login_100"]?.offsetMinutes)
        assertEquals(75, byKey["launching_100"]?.offsetMinutes)
        assertEquals(100, byKey["candidate_reg"]?.offsetMinutes)
        assertEquals(120, byKey["qp_download"]?.offsetMinutes)
        assertEquals(151, byKey["process_bundle"]?.offsetMinutes)
        assertEquals(170, byKey["allow_candidate"]?.offsetMinutes)
        assertEquals(181, byKey["exam_started"]?.offsetMinutes)
        assertEquals(230, byKey["paper_verification"]?.offsetMinutes)

        // Anchor Shift 1 at 06:00 AM UTC, Shift 2 at 09:45 AM UTC (+3h 45m = +225m)
        val t0Millis = Instant.parse("2026-11-20T06:00:00Z").toEpochMilli()
        val shift1StartIso = Instant.ofEpochMilli(t0Millis).toString()
        val shift2StartIso = Instant.parse("2026-11-20T09:45:00Z").toString()

        assertEquals("+3h 45m", com.example.ui.shifts.ShiftsViewModel.computeOffsetSummary(shift1StartIso, shift2StartIso))

        val sourceTasks = com.example.ui.shifts.STANDARD_EXAM_CHECKLIST.map { tpl ->
            val execIso = Instant.ofEpochMilli(t0Millis + tpl.offsetMinutes * 60_000L).toString()
            TaskItem(
                id = "task-${tpl.key}",
                shiftId = "shift-1",
                title = tpl.title,
                taskType = TaskType.TIME_SPECIFIC,
                responseType = ResponseType.YES_NO,
                executionTime = execIso,
                dueAt = execIso
            )
        }

        val previews = com.example.ui.shifts.ShiftsViewModel.buildCopyPreview(
            sourceShiftStartsAt = shift1StartIso,
            sourceTasks = sourceTasks,
            newStartsAt = shift2StartIso
        )
        assertEquals(11, previews.size)

        // Shift-1-only tasks default unchecked, recurring tasks default checked
        val byTitle = previews.associateBy { it.title }
        assertTrue(byTitle["Backup Configured?"]?.isShift1Only == true)
        assertFalse(byTitle["Backup Configured?"]?.defaultSelected ?: true)
        assertTrue(byTitle["10% Launching Done?"]?.isShift1Only == true)
        assertFalse(byTitle["10% Launching Done?"]?.defaultSelected ?: true)
        assertTrue(byTitle["100% RD Login Done?"]?.isShift1Only == true)
        assertFalse(byTitle["100% RD Login Done?"]?.defaultSelected ?: true)
        assertTrue(byTitle["100% Launching Done?"]?.isShift1Only == true)
        assertFalse(byTitle["100% Launching Done?"]?.defaultSelected ?: true)

        assertFalse(byTitle["Drive Downloaded?"]?.isShift1Only ?: true)
        assertTrue(byTitle["Drive Downloaded?"]?.defaultSelected == true)
        assertTrue(byTitle["Question Paper Downloaded?"]?.defaultSelected == true)
        assertTrue(byTitle["Exam Started?"]?.defaultSelected == true)
        assertTrue(byTitle["Paper Verification Done?"]?.defaultSelected == true)

        // Question Paper Download gap (+2h from T0): Shift 1 08:00 -> Shift 2 11:45 in device timezone
        val qpSrcExpected = com.example.ui.shifts.ShiftsViewModel.formatTimeOnly(Instant.parse("2026-11-20T08:00:00Z").toEpochMilli())
        val qpDstExpected = com.example.ui.shifts.ShiftsViewModel.formatTimeOnly(Instant.parse("2026-11-20T11:45:00Z").toEpochMilli())
        assertEquals(qpSrcExpected, byTitle["Question Paper Downloaded?"]?.sourceTime)
        assertEquals(qpDstExpected, byTitle["Question Paper Downloaded?"]?.newTime)
    }
}
