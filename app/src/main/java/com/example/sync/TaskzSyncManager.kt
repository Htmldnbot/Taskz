package com.example.sync

import android.content.Context
import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AppNotification
import com.example.core.model.AssignedRoleInfo
import com.example.core.model.AuthorizationContext
import com.example.core.model.OrgCentre
import com.example.core.model.OrgCity
import com.example.core.model.OrgHierarchyData
import com.example.core.model.OrgScope
import com.example.core.model.OrgState
import com.example.core.model.OrgZone
import com.example.core.model.ScopeLevel
import com.example.core.model.ScopedDashboardSummary
import com.example.core.model.SyncStatus
import com.example.core.model.TaskzRole
import com.example.core.network.NetworkMonitor
import com.example.core.network.SupabaseConfig
import com.example.data.local.TaskzDatabase
import com.example.data.local.entity.CachedAuthContextEntity
import com.example.data.local.entity.CachedOrgNodeEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.remote.RealtimeEventType
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHttpException
import com.example.data.remote.TaskzRealtimeManager
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.SessionExpiredException
import com.example.domain.repository.SystemIncidentRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.security.TaskzPermissionManager
import com.example.notifications.DeviceTokenManager
import com.example.notifications.PushNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Centralized synchronization, lifecycle, role-switch cache invalidation, and delta-pull coordinator.
 * Guarantees automatic background/foreground/network-recovery sync with zero manual Sync button requirement.
 */
class TaskzSyncManager(
    private val context: Context,
    private val database: TaskzDatabase,
    private val userPrefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    private val taskRepository: TaskRepository,
    private val systemIncidentRepository: SystemIncidentRepository,
    private val permissionManager: TaskzPermissionManager,
    private val realtimeManager: TaskzRealtimeManager,
    private val deviceTokenManager: DeviceTokenManager,
    private val pushNotificationManager: PushNotificationManager
) {
    companion object {
        private const val TAG = "TaskzSyncManager"
        private const val NETWORK_DEBOUNCE_MS = 1500L
        private const val REALTIME_COALESCE_MS = 450L
    }

    private val syncMutex = Mutex()
    private var appScope: CoroutineScope? = null
    private var networkDebounceJob: Job? = null
    private var realtimeCoalesceJob: Job? = null
    @Volatile private var authPaused: Boolean = false
    @Volatile private var started = false

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    private val _scopedDashboardSummary = MutableStateFlow(ScopedDashboardSummary())
    val scopedDashboardSummary: StateFlow<ScopedDashboardSummary> = _scopedDashboardSummary.asStateFlow()

    private val _orgHierarchy = MutableStateFlow(OrgHierarchyData())
    val orgHierarchy: StateFlow<OrgHierarchyData> = _orgHierarchy.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notificationsFlow: Flow<List<AppNotification>> =
        userPrefs.userSessionFlow
            .map { it.userId.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(emptyList())
                else database.notificationDao().getNotificationsFlow(uid).map { list ->
                    list.map { it.toDomain() }
                }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationCount: Flow<Int> =
        userPrefs.userSessionFlow
            .map { it.userId.orEmpty() }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid.isBlank()) flowOf(0)
                else database.notificationDao().getUnreadCountFlow(uid)
            }

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        appScope = scope

        scope.launch {
            restoreCachedContext()
        }

        scope.launch {
            networkMonitor.isOnlineFlow
                .distinctUntilChanged()
                .collect { isOnline ->
                    networkDebounceJob?.cancel()
                    networkDebounceJob = scope.launch {
                        delay(NETWORK_DEBOUNCE_MS)
                        handleDebouncedConnectivityChange(isOnline)
                    }
                }
        }

        scope.launch {
            realtimeManager.signals.collect { signal ->
                when (signal.type) {
                    RealtimeEventType.ROLE_OR_SCOPE_CHANGED -> {
                        Log.i(TAG, "Realtime detected role/scope mutation; forcing clean authorization refresh")
                        syncNow(forceFull = true)
                    }
                    RealtimeEventType.NOTIFICATION_CREATED,
                    RealtimeEventType.TASK_CHANGED,
                    RealtimeEventType.INCIDENT_CHANGED -> {
                        realtimeCoalesceJob?.cancel()
                        realtimeCoalesceJob = scope.launch {
                            delay(REALTIME_COALESCE_MS)
                            syncNow(forceFull = false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun restoreCachedContext() {
        val session = userPrefs.getUserSession()
        val uid = session.userId
        if (!session.isAuthenticated || uid.isNullOrBlank()) return

        val cached = database.authorizationDao().getAuthContext(uid)
        if (cached != null) {
            val domainCtx = cached.toDomain()
            permissionManager.updateContext(domainCtx)
            realtimeManager.updateSessionAndRole(
                accessToken = session.accessToken,
                userId = uid,
                activeRoleCode = cached.activeRoleCode,
                orgScope = domainCtx.scope
            )
        } else {
            val cachedProfile = database.profileDao().getCachedProfile()?.toDomain()
            if (cachedProfile != null) {
                val role = TaskzRole.fromCode(cachedProfile.activeRoleCode)
                permissionManager.updateContext(
                    AuthorizationContext(
                        userId = cachedProfile.id,
                        employeeId = cachedProfile.employeeId,
                        fullName = cachedProfile.fullName,
                        isActive = cachedProfile.isActive,
                        isOnLeave = cachedProfile.isOnLeave,
                        activeRole = role,
                        primaryRole = TaskzRole.fromCode(cachedProfile.primaryRoleCode),
                        assignedRoles = cachedProfile.assignedRoleCodes.map { code ->
                            val r = TaskzRole.fromCode(code)
                            AssignedRoleInfo(
                                roleId = code,
                                roleCode = r.code,
                                roleName = r.displayName,
                                hierarchyLevel = r.hierarchyRank,
                                defaultScopeLevel = r.defaultScope.code,
                                isPrimary = r.code == cachedProfile.primaryRoleCode
                            )
                        },
                        scope = OrgScope(
                            effectiveScopeLevel = role.defaultScope,
                            zoneId = cachedProfile.zoneId,
                            zoneName = cachedProfile.zoneName,
                            stateId = cachedProfile.stateId,
                            stateName = cachedProfile.stateName,
                            cityId = cachedProfile.cityId,
                            cityName = cachedProfile.cityName,
                            centreId = cachedProfile.centreId,
                            centreCode = cachedProfile.centreCode
                        ),
                        roleVersion = cachedProfile.roleVersion,
                        scopeVersion = cachedProfile.scopeVersion
                    )
                )
            }
        }

        val cachedNodes = database.authorizationDao().getAllOrgNodes()
        if (cachedNodes.isNotEmpty()) {
            _orgHierarchy.value = nodesToHierarchy(cachedNodes)
        }
    }

    private suspend fun handleDebouncedConnectivityChange(isOnline: Boolean) {
        val session = userPrefs.getUserSession()
        if (!session.isAuthenticated) {
            database.syncMetadataDao().updateSyncStatus(SyncStatus.OFFLINE.name, "Signed out")
            return
        }
        val uid = session.userId.orEmpty()
        val pendingCount = if (uid.isNotBlank()) database.outboxDao().getPendingEventsForUser(uid).size else 0

        if (isOnline) {
            if (authPaused) return
            realtimeManager.recoverConnectionIfEligible()
            runCatching { SyncWorker.enqueueSync(context) }
            runCatching { deviceTokenManager.registerActiveDeviceToken() }
            syncNow(forceFull = false)
        } else {
            realtimeManager.onNetworkUnavailable()
            val offlineMsg = if (pendingCount > 0) {
                "Offline — $pendingCount pending action(s) queued safely"
            } else {
                "Offline — Cached authorized data available"
            }
            database.syncMetadataDao().updateSyncStatus(SyncStatus.OFFLINE.name, offlineMsg)
        }
    }

    fun onAppForegrounded() {
        realtimeManager.setForegroundState(true)
        appScope?.launch {
            restoreCachedContext()
            val session = userPrefs.getUserSession()
            if (session.isAuthenticated) {
                authPaused = false
                if (networkMonitor.isCurrentlyConnected()) {
                    runCatching { deviceTokenManager.registerActiveDeviceToken() }
                    syncNow(forceFull = false)
                }
            }
        }
    }

    fun onAppBackgrounded() {
        realtimeManager.setForegroundState(false)
    }

    suspend fun syncNow(forceFull: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val session = userPrefs.getUserSession()
            val userId = session.userId
            if (!session.isAuthenticated || userId.isNullOrBlank()) {
                return@withLock Result.success(Unit)
            }

            if (!networkMonitor.isCurrentlyConnected()) {
                val pendingCount = database.outboxDao().getPendingEventsForUser(userId).size
                val msg = if (pendingCount > 0) {
                    "Offline — $pendingCount pending operation(s) awaiting connection"
                } else {
                    "Offline — Using authorized local cache"
                }
                database.syncMetadataDao().updateSyncStatus(SyncStatus.OFFLINE.name, msg)
                return@withLock Result.failure(Exception("Offline"))
            }

            if (!SupabaseConfig.isConfigured) {
                return@withLock Result.failure(Exception("Supabase not configured"))
            }

            _isSyncing.value = true
            _lastSyncError.value = null
            database.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCING.name, "Synchronizing...")

            try {
                // 1. Validate / refresh session token
                val tokenRes = authRepository.getValidAccessToken()
                if (tokenRes.isFailure) {
                    val ex = tokenRes.exceptionOrNull()!!
                    if (authRepository.isNetworkFailure(ex)) {
                        database.syncMetadataDao().updateSyncStatus(SyncStatus.OFFLINE.name, "Network unavailable")
                    } else {
                        authPaused = true
                        _lastSyncError.value = "Session expired. Please sign in again."
                        database.syncMetadataDao().updateSyncStatus(
                            SyncStatus.ERROR.name,
                            "Authentication required: Please sign in to resume sync"
                        )
                    }
                    return@withLock Result.failure(ex)
                }
                authPaused = false
                var token = tokenRes.getOrThrow()

                // 2. Push pending local outbox operations first (tasks, responses, acks, incidents, escalations)
                runCatching { taskRepository.syncPendingTasks() }
                runCatching { systemIncidentRepository.syncPendingIncidents() }

                // 3. Delta sync pull (bounded jittered backoff for transient 429/5xx; no infinite retry on 401/403)
                val sinceIso = if (forceFull) null else userPrefs.getLastDeltaSyncIso()
                val knownVersion = if (forceFull) null else permissionManager.getCurrentContext().combinedVersion

                var pullRes = executeDeltaPullWithBackoff(token, sinceIso, knownVersion)
                if (pullRes.isFailure) {
                    val ex = pullRes.exceptionOrNull()
                    if (ex is SupabaseHttpException && ex.isAuthFailure) {
                        val refreshed = authRepository.forceRefreshAccessToken(token).getOrNull()
                        if (!refreshed.isNullOrBlank()) {
                            token = refreshed
                            pullRes = executeDeltaPullWithBackoff(token, sinceIso, knownVersion)
                        }
                    }
                }

                if (pullRes.isSuccess) {
                    val payload = pullRes.getOrThrow()
                    reconcileDeltaPayload(token, payload)
                    runCatching { taskRepository.purgeExpiredTasksOlderThan24Hours() }
                } else {
                    val err = pullRes.exceptionOrNull()
                    if (err is SupabaseHttpException && err.isAuthFailure) {
                        authPaused = true
                        _lastSyncError.value = err.message
                        database.syncMetadataDao().updateSyncStatus(
                            SyncStatus.ERROR.name,
                            "Authorization pause: ${err.message}"
                        )
                        return@withLock Result.failure(err)
                    }
                    // If delta_sync_pull RPC is not yet deployed (404/PGRST202), fall back cleanly to legacy sync endpoints
                    val errMsg = err?.message.orEmpty()
                    val isMissingRpc = (err is SupabaseHttpException && err.statusCode == 404) ||
                        errMsg.contains("PGRST202", ignoreCase = true) ||
                        errMsg.contains("Could not find the function", ignoreCase = true)
                    if (isMissingRpc) {
                        runCatching { taskRepository.syncGroupsFromServer(token) }
                        runCatching { taskRepository.syncShiftsFromServer(token) }
                        runCatching { taskRepository.syncTasksFromServer(token) }
                        runCatching { systemIncidentRepository.syncIncidentsFromServer() }
                        runCatching { taskRepository.purgeExpiredTasksOlderThan24Hours() }
                    } else if (err != null) {
                        Log.w(TAG, "Delta sync pull warning: ${err.message}")
                        _lastSyncError.value = err.message
                    }
                }

                // 4. Refresh SyncMetadata cleanly
                val now = System.currentTimeMillis()
                val pendingEvents = database.outboxDao().getPendingEventsForUser(userId)
                val pendingCount = pendingEvents.size
                database.syncMetadataDao().updateLastServerFetch(now)
                if (pendingCount == 0) {
                    database.syncMetadataDao().updateLastSuccessfulSync(now)
                    database.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Synced")
                } else {
                    database.syncMetadataDao().updateSyncStatus(
                        SyncStatus.PENDING.name,
                        "$pendingCount pending operation(s) awaiting sync"
                    )
                }

                Result.success(Unit)
            } catch (e: SessionExpiredException) {
                authPaused = true
                _lastSyncError.value = e.message
                database.syncMetadataDao().updateSyncStatus(SyncStatus.ERROR.name, e.message ?: "Session expired")
                Result.failure(e)
            } catch (e: Exception) {
                Log.w(TAG, "syncNow failed cleanly: ${e.message}")
                _lastSyncError.value = e.message
                Result.failure(e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun executeDeltaPullWithBackoff(
        token: String,
        sinceIso: String?,
        knownVersion: Long?
    ): Result<JSONObject> {
        var attempt = 0
        var lastResult: Result<JSONObject> = Result.failure(Exception("Unattempted"))
        while (attempt < 3) {
            lastResult = supabaseClient.deltaSyncPull(token, sinceIso, knownVersion)
            if (lastResult.isSuccess) return lastResult
            val err = lastResult.exceptionOrNull()
            if (err is SupabaseHttpException) {
                if (err.isAuthFailure || err.isConflict || !err.isRetryable) {
                    return lastResult
                }
            } else if (err != null && !authRepository.isNetworkFailure(err)) {
                return lastResult
            }
            attempt++
            if (attempt < 3) {
                val backoffMs = (750L shl attempt) + Random.nextLong(150L, 600L)
                delay(backoffMs)
            }
        }
        return lastResult
    }

    private suspend fun reconcileDeltaPayload(token: String, payload: JSONObject) {
        val authJson = payload.optJSONObject("authorization")
        val isFullRefresh = payload.optBoolean("is_full_refresh", false)

        if (isFullRefresh) {
            Log.i(TAG, "Full role/scope refresh signaled by server; invalidating stale role/scope cache")
            database.clearRoleScopeCacheForSwitch()
            taskRepository.clearInMemoryCaches()
        }

        if (authJson != null) {
            val authCtx = supabaseClient.parseAuthorizationContext(authJson)
            if (!authCtx.isActive) {
                onLogout()
                return
            }
            permissionManager.updateContext(authCtx)
            database.authorizationDao().upsertAuthContext(authCtx.toEntity())
            syncProfileSnapshot(authCtx)
            if (authCtx.isOnLeave) {
                runCatching { com.example.alarm.TaskAlarmScheduler(context).cancelAllAlarmsForUser(authCtx.userId) }
            }
            realtimeManager.updateSessionAndRole(token, authCtx.userId, authCtx.activeRole.code, authCtx.scope)
        }

        val dashJson = payload.optJSONObject("dashboard")
        if (dashJson != null) {
            _scopedDashboardSummary.value = ScopedDashboardSummary(
                activeRole = TaskzRole.fromCode(dashJson.optString("active_role")),
                pendingTasks = dashJson.optInt("pending_tasks", 0),
                overdueTasks = dashJson.optInt("overdue_tasks", 0),
                assignedTasks = dashJson.optInt("assigned_tasks", 0),
                openIncidents = dashJson.optInt("open_incidents", 0),
                escalatedIncidents = dashJson.optInt("escalated_incidents", 0),
                unreadNotifications = dashJson.optInt("unread_notifications", 0),
                activeUsers = dashJson.optInt("active_users", 0),
                totalCentres = dashJson.optInt("total_centres", 0)
            )
        }

        var cursorSafeToAdvance = true

        val tasksArr = payload.optJSONArray("tasks")
        if (tasksArr != null && tasksArr.length() > 0) {
            taskRepository.reconcileTasksFromJson(tasksArr, scheduleAlarms = true)
            val respSync = taskRepository.syncResponsesFromServer(token)
            if (respSync.isFailure) {
                cursorSafeToAdvance = false
            }
        } else if (isFullRefresh) {
            runCatching { taskRepository.syncGroupsFromServer(token) }
            runCatching { taskRepository.syncShiftsFromServer(token) }
        }

        val deletedTaskIdsArr = payload.optJSONArray("deleted_task_ids")
        if (deletedTaskIdsArr != null && deletedTaskIdsArr.length() > 0) {
            val ids = List(deletedTaskIdsArr.length()) { idx -> deletedTaskIdsArr.optString(idx) }
                .filter { it.isNotBlank() }
            if (ids.isNotEmpty()) {
                taskRepository.removeDeletedTasksLocally(ids)
            }
        }

        val incidentsArr = payload.optJSONArray("incidents")
        if (incidentsArr != null && incidentsArr.length() > 0) {
            systemIncidentRepository.reconcileIncidentsFromJson(incidentsArr)
        }

        val deletedIncidentIdsArr = payload.optJSONArray("deleted_incident_ids")
        if (deletedIncidentIdsArr != null && deletedIncidentIdsArr.length() > 0) {
            val ids = List(deletedIncidentIdsArr.length()) { idx -> deletedIncidentIdsArr.optString(idx) }
                .filter { it.isNotBlank() }
            ids.forEach { id ->
                database.systemIncidentDao().deleteIncident(id)
                database.systemIncidentDao().deleteAuditsForIncident(id)
            }
        }

        val notificationsArr = payload.optJSONArray("notifications")
        if (notificationsArr != null && notificationsArr.length() > 0) {
            val parsed = MutableList(notificationsArr.length()) { idx ->
                supabaseClient.parseNotificationJson(notificationsArr.getJSONObject(idx))
            }
            database.notificationDao().insertNotifications(parsed.map { NotificationEntity.fromDomain(it) })
            for (notif in parsed) {
                if (!notif.isRead) {
                    pushNotificationManager.dispatchIfUnique(notif)
                }
            }
        }

        if (isFullRefresh || _orgHierarchy.value.centres.isEmpty()) {
            refreshOrgHierarchy(token)
        }

        val serverNow = payload.optString("server_now").takeIf { it.isNotBlank() && !it.equals("null", true) }
        if (cursorSafeToAdvance && !serverNow.isNullOrBlank()) {
            userPrefs.saveLastDeltaSyncIso(serverNow)
        }
    }

    private suspend fun syncProfileSnapshot(ctx: AuthorizationContext) {
        val existing = database.profileDao().getCachedProfile() ?: return
        database.profileDao().insertProfile(
            existing.copy(
                activeRoleCode = ctx.activeRole.code,
                primaryRoleCode = ctx.primaryRole.code,
                assignedRoleCodesCsv = ctx.assignedRoles.joinToString(",") { it.roleCode }.ifBlank { ctx.activeRole.code },
                appRole = if (ctx.activeRole == TaskzRole.SUPER_ADMIN) "admin" else "employee",
                zoneId = ctx.scope.zoneId,
                zoneName = ctx.scope.zoneName,
                stateId = ctx.scope.stateId,
                stateName = ctx.scope.stateName,
                cityId = ctx.scope.cityId,
                cityName = ctx.scope.cityName,
                centreId = ctx.scope.centreId,
                centreCode = ctx.scope.centreCode,
                roleVersion = ctx.roleVersion,
                scopeVersion = ctx.scopeVersion,
                isActive = ctx.isActive,
                isOnLeave = ctx.isOnLeave
            )
        )
    }

    suspend fun refreshOrgHierarchy(tokenOverride: String? = null): Result<OrgHierarchyData> = withContext(Dispatchers.IO) {
        val token = tokenOverride ?: authRepository.getValidAccessToken().getOrNull()
            ?: return@withContext Result.failure(Exception("Authentication required"))
        val res = supabaseClient.fetchOrgHierarchy(token)
        if (res.isSuccess) {
            val hierarchy = res.getOrThrow()
            _orgHierarchy.value = hierarchy
            val nodes = buildList {
                hierarchy.zones.forEach { add(CachedOrgNodeEntity(it.id, "ZONE", it.code, it.name)) }
                hierarchy.states.forEach { add(CachedOrgNodeEntity(it.id, "STATE", it.code, it.name, parentZoneId = it.zoneId)) }
                hierarchy.cities.forEach { add(CachedOrgNodeEntity(it.id, "CITY", it.code, it.name, parentZoneId = it.zoneId, parentStateId = it.stateId)) }
                hierarchy.centres.forEach {
                    add(
                        CachedOrgNodeEntity(
                            id = it.id,
                            nodeType = "CENTRE",
                            code = it.code,
                            name = it.name,
                            parentZoneId = it.zoneId,
                            parentStateId = it.stateId,
                            parentCityId = it.cityId,
                            externalCode = it.externalCode,
                            districtName = it.districtName,
                            address = it.address,
                            subHub = it.subHub,
                            availableLansCsv = OrgCentre.formatLansCsv(it.availableLans)
                        )
                    )
                }
            }
            if (nodes.isNotEmpty()) {
                database.authorizationDao().clearOrgNodes()
                database.authorizationDao().insertOrgNodes(nodes)
            }
        }
        res
    }

    suspend fun upsertOrgCentre(
        id: String? = null,
        code: String,
        name: String,
        cityId: String? = null,
        districtName: String? = null,
        address: String? = null,
        subHub: String? = null,
        availableLans: List<String> = emptyList(),
        stateName: String? = null
    ): Result<OrgCentre> = withContext(Dispatchers.IO) {
        var token = authRepository.getValidAccessToken().getOrNull()
            ?: return@withContext Result.failure(Exception("Authentication required"))
        var res = supabaseClient.upsertOrgCentre(
            accessToken = token,
            id = id,
            code = code,
            name = name,
            cityId = cityId,
            districtName = districtName,
            address = address,
            subHub = subHub,
            availableLans = availableLans,
            stateName = stateName
        )
        if (res.isFailure) {
            val msg = res.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("401") || msg.contains("jwt", true) || msg.contains("expired", true)) {
                val refreshed = authRepository.forceRefreshAccessToken(token).getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    res = supabaseClient.upsertOrgCentre(
                        accessToken = token,
                        id = id,
                        code = code,
                        name = name,
                        cityId = cityId,
                        districtName = districtName,
                        address = address,
                        subHub = subHub,
                        availableLans = availableLans,
                        stateName = stateName
                    )
                }
            }
        }
        if (res.isSuccess) {
            refreshOrgHierarchy(token)
        }
        res
    }

    suspend fun switchActiveRole(roleCode: String): Result<AuthorizationContext> = withContext(Dispatchers.IO) {
        val tokenRes = authRepository.getValidAccessToken()
        if (tokenRes.isFailure) return@withContext Result.failure(tokenRes.exceptionOrNull()!!)
        val token = tokenRes.getOrThrow()

        val res = supabaseClient.switchActiveRole(token, roleCode)
        if (res.isFailure) return@withContext res
        val newCtx = res.getOrThrow()

        // Step 1..11 of Spec Section 5: invalidate old role/scope cache cleanly before loading new role data
        database.clearRoleScopeCacheForSwitch()
        taskRepository.clearInMemoryCaches()
        userPrefs.clearSyncCursor()

        permissionManager.updateContext(newCtx)
        database.authorizationDao().upsertAuthContext(newCtx.toEntity())
        syncProfileSnapshot(newCtx)
        realtimeManager.refreshSubscriptionsForRoleSwitch(newCtx.activeRole.code, newCtx.scope)

        syncNow(forceFull = true)
        Result.success(newCtx)
    }

    suspend fun updateOperationalLocation(
        roleCode: String? = null,
        stateId: String? = null,
        cityId: String? = null,
        centreId: String? = null,
        centreCode: String? = null,
        lanNumber: String? = null,
        zoneId: String? = null
    ): Result<AuthorizationContext> = withContext(Dispatchers.IO) {
        val tokenRes = authRepository.getValidAccessToken()
        if (tokenRes.isFailure) return@withContext Result.failure(tokenRes.exceptionOrNull()!!)
        val token = tokenRes.getOrThrow()

        val res = supabaseClient.setMyOperationalLocation(
            accessToken = token,
            roleCode = roleCode,
            stateId = stateId,
            cityId = cityId,
            centreId = centreId,
            centreCode = centreCode,
            lanNumber = lanNumber,
            zoneId = zoneId
        )
        if (res.isFailure) return@withContext res
        val newCtx = res.getOrThrow()

        database.clearRoleScopeCacheForSwitch()
        taskRepository.clearInMemoryCaches()
        userPrefs.clearSyncCursor()

        permissionManager.updateContext(newCtx)
        database.authorizationDao().upsertAuthContext(newCtx.toEntity())
        syncProfileSnapshot(newCtx)
        realtimeManager.refreshSubscriptionsForRoleSwitch(newCtx.activeRole.code, newCtx.scope)

        syncNow(forceFull = true)
        Result.success(newCtx)
    }

    suspend fun updateLeaveStatus(isOnLeave: Boolean): Result<AuthorizationContext> = withContext(Dispatchers.IO) {
        val tokenRes = authRepository.getValidAccessToken()
        if (tokenRes.isFailure) return@withContext Result.failure(tokenRes.exceptionOrNull()!!)
        val token = tokenRes.getOrThrow()

        val res = supabaseClient.setMyLeaveStatus(token, isOnLeave)
        if (res.isFailure) return@withContext res
        val newCtx = res.getOrThrow()

        permissionManager.updateContext(newCtx)
        database.authorizationDao().upsertAuthContext(newCtx.toEntity())
        syncProfileSnapshot(newCtx)

        val scheduler = com.example.alarm.TaskAlarmScheduler(context)
        if (isOnLeave) {
            runCatching { scheduler.cancelAllAlarmsForUser(newCtx.userId) }
        } else {
            syncNow(forceFull = false)
        }
        Result.success(newCtx)
    }

    suspend fun markNotificationRead(notificationId: String) = withContext(Dispatchers.IO) {
        database.notificationDao().markAsRead(notificationId)
        val currentDash = _scopedDashboardSummary.value
        if (currentDash.unreadNotifications > 0) {
            _scopedDashboardSummary.value = currentDash.copy(
                unreadNotifications = (currentDash.unreadNotifications - 1).coerceAtLeast(0)
            )
        }
        val token = authRepository.getValidAccessToken().getOrNull() ?: return@withContext
        supabaseClient.markNotificationReadRpc(token, notificationId)
    }

    suspend fun markAllNotificationsRead() = withContext(Dispatchers.IO) {
        val uid = userPrefs.getUserSession().userId ?: return@withContext
        database.notificationDao().markAllAsReadForUser(uid)
        _scopedDashboardSummary.value = _scopedDashboardSummary.value.copy(unreadNotifications = 0)
    }

    suspend fun onLogout() = withContext(Dispatchers.IO) {
        authPaused = false
        realtimeManager.disconnect(clearSession = true)
        pushNotificationManager.clearMemoryKeys()
        permissionManager.reset()
        taskRepository.clearInMemoryCaches()
        _scopedDashboardSummary.value = ScopedDashboardSummary()
        database.clearAllUserScopedTables()
        userPrefs.clearSyncCursor()
        database.syncMetadataDao().updateSyncStatus(SyncStatus.OFFLINE.name, "Signed out")
    }

    private fun nodesToHierarchy(nodes: List<CachedOrgNodeEntity>): OrgHierarchyData {
        return OrgHierarchyData(
            zones = nodes.filter { it.nodeType == "ZONE" }.map { OrgZone(it.id, it.code, it.name) },
            states = nodes.filter { it.nodeType == "STATE" }.map { OrgState(it.id, it.parentZoneId.orEmpty(), it.code, it.name) },
            cities = nodes.filter { it.nodeType == "CITY" }.map { OrgCity(it.id, it.parentStateId.orEmpty(), it.parentZoneId.orEmpty(), it.code, it.name) },
            centres = nodes.filter { it.nodeType == "CENTRE" }.map {
                OrgCentre(
                    id = it.id,
                    externalCode = it.externalCode,
                    cityId = it.parentCityId.orEmpty(),
                    stateId = it.parentStateId.orEmpty(),
                    zoneId = it.parentZoneId.orEmpty(),
                    code = it.code,
                    name = it.name,
                    districtName = it.districtName,
                    address = it.address,
                    subHub = it.subHub,
                    availableLans = OrgCentre.parseLansCsv(it.availableLansCsv)
                )
            }
        )
    }
}

private fun AuthorizationContext.toEntity(): CachedAuthContextEntity = CachedAuthContextEntity(
    userId = userId,
    employeeId = employeeId,
    fullName = fullName,
    isActive = isActive,
    isOnLeave = isOnLeave,
    activeRoleCode = activeRole.code,
    primaryRoleCode = primaryRole.code,
    assignedRolesJson = JSONArray().apply {
        assignedRoles.forEach { r ->
            put(
                JSONObject().apply {
                    put("role_id", r.roleId)
                    put("role_code", r.roleCode)
                    put("role_name", r.roleName)
                    put("hierarchy_level", r.hierarchyLevel)
                    put("default_scope_level", r.defaultScopeLevel)
                    put("is_primary", r.isPrimary)
                }
            )
        }
    }.toString(),
    effectiveScopeLevel = scope.effectiveScopeLevel.code,
    zoneId = scope.zoneId,
    zoneName = scope.zoneName,
    stateId = scope.stateId,
    stateName = scope.stateName,
    cityId = scope.cityId,
    cityName = scope.cityName,
    centreId = scope.centreId,
    centreCode = scope.centreCode,
    roleVersion = roleVersion,
    scopeVersion = scopeVersion,
    lastUpdatedMillis = System.currentTimeMillis()
)

private fun CachedAuthContextEntity.toDomain(): AuthorizationContext {
    val rolesArr = runCatching { JSONArray(assignedRolesJson) }.getOrNull() ?: JSONArray()
    val assigned = List(rolesArr.length()) { idx ->
        val r = rolesArr.getJSONObject(idx)
        AssignedRoleInfo(
            roleId = r.optString("role_id"),
            roleCode = r.optString("role_code"),
            roleName = r.optString("role_name"),
            hierarchyLevel = r.optInt("hierarchy_level", 10),
            defaultScopeLevel = r.optString("default_scope_level", "CENTRE"),
            isPrimary = r.optBoolean("is_primary", false)
        )
    }
    return AuthorizationContext(
        userId = userId,
        employeeId = employeeId,
        fullName = fullName,
        isActive = isActive,
        isOnLeave = isOnLeave,
        activeRole = TaskzRole.fromCode(activeRoleCode),
        primaryRole = TaskzRole.fromCode(primaryRoleCode),
        assignedRoles = assigned,
        scope = OrgScope(
            effectiveScopeLevel = ScopeLevel.fromCode(effectiveScopeLevel),
            zoneId = zoneId,
            zoneName = zoneName,
            stateId = stateId,
            stateName = stateName,
            cityId = cityId,
            cityName = cityName,
            centreId = centreId,
            centreCode = centreCode
        ),
        roleVersion = roleVersion,
        scopeVersion = scopeVersion
    )
}
