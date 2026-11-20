package com.example.domain.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.DailyDutyCheckin
import com.example.core.model.SystemIncident
import com.example.core.model.SystemIncidentAudit
import com.example.core.model.TaskGroup
import com.example.core.network.NetworkMonitor
import com.example.data.local.dao.OutboxDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.SystemIncidentDao
import com.example.data.local.dao.TaskGroupDao
import com.example.data.local.entity.OutboxEventEntity
import com.example.data.local.entity.SystemIncidentAuditEntity
import com.example.data.local.entity.SystemIncidentEntity
import com.example.data.local.entity.TaskGroupEntity
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHttpException
import com.example.sync.OutboxSyncCoordinator
import com.example.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SystemIncidentRepository(
    private val systemIncidentDao: SystemIncidentDao,
    private val taskGroupDao: TaskGroupDao,
    private val shiftDao: ShiftDao,
    private val outboxDao: OutboxDao,
    private val supabaseClient: SupabaseClient,
    private val userPrefs: UserPreferencesDataStore,
    private val networkMonitor: NetworkMonitor,
    private val context: Context = TaskzApplication.instance
) {
    companion object {
        private const val TAG = "SystemIncidentRepo"
        private const val PREFS_NAME = "taskz_incident_prefs"
        private const val KEY_LAST_CENTRE_ID = "last_selected_centre_id"
        private const val KEY_LAST_EXAM_NAME = "last_selected_exam_name"
        private const val KEY_LAST_SHIFT_NAME = "last_selected_shift_name"
        private const val KEY_LAST_LAN_NAME = "last_selected_lan_name"
        private const val KEY_LAST_IT_MANAGER = "last_selected_it_manager"
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val repoScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    init {
        repoScope.launch {
            try {
                syncIncidentsFromServer()
            } catch (e: Exception) {
                Log.w(TAG, "Initial Supabase incident sync warning: ${e.message}")
            }
        }
    }

    suspend fun syncIncidentsFromServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!networkMonitor.isCurrentlyConnected()) {
                return@withContext Result.failure(Exception("Offline"))
            }
            var token = TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()
                ?: return@withContext Result.failure(Exception("Authentication required"))
            var res = supabaseClient.fetchSystemIncidents(token)
            if (res.isFailure && isAuthExpiredError(res.exceptionOrNull())) {
                val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken(token).getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    res = supabaseClient.fetchSystemIncidents(token)
                }
            }
            if (res.isFailure) {
                return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed to fetch incidents"))
            }
            val remoteList = res.getOrDefault(emptyList())
            if (remoteList.isNotEmpty()) {
                val pendingIds = systemIncidentDao.getPendingIncidents().map { it.id }.toSet()
                val safeRemote = mutableListOf<SystemIncident>()
                for (remote in remoteList) {
                    if (remote.id in pendingIds) continue
                    val existing = systemIncidentDao.getIncidentById(remote.id)
                    if (existing != null && (existing.syncStatus == "PENDING" || remote.version < existing.version)) {
                        continue
                    }
                    safeRemote.add(remote)
                }
                if (safeRemote.isNotEmpty()) {
                    systemIncidentDao.insertIncidents(safeRemote.map { SystemIncidentEntity.fromDomain(it) })
                }
            }
            Result.success(remoteList.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync incidents from Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun reconcileIncidentsFromJson(incidentsArr: org.json.JSONArray): List<SystemIncident> = withContext(Dispatchers.IO) {
        val pendingIds = systemIncidentDao.getPendingIncidents().map { it.id }.toSet()
        val entities = mutableListOf<SystemIncidentEntity>()
        val domains = mutableListOf<SystemIncident>()
        for (i in 0 until incidentsArr.length()) {
            val o = incidentsArr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank() || id in pendingIds) continue
            val existing = systemIncidentDao.getIncidentById(id)
            val remoteVersion = o.optLong("version", 1L)
            if (existing != null && (existing.syncStatus == "PENDING" || remoteVersion < existing.version)) {
                domains.add(existing.toDomain())
                continue
            }
            val incidentMillis = parseIsoMillisOrNow(o.optString("incident_time"))
            val createdMillis = parseIsoMillisOrNow(o.optString("created_at"))
            val updatedMillis = parseIsoMillisOrNow(o.optString("updated_at"))
            val displayTime = runCatching {
                java.time.Instant.ofEpochMilli(incidentMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
            }.getOrDefault("10:00 AM")

            fun optStr(k: String): String? =
                if (!o.has(k) || o.isNull(k)) null else o.optString(k, "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }

            val domain = SystemIncident(
                id = id,
                submissionId = optStr("submission_id") ?: id,
                centreId = optStr("centre_id"),
                examSessionId = optStr("exam_session_id"),
                stateName = optStr("state_name") ?: "",
                districtName = optStr("district_name") ?: "",
                centreCode = optStr("centre_code") ?: "",
                centreName = optStr("centre_name") ?: "",
                examDate = optStr("exam_date") ?: "",
                examName = optStr("exam_name") ?: "",
                shiftName = optStr("shift_name") ?: "",
                lanName = optStr("lan_name") ?: "",
                macAddress = optStr("mac_address") ?: "",
                ipAddress = optStr("ip_address") ?: "",
                lockedSystemNumber = optStr("locked_system_number") ?: "",
                newlyAllocatedSystemNumber = optStr("newly_allocated_system_number"),
                candidateShifted = o.optBoolean("candidate_shifted", false),
                incidentTime = displayTime,
                incidentTimestampMillis = incidentMillis,
                reason = optStr("reason") ?: "",
                itManagerName = optStr("it_manager_name") ?: "",
                createdBy = optStr("created_by") ?: "",
                createdAt = createdMillis,
                updatedAt = updatedMillis,
                syncStatus = "SYNCED",
                scopeLevel = optStr("scope_level") ?: "CENTRE",
                zoneId = optStr("zone_id"),
                stateId = optStr("state_id"),
                cityId = optStr("city_id"),
                status = optStr("status") ?: "OPEN",
                escalationLevel = optStr("escalation_level") ?: "IT_MANAGER",
                assignedTo = optStr("assigned_to"),
                version = remoteVersion
            )
            domains.add(domain)
            entities.add(SystemIncidentEntity.fromDomain(domain))
        }
        if (entities.isNotEmpty()) {
            systemIncidentDao.insertIncidents(entities)
        }
        domains
    }

    private fun parseIsoMillisOrNow(raw: String?): Long {
        if (raw.isNullOrBlank() || raw.equals("null", true)) return System.currentTimeMillis()
        return runCatching { java.time.Instant.parse(raw.trim()).toEpochMilli() }
            .recoverCatching { java.time.OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }

    val allIncidentsFlow: Flow<List<SystemIncident>> = systemIncidentDao.getAllIncidentsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    fun getIncidentsForUserFlow(userId: String): Flow<List<SystemIncident>> =
        systemIncidentDao.getIncidentsForUserFlow(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    fun getIncidentsForCentreFlow(centreId: String): Flow<List<SystemIncident>> =
        systemIncidentDao.getIncidentsForCentreFlow(centreId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getIncidentById(incidentId: String): SystemIncident? = withContext(Dispatchers.IO) {
        systemIncidentDao.getIncidentById(incidentId)?.toDomain()
    }

    suspend fun getActiveUserId(): String {
        return userPrefs.getUserSession().userId ?: ""
    }

    suspend fun getActiveUserName(): String {
        return userPrefs.getUserSession().employeeId ?: ""
    }

    // ==========================================
    // AUTO-FILL & REMEMBERED STATE
    // ==========================================

    suspend fun getAvailableCentres(): List<TaskGroup> = withContext(Dispatchers.IO) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val rajasthanSeedList = listOf(
            TaskGroup("centre_8451", "8451 · iON Digital Zone iDZ Gegal Industrial Area", "8451", "Ajmer, Rajasthan", nowStr),
            TaskGroup("centre_8665", "8665 · iON Digital Zone iDZ Naino Ka Bas", "8665", "Bikaner, Rajasthan", nowStr),
            TaskGroup("centre_37201", "37201 · Dr Tanveer Malawat Nursing College Bikaner", "37201", "Bikaner, Rajasthan", nowStr),
            TaskGroup("centre_8571", "8571 · Sri Balaji College Of Engineering And Technology", "8571", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_41682", "41682 · iON Digital Zone iDZ Sitapura 1", "41682", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_41683", "41683 · iON Digital Zone iDZ Sitapura 2", "41683", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_141", "141 · Yagyavalkya Institute of Technology", "141", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_1118", "1118 · Jaipur Engineering College", "1118", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_24264", "24264 · Akashdeep Girls PG College", "24264", "Jaipur, Rajasthan", nowStr),
            TaskGroup("centre_8450", "8450 · iON Digital Zone iDZ Laxman Nagar", "8450", "Jodhpur, Rajasthan", nowStr),
            TaskGroup("centre_33978", "33978 · Shree Vinayak Institute", "33978", "Jodhpur, Rajasthan", nowStr),
            TaskGroup("centre_10624", "10624 · Shiv Jyoti International School", "10624", "Kota, Rajasthan", nowStr),
            TaskGroup("centre_42599", "42599 · iON Digital Zone iDZ Modi Kota", "42599", "Kota, Rajasthan", nowStr),
            TaskGroup("centre_1275", "1275 · Bhartiya Institute of Engineering and Technology", "1275", "Sikar, Rajasthan", nowStr),
            TaskGroup("centre_7850", "7850 · iON Digital Zone iDZ Chandpura", "7850", "Sikar, Rajasthan", nowStr),
            TaskGroup("centre_24271", "24271 · Khwaja Gharib Nawaz College of Nursing", "24271", "Sikar, Rajasthan", nowStr),
            TaskGroup("centre_8449", "8449 · iON Digital Zone iDZ Dholi Magri", "8449", "Udaipur, Rajasthan", nowStr)
        )
        val obsoletePlaceholderIds = setOf(
            "centre_142", "centre_143", "centre_144", "centre_145", "centre_146", "centre_147",
            "centre_148", "centre_149", "centre_150", "centre_151", "centre_152", "centre_153",
            "centre_154", "centre_155"
        )
        val existingAll = taskGroupDao.getAllGroupsFlow().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        val stale = existingAll.filter { it.id in obsoletePlaceholderIds }
        stale.forEach { runCatching { taskGroupDao.deleteGroup(it.id) } }

        val customCentres = existingAll.filter { it.id.startsWith("centre_") && it.id !in obsoletePlaceholderIds }
        val customCodes = customCentres.mapNotNull { it.code?.uppercase(Locale.ROOT) }.toSet()
        customCentres + rajasthanSeedList.filter { (it.code?.uppercase(Locale.ROOT) ?: "") !in customCodes }
    }

    // ==========================================
    // DAILY EXAMINATION DUTY CHECK-IN
    // ==========================================

    suspend fun saveDailyDutyCheckin(
        userId: String,
        userName: String,
        employeeId: String,
        centreCode: String,
        centreName: String,
        role: String,
        lan: String?
    ): DailyDutyCheckin = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val checkin = DailyDutyCheckin(
            id = "${todayStr}_$userId",
            userId = userId,
            userName = userName,
            employeeId = employeeId,
            date = todayStr,
            centreCode = centreCode,
            centreName = centreName,
            role = role,
            lan = if (role == "VCO") null else lan,
            timestamp = System.currentTimeMillis()
        )

        // Save in SharedPreferences for local & offline lookup
        sharedPrefs.edit()
            .putString("daily_checkin_date_$userId", todayStr)
            .putString("daily_centre_code_$userId", centreCode)
            .putString("daily_centre_name_$userId", centreName)
            .putString("daily_role_$userId", role)
            .putString("daily_lan_$userId", lan ?: "")
            .putString("daily_user_name_$userId", userName)
            .putString("daily_emp_id_$userId", employeeId)
            .apply()

        val trackedUsers = sharedPrefs.getStringSet("daily_checkin_users_$todayStr", emptySet())?.toMutableSet() ?: mutableSetOf()
        trackedUsers.add(userId)
        sharedPrefs.edit().putStringSet("daily_checkin_users_$todayStr", trackedUsers).apply()

        checkin
    }

    suspend fun getTodayDutyCheckin(userId: String): DailyDutyCheckin? = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = sharedPrefs.getString("daily_checkin_date_$userId", null)
        if (savedDate == todayStr) {
            val code = sharedPrefs.getString("daily_centre_code_$userId", "") ?: ""
            val name = sharedPrefs.getString("daily_centre_name_$userId", "") ?: ""
            val role = sharedPrefs.getString("daily_role_$userId", "IT_MANAGER") ?: "IT_MANAGER"
            val lan = sharedPrefs.getString("daily_lan_$userId", null)
            val userName = sharedPrefs.getString("daily_user_name_$userId", "") ?: ""
            val empId = sharedPrefs.getString("daily_emp_id_$userId", "") ?: ""
            return@withContext DailyDutyCheckin(
                id = "${todayStr}_$userId",
                userId = userId,
                userName = userName,
                employeeId = empId,
                date = todayStr,
                centreCode = code,
                centreName = name,
                role = role,
                lan = lan.takeIf { !it.isNullOrBlank() }
            )
        }
        null
    }

    suspend fun getTodayCheckinsForCentre(centreCode: String): List<DailyDutyCheckin> = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cleanCode = centreCode.trim()
        val trackedUsers = sharedPrefs.getStringSet("daily_checkin_users_$todayStr", emptySet()) ?: emptySet()
        trackedUsers.mapNotNull { uid ->
            val checkin = getTodayDutyCheckin(uid)
            if (checkin != null && checkin.centreCode.equals(cleanCode, ignoreCase = true)) checkin else null
        }
    }

    fun rememberCentreLans(centreCode: String, lans: List<String>) {
        val normalized = lans.mapNotNull { com.example.core.model.OrgCentre.normalizeLanLabel(it) }.distinct()
        if (centreCode.isNotBlank() && normalized.isNotEmpty()) {
            sharedPrefs.edit().putString("centre_lans_${centreCode.trim()}", com.example.core.model.OrgCentre.formatLansCsv(normalized)).apply()
        }
    }

    fun getRememberedCentreLans(centreCode: String): List<String> {
        val csv = sharedPrefs.getString("centre_lans_${centreCode.trim()}", null)
        return com.example.core.model.OrgCentre.parseLansCsv(csv)
    }

    suspend fun createCentre(
        code: String,
        name: String,
        district: String,
        state: String,
        availableLans: List<String> = emptyList()
    ): TaskGroup = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().filter { it.isDigit() || it.isLetter() || it == '-' }
        val cleanName = name.trim().removePrefix("$trimmedCode ·").removePrefix("$trimmedCode -").trim()
        val title = if (trimmedCode.isNotBlank()) "$trimmedCode · $cleanName" else cleanName
        val location = "${district.trim().ifBlank { "Jaipur" }}, ${state.trim().ifBlank { "Rajasthan" }}"
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val newCentre = TaskGroup(
            id = "centre_${UUID.randomUUID().toString().take(8)}",
            title = title,
            code = trimmedCode.ifBlank { null },
            description = location,
            createdAt = nowStr,
            updatedAt = nowStr
        )
        taskGroupDao.insertGroup(TaskGroupEntity.fromDomain(newCentre))
        if (trimmedCode.isNotBlank() && availableLans.isNotEmpty()) {
            rememberCentreLans(trimmedCode, availableLans)
        }
        rememberSelectedCentre(newCentre.id)
        newCentre
    }

    suspend fun updateCentre(
        centreId: String,
        code: String,
        name: String,
        district: String,
        state: String,
        availableLans: List<String> = emptyList()
    ): TaskGroup = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().filter { it.isDigit() || it.isLetter() || it == '-' }
        val cleanName = name.trim().removePrefix("$trimmedCode ·").removePrefix("$trimmedCode -").trim()
        val title = if (trimmedCode.isNotBlank()) "$trimmedCode · $cleanName" else cleanName
        val location = "${district.trim().ifBlank { "Jaipur" }}, ${state.trim().ifBlank { "Rajasthan" }}"
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val updated = TaskGroup(
            id = centreId,
            title = title,
            code = trimmedCode.ifBlank { null },
            description = location,
            updatedAt = nowStr
        )
        taskGroupDao.updateGroup(TaskGroupEntity.fromDomain(updated))
        if (trimmedCode.isNotBlank() && availableLans.isNotEmpty()) {
            rememberCentreLans(trimmedCode, availableLans)
        }
        rememberSelectedCentre(updated.id)
        updated
    }

    suspend fun resolveDefaultCentre(): TaskGroup? = withContext(Dispatchers.IO) {
        val savedId = sharedPrefs.getString(KEY_LAST_CENTRE_ID, null)
        val allCentres = getAvailableCentres()
        if (!savedId.isNullOrBlank()) {
            val found = allCentres.firstOrNull { it.id == savedId }
            if (found != null) return@withContext found
        }
        allCentres.firstOrNull()
    }

    fun rememberSelectedCentre(centreId: String) {
        sharedPrefs.edit().putString(KEY_LAST_CENTRE_ID, centreId).apply()
    }

    suspend fun resolveDefaultItManager(userId: String, centreId: String): String = withContext(Dispatchers.IO) {
        // Priority 1: Latest saved incident by same user at same centre
        val latestFromIncident = systemIncidentDao.getLatestItManagerName(userId, centreId)
        if (!latestFromIncident.isNullOrBlank()) {
            return@withContext latestFromIncident
        }
        // Priority 2: Last remembered in prefs for this user/centre
        val remembered = sharedPrefs.getString("${KEY_LAST_IT_MANAGER}_$centreId", null)
        if (!remembered.isNullOrBlank()) {
            return@withContext remembered
        }
        ""
    }

    suspend fun resolveDefaultLan(userId: String, centreId: String): String = withContext(Dispatchers.IO) {
        val latest = systemIncidentDao.getLatestLanName(userId, centreId)
        if (!latest.isNullOrBlank()) return@withContext latest
        val remembered = sharedPrefs.getString("${KEY_LAST_LAN_NAME}_$centreId", null)
        if (!remembered.isNullOrBlank()) return@withContext remembered
        "LAN 1"
    }

    suspend fun resolveDefaultExamAndShift(centreId: String): Pair<String, String> = withContext(Dispatchers.IO) {
        // Check if there is an active shift configured for this centre/group
        val shifts = shiftDao.getShiftsForGroupFlow(centreId).firstOrNull()
        val activeShift = shifts?.firstOrNull { it.status == "active" || it.status == "scheduled" }
        val shiftName = activeShift?.name ?: sharedPrefs.getString(KEY_LAST_SHIFT_NAME, "Shift 1") ?: "Shift 1"
        val examName = sharedPrefs.getString(KEY_LAST_EXAM_NAME, "Staff Selection Commission Examination")
            ?: "Staff Selection Commission Examination"
        Pair(examName, shiftName)
    }

    fun rememberExamSession(examName: String, shiftName: String, lanName: String, itManagerName: String, centreId: String) {
        sharedPrefs.edit()
            .putString(KEY_LAST_EXAM_NAME, examName)
            .putString(KEY_LAST_SHIFT_NAME, shiftName)
            .putString("${KEY_LAST_LAN_NAME}_$centreId", lanName)
            .putString("${KEY_LAST_IT_MANAGER}_$centreId", itManagerName)
            .apply()
    }

    // ==========================================
    // SAVE INCIDENT (OFFLINE-FIRST)
    // ==========================================

    suspend fun saveIncident(incident: SystemIncident): Result<SystemIncident> = withContext(Dispatchers.IO) {
        try {
            // Validation
            if (!SystemIncident.isValidSystemNumber(incident.lockedSystemNumber)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Locked System Number must follow CXXX format (e.g. C005, C105).")
                )
            }

            if (incident.candidateShifted) {
                val rep = incident.newlyAllocatedSystemNumber ?: ""
                if (!SystemIncident.isValidSystemNumber(rep)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Newly Allocated System Number is required when Candidate Shifted is Yes (e.g. C159).")
                    )
                }
                if (incident.lockedSystemNumber.equals(rep, ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Locked System and Newly Allocated System cannot be identical.")
                    )
                }
            }

            if (!SystemIncident.isValidMacAddress(incident.macAddress)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Please enter a valid 48-bit MAC Address (e.g. AA:BB:CC:DD:EE:FF).")
                )
            }

            if (!SystemIncident.isValidIpv4(incident.ipAddress)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Please enter a valid IPv4 address (e.g. 192.168.1.10).")
                )
            }

            if (incident.reason.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Please specify the reason for system failure."))
            }

            if (incident.itManagerName.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("IT Manager Name cannot be empty."))
            }

            val session = userPrefs.getUserSession()
            val token = com.example.TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull() ?: session.accessToken
            val userId = session.userId ?: getActiveUserId()
            val userName = session.employeeId ?: ""

            val ctx = runCatching { TaskzApplication.instance.permissionManager.getCurrentContext() }.getOrNull()
            val validCentreUuid = incident.centreId?.takeIf { com.example.core.network.SupabaseConfig.isValidUuid(it) }
                ?: ctx?.scope?.centreId
            val finalIncident = incident.copy(
                createdBy = userId,
                creatorName = userName,
                updatedAt = System.currentTimeMillis(),
                zoneId = incident.zoneId ?: ctx?.scope?.zoneId,
                stateId = incident.stateId ?: ctx?.scope?.stateId,
                cityId = incident.cityId ?: ctx?.scope?.cityId,
                centreId = validCentreUuid
            )

            // 1. Save locally in Room first (Durable Offline-First)
            systemIncidentDao.insertIncident(SystemIncidentEntity.fromDomain(finalIncident))

            // Record audit log
            systemIncidentDao.insertAudit(
                SystemIncidentAuditEntity(
                    id = UUID.randomUUID().toString(),
                    incidentId = finalIncident.id,
                    changedBy = userId,
                    changedByName = userName,
                    changedAt = System.currentTimeMillis(),
                    action = "INSERT",
                    previousValues = null,
                    newValues = "Locked: ${finalIncident.lockedSystemNumber}, Replacement: ${finalIncident.displayReplacementSystem}, Reason: ${finalIncident.reason}"
                )
            )

            // Remember user's session context for faster next entry
            if (!finalIncident.centreId.isNullOrBlank()) {
                rememberExamSession(
                    examName = finalIncident.examName,
                    shiftName = finalIncident.shiftName,
                    lanName = finalIncident.lanName,
                    itManagerName = finalIncident.itManagerName,
                    centreId = finalIncident.centreId
                )
            }

            // 2. Queue in Outbox for background persistence & sync
            val payloadJson = JSONObject().apply {
                put("id", finalIncident.id)
                put("submissionId", finalIncident.submissionId)
                put("centreId", finalIncident.centreId)
                put("zoneId", finalIncident.zoneId)
                put("stateId", finalIncident.stateId)
                put("cityId", finalIncident.cityId)
                put("lockedSystemNumber", finalIncident.lockedSystemNumber)
                put("newlyAllocatedSystemNumber", finalIncident.newlyAllocatedSystemNumber)
                put("candidateShifted", finalIncident.candidateShifted)
                put("macAddress", finalIncident.macAddress)
                put("ipAddress", finalIncident.ipAddress)
                put("reason", finalIncident.reason)
                put("itManagerName", finalIncident.itManagerName)
                put("examDate", finalIncident.examDate)
                put("examName", finalIncident.examName)
                put("shiftName", finalIncident.shiftName)
                put("lanName", finalIncident.lanName)
            }

            val outboxRowId = outboxDao.enqueueEvent(
                OutboxEventEntity(
                    eventId = finalIncident.submissionId,
                    userId = userId,
                    taskId = finalIncident.id,
                    operationType = "SYSTEM_INCIDENT_SUBMISSION",
                    payloadJson = payloadJson.toString(),
                    syncStatus = "PENDING",
                    createdAt = System.currentTimeMillis(),
                    activeRole = ctx?.activeRole?.code ?: "IT_MANAGER",
                    scopeLevel = finalIncident.scopeLevel,
                    scopeId = finalIncident.centreId
                )
            )

            // 3. Attempt direct sync to Supabase
            if (networkMonitor.isCurrentlyConnected() && !token.isNullOrBlank() && !token.startsWith("offline_")) {
                val remoteRes = supabaseClient.submitSystemIncident(token, finalIncident)
                if (remoteRes.isSuccess) {
                    systemIncidentDao.updateSyncStatus(finalIncident.id, "SYNCED", null)
                    outboxDao.markEventSynced(outboxRowId, finalIncident.submissionId, System.currentTimeMillis())
                    return@withContext Result.success(finalIncident.copy(syncStatus = "SYNCED"))
                } else {
                    val ex = remoteRes.exceptionOrNull()
                    Log.w(TAG, "Direct incident sync failed, keeping as PENDING: ${ex?.message}")
                    systemIncidentDao.updateSyncStatus(finalIncident.id, "PENDING", ex?.message)
                    SyncWorker.enqueueSync(context)
                }
            } else {
                SyncWorker.enqueueSync(context)
            }

            Result.success(finalIncident)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save incident", e)
            Result.failure(e)
        }
    }

    suspend fun escalateIncident(
        incidentId: String,
        targetLevel: String,
        reason: String?
    ): Result<SystemIncident> = withContext(Dispatchers.IO) {
        try {
            val existing = systemIncidentDao.getIncidentById(incidentId)?.toDomain()
                ?: return@withContext Result.failure(NoSuchElementException("Incident not found"))
            val cleanLevel = targetLevel.trim().uppercase()
            val session = userPrefs.getUserSession()
            val userId = session.userId ?: getActiveUserId()
            val ctx = runCatching { TaskzApplication.instance.permissionManager.getCurrentContext() }.getOrNull()
            val clientOpId = UUID.randomUUID().toString()

            val updatedLocal = existing.copy(
                status = "ESCALATED",
                escalationLevel = cleanLevel,
                updatedAt = System.currentTimeMillis(),
                updatedBy = userId,
                syncStatus = "PENDING"
            )
            systemIncidentDao.insertIncident(SystemIncidentEntity.fromDomain(updatedLocal))
            systemIncidentDao.insertAudit(
                SystemIncidentAuditEntity(
                    id = UUID.randomUUID().toString(),
                    incidentId = incidentId,
                    changedBy = userId,
                    changedByName = ctx?.fullName ?: getActiveUserName(),
                    changedAt = System.currentTimeMillis(),
                    action = "ESCALATED",
                    previousValues = existing.escalationLevel,
                    newValues = "Escalated to $cleanLevel: ${reason.orEmpty()}"
                )
            )

            val payload = JSONObject().apply {
                put("incidentId", incidentId)
                put("targetLevel", cleanLevel)
                if (!reason.isNullOrBlank()) put("reason", reason.trim())
            }
            val outboxRowId = outboxDao.enqueueEvent(
                OutboxEventEntity(
                    eventId = clientOpId,
                    userId = userId,
                    taskId = incidentId,
                    operationType = "INCIDENT_ESCALATE",
                    payloadJson = payload.toString(),
                    syncStatus = "PENDING",
                    createdAt = System.currentTimeMillis(),
                    activeRole = ctx?.activeRole?.code ?: "IT_MANAGER",
                    scopeLevel = existing.scopeLevel,
                    scopeId = existing.centreId
                )
            )

            var token = TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()
            if (networkMonitor.isCurrentlyConnected() && !token.isNullOrBlank()) {
                var res = supabaseClient.escalateSystemIncident(token, incidentId, cleanLevel, reason, clientOpId)
                if (res.isFailure && isAuthExpiredError(res.exceptionOrNull())) {
                    val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken(token).getOrNull()
                    if (!refreshed.isNullOrBlank()) {
                        token = refreshed
                        res = supabaseClient.escalateSystemIncident(token, incidentId, cleanLevel, reason, clientOpId)
                    }
                }
                if (res.isSuccess) {
                    val synced = updatedLocal.copy(syncStatus = "SYNCED")
                    systemIncidentDao.insertIncident(SystemIncidentEntity.fromDomain(synced))
                    outboxDao.markEventSynced(outboxRowId, "INCIDENT_ESCALATED", System.currentTimeMillis())
                    return@withContext Result.success(synced)
                } else {
                    val ex = res.exceptionOrNull()
                    val errMsg = ex?.message.orEmpty()
                    if (isPermanentRejection(ex, errMsg)) {
                        // Roll back local optimistic state and mark outbox permanent failure
                        systemIncidentDao.insertIncident(SystemIncidentEntity.fromDomain(existing))
                        outboxDao.markEventFailed(
                            outboxRowId,
                            errMsg.ifBlank { "Escalation rejected by server" },
                            "FAILED_PERMANENT",
                            System.currentTimeMillis()
                        )
                        return@withContext Result.failure(ex ?: IllegalStateException(errMsg.ifBlank { "Escalation rejected" }))
                    }
                }
            }
            SyncWorker.enqueueSync(context)
            Result.success(updatedLocal)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to escalate incident", e)
            Result.failure(e)
        }
    }

    suspend fun deleteIncident(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            systemIncidentDao.deleteIncident(id)
            systemIncidentDao.deleteAuditsForIncident(id)
            val token = TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()
            if (!token.isNullOrBlank() && !token.startsWith("offline_") && networkMonitor.isCurrentlyConnected()) {
                val res = supabaseClient.deleteSystemIncident(token, id)
                if (res.isFailure) {
                    Log.w(TAG, "Remote delete incident warning: ${res.exceptionOrNull()?.message}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete incident", e)
            Result.failure(e)
        }
    }

    suspend fun syncPendingIncidents(): Int = withContext(Dispatchers.IO) {
        val session = userPrefs.getUserSession()
        val userId = session.userId ?: getActiveUserId()
        var token = TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()

        val pending = OutboxSyncCoordinator.mutex.withLock {
            systemIncidentDao.getPendingIncidents()
        }

        var syncedCount = 0
        val initialToken = token
        if (!initialToken.isNullOrBlank() && !initialToken.startsWith("offline_") && networkMonitor.isCurrentlyConnected()) {
            var activeToken: String = initialToken
            for (item in pending) {
                val domain = item.toDomain()
                var res = supabaseClient.submitSystemIncident(activeToken, domain)
                if (res.isFailure && isAuthExpiredError(res.exceptionOrNull())) {
                    val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken(activeToken).getOrNull()
                    if (!refreshed.isNullOrBlank()) {
                        activeToken = refreshed
                        res = supabaseClient.submitSystemIncident(activeToken, domain)
                    }
                }
                if (res.isSuccess) {
                    systemIncidentDao.updateSyncStatus(item.id, "SYNCED", null)
                    OutboxSyncCoordinator.mutex.withLock {
                        outboxDao.getPendingEventsForUser(userId)
                            .find { it.eventId == item.submissionId || it.taskId == item.id }
                            ?.let { ev ->
                                outboxDao.markEventSynced(ev.id, "INCIDENT_SYNCED", System.currentTimeMillis())
                            }
                    }
                    syncedCount++
                } else {
                    val ex = res.exceptionOrNull()
                    val errMsg = ex?.message.orEmpty()
                    if (isPermanentRejection(ex, errMsg)) {
                        systemIncidentDao.updateSyncStatus(item.id, "ERROR", errMsg)
                        OutboxSyncCoordinator.mutex.withLock {
                            outboxDao.getPendingEventsForUser(userId)
                                .find { it.eventId == item.submissionId || it.taskId == item.id }
                                ?.let { ev ->
                                    outboxDao.markEventFailed(ev.id, errMsg, "FAILED_PERMANENT", System.currentTimeMillis())
                                }
                        }
                    } else {
                        // Keep PENDING so background SyncWorker retries transient network/5xx errors
                        systemIncidentDao.updateSyncStatus(item.id, "PENDING", errMsg)
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        try {
            val remaining = outboxDao.getPendingEventsForUser(userId).size
            TaskzApplication.instance.database.syncMetadataDao().apply {
                if (remaining == 0) {
                    updateSyncStatus("SYNCED", "Up to date")
                    updateLastSuccessfulSync(now)
                } else {
                    updateSyncStatus("PENDING", "$remaining pending operations awaiting sync")
                }
                updateLastServerFetch(now)
            }
        } catch (_: Exception) {}

        return@withContext syncedCount
    }

    private fun isAuthExpiredError(err: Throwable?): Boolean {
        if (err is SupabaseHttpException && err.statusCode == 401) return true
        val msg = err?.message.orEmpty()
        return msg.contains("JWT", ignoreCase = true) ||
            msg.contains("401", ignoreCase = true) ||
            msg.contains("Unauthorized", ignoreCase = true)
    }

    private fun isPermanentRejection(err: Throwable?, msg: String): Boolean {
        if (err is SupabaseHttpException && err.statusCode in setOf(400, 403, 404, 422)) {
            return true
        }
        return msg.contains("forbidden", ignoreCase = true) ||
            msg.contains("not authorized", ignoreCase = true) ||
            msg.contains("unauthorized", ignoreCase = true) ||
            msg.contains("sequential escalation", ignoreCase = true) ||
            msg.contains("invalid escalation", ignoreCase = true) ||
            msg.contains("already resolved", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("invalid input syntax", ignoreCase = true)
    }

    // ==========================================
    // EXCEL / CSV REPORT EXPORT
    // ==========================================

    /**
     * Generates a clean Excel-compatible CSV report with UTF-8 BOM,
     * header freeze friendliness, readable columns, CXXX textual preserving, and NA for unshifted systems.
     */
    suspend fun exportIncidentsToCsvFile(incidents: List<SystemIncident>): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "Taskz_System_Incidents_$timeStamp.csv")

            FileOutputStream(file).use { fos ->
                // Write UTF-8 BOM so Microsoft Excel automatically recognizes UTF-8 formatting and text columns
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // 18 Standard Columns as specified
                    val headers = listOf(
                        "S.No.",
                        "State",
                        "District",
                        "Centre Code",
                        "Centre Name",
                        "Exam Date",
                        "Exam Name",
                        "Shift",
                        "LAN",
                        "MAC Address",
                        "IP Address",
                        "Locked System Number",
                        "Newly Allocated System Number",
                        "Lock Time",
                        "Reason",
                        "IT Manager",
                        "Entered By",
                        "Created At"
                    )
                    writer.write(headers.joinToString(",") { escapeCsv(it) })
                    writer.write("\r\n")

                    incidents.forEachIndexed { index, item ->
                        val createdAtFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(item.createdAt))
                        val row = listOf(
                            (index + 1).toString(),
                            item.stateName,
                            item.districtName,
                            item.centreCode,
                            item.centreName,
                            item.examDate,
                            item.examName,
                            item.shiftName,
                            item.lanName,
                            item.macAddress,
                            item.ipAddress,
                            item.lockedSystemNumber,
                            item.displayReplacementSystem,
                            item.incidentTime,
                            item.reason,
                            item.itManagerName,
                            item.creatorName ?: item.createdBy,
                            createdAtFormatted
                        )
                        writer.write(row.joinToString(",") { escapeCsv(it) })
                        writer.write("\r\n")
                    }
                    writer.flush()
                }
            }

            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting CSV file", e)
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String?): String {
        if (value == null) return "\"\""
        var clean = value.replace("\"", "\"\"")
        // If value starts with '=' or '+' or '-' or '@', prepend single quote to prevent Excel CSV formula injection
        if (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@")) {
            clean = "'$clean"
        }
        return "\"$clean\""
    }

    fun createShareIntentForFile(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Taskz System Incident Register Report")
            putExtra(Intent.EXTRA_TEXT, "Attached is the examination system incident report generated from Taskz.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun exportIncidentsToCsvAndShare(incidents: List<SystemIncident>) = withContext(Dispatchers.Main) {
        val fileRes = exportIncidentsToCsvFile(incidents)
        fileRes.onSuccess { file ->
            val intent = createShareIntentForFile(file).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share Incident Register Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    suspend fun getAuditsForIncident(incidentId: String): List<SystemIncidentAudit> = withContext(Dispatchers.IO) {
        systemIncidentDao.getAuditsForIncident(incidentId).map { it.toDomain() }
    }
}
