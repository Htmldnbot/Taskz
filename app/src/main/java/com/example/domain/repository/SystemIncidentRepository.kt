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
import com.example.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import com.example.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
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

    private val firestore: FirebaseFirestore by lazy {
        val dbId = try {
            context.getString(R.string.firestore_database_id)
        } catch (_: Exception) {
            "(default)"
        }
        try {
            FirebaseFirestore.getInstance(dbId)
        } catch (_: Exception) {
            FirebaseFirestore.getInstance()
        }
    }

    private val repoScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    init {
        // Start background real-time sync with Cloud Firestore
        setupFirestoreSyncAndListener()
    }

    private fun setupFirestoreSyncAndListener() {
        repoScope.launch {
            try {
                fetchAndSyncIncidentsFromFirestore()
                // Attach real-time Firestore snapshot listener
                firestore.collection("system_incidents")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Firestore snapshot listener error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            repoScope.launch {
                                val entities = snapshot.documents.mapNotNull { doc ->
                                    val data = doc.data ?: return@mapNotNull null
                                    mapFirestoreDocToEntity(doc.id, data)
                                }
                                if (entities.isNotEmpty()) {
                                    systemIncidentDao.insertIncidents(entities)
                                    Log.d(TAG, "Synced ${entities.size} incidents from Firestore listener into local DB")
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing Firestore sync: ${e.message}")
            }
        }
    }

    suspend fun fetchAndSyncIncidentsFromFirestore(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("system_incidents")
                .get()
                .await()
            val entities = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                mapFirestoreDocToEntity(doc.id, data)
            }
            if (entities.isNotEmpty()) {
                systemIncidentDao.insertIncidents(entities)
                Log.d(TAG, "Fetched and inserted ${entities.size} incidents from Firestore")
            }
            Result.success(entities.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch incidents from Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    private fun mapFirestoreDocToEntity(id: String, data: Map<String, Any?>): SystemIncidentEntity {
        val submissionId = (data["submissionId"] as? String) ?: "SUB-$id"
        val centreId = data["centreId"] as? String
        val examSessionId = data["examSessionId"] as? String
        val stateName = (data["stateName"] as? String)?.ifBlank { "Rajasthan" } ?: "Rajasthan"
        val districtName = (data["districtName"] as? String)?.ifBlank { "Jaipur" } ?: "Jaipur"
        val centreCode = (data["centreCode"] as? String)?.ifBlank { "141" } ?: "141"
        val centreName = (data["centreName"] as? String)?.ifBlank { "Yagyavalkya Institute of Technology" }
            ?: "Yagyavalkya Institute of Technology"
        val examDate = (data["examDate"] as? String) ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val examName = (data["examName"] as? String) ?: "Staff Selection Commission Examination"
        val shiftName = (data["shiftName"] as? String) ?: "Shift 1"
        val lanName = (data["lanName"] as? String) ?: "LAN 1"
        val macAddress = (data["macAddress"] as? String) ?: "AA:BB:CC:DD:EE:FF"
        val ipAddress = (data["ipAddress"] as? String) ?: "192.168.1.10"
        val lockedSystemNumber = (data["lockedSystemNumber"] as? String) ?: "C001"
        val newlyAllocatedSystemNumber = data["newlyAllocatedSystemNumber"] as? String
        val candidateShifted = (data["candidateShifted"] as? Boolean) ?: (!newlyAllocatedSystemNumber.isNullOrBlank())
        val incidentTime = (data["incidentTime"] as? String) ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val incidentTimestampMillis = (data["incidentTimestampMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val reason = (data["reason"] as? String) ?: "System Lock"
        val itManagerName = (data["itManagerName"] as? String) ?: ""
        val createdBy = (data["createdBy"] as? String) ?: ""
        val creatorName = data["creatorName"] as? String
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedBy = data["updatedBy"] as? String
        val syncStatus = (data["syncStatus"] as? String) ?: "SYNCED"

        return SystemIncidentEntity(
            id = id,
            submissionId = submissionId,
            centreId = centreId,
            examSessionId = examSessionId,
            stateName = stateName,
            districtName = districtName,
            centreCode = centreCode,
            centreName = centreName,
            examDate = examDate,
            examName = examName,
            shiftName = shiftName,
            lanName = lanName,
            macAddress = macAddress,
            ipAddress = ipAddress,
            lockedSystemNumber = lockedSystemNumber,
            newlyAllocatedSystemNumber = newlyAllocatedSystemNumber,
            candidateShifted = candidateShifted,
            incidentTime = incidentTime,
            incidentTimestampMillis = incidentTimestampMillis,
            reason = reason,
            itManagerName = itManagerName,
            createdBy = createdBy,
            creatorName = creatorName,
            createdAt = createdAt,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            syncStatus = syncStatus,
            lastSyncError = null
        )
    }

    suspend fun syncIncidentToFirestore(incident: SystemIncident): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val incidentDoc = mapOf(
                "id" to incident.id,
                "submissionId" to incident.submissionId,
                "centreId" to (incident.centreId ?: ""),
                "examSessionId" to (incident.examSessionId ?: ""),
                "stateName" to incident.stateName,
                "districtName" to incident.districtName,
                "centreCode" to incident.centreCode,
                "centreName" to incident.centreName,
                "lockedSystemNumber" to incident.lockedSystemNumber,
                "newlyAllocatedSystemNumber" to (incident.newlyAllocatedSystemNumber ?: ""),
                "candidateShifted" to incident.candidateShifted,
                "macAddress" to incident.macAddress,
                "ipAddress" to incident.ipAddress,
                "reason" to incident.reason,
                "itManagerName" to incident.itManagerName,
                "examDate" to incident.examDate,
                "examName" to incident.examName,
                "shiftName" to incident.shiftName,
                "lanName" to incident.lanName,
                "incidentTime" to incident.incidentTime,
                "incidentTimestampMillis" to incident.incidentTimestampMillis,
                "createdBy" to (incident.createdBy ?: ""),
                "creatorName" to (incident.creatorName ?: ""),
                "createdAt" to incident.createdAt,
                "updatedAt" to System.currentTimeMillis(),
                "syncStatus" to "SYNCED"
            )
            firestore.collection("system_incidents").document(incident.id)
                .set(incidentDoc, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync error for incident ${incident.id}: ${e.message}")
            Result.failure(e)
        }
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
        val existing = taskGroupDao.getAllGroupsFlow().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        val jaipurSeedList = listOf(
            TaskGroup("centre_141", "141 · Yagyavalkya Institute of Technology", "141", "Sitapura Industrial Area, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_142", "142 · Arya College of Engineering & IT", "142", "SP-42, RIICO Industrial Area, Kukas, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_143", "143 · JECRC Foundation & University", "143", "Ramchandrapura, Sitapura Extension, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_144", "144 · Poornima Institute of Engineering & Technology", "144", "ISI-2, RIICO Institutional Area, Sitapura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_145", "145 · Swami Keshvanand Institute of Technology", "145", "Ramnagaria, Jagatpura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_146", "146 · Rajasthan College of Engineering for Women", "146", "Bhankrota, Ajmer Road, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_147", "147 · Anand International College of Engineering", "147", "Near Kanota, Agra Road, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_148", "148 · Global Institute of Technology", "148", "ITS-1 & 2, IT Park, EPIP, Sitapura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_149", "149 · Apex Institute of Engineering & Technology", "149", "ISI-5, RIICO Institutional Area, Sitapura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_150", "150 · Maharishi Arvind Institute of Engineering", "150", "Sector 7, Madhyam Marg, Mansarovar, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_151", "151 · Jaipur Engineering College & Research Centre", "151", "Tonk Road, Sitapura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_152", "152 · Stani Memorial P.G. College", "152", "Sector 11, Tagor Path, Mansarovar, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_153", "153 · Subodh Public School & PG College", "153", "Near Rambagh Circle / Sanganer, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_154", "154 · Shankara Institute of Technology", "154", "SP-43, RIICO Kukas, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())),
            TaskGroup("centre_155", "155 · Vivekananda Global University", "155", "Sector 36, NRI Road, Jagatpura, Jaipur, Rajasthan", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        )

        if (existing.isEmpty() || existing.size < 10) {
            val missing = jaipurSeedList.filter { seed -> existing.none { it.code == seed.code } }
            if (missing.isNotEmpty()) {
                taskGroupDao.insertGroups(missing.map { TaskGroupEntity.fromDomain(it) })
            }
            taskGroupDao.getAllGroupsFlow().firstOrNull()?.map { it.toDomain() } ?: jaipurSeedList
        } else {
            existing
        }
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

        // Save in local SharedPreferences for fast offline retrieval
        sharedPrefs.edit()
            .putString("daily_checkin_date_$userId", todayStr)
            .putString("daily_centre_code_$userId", centreCode)
            .putString("daily_centre_name_$userId", centreName)
            .putString("daily_role_$userId", role)
            .putString("daily_lan_$userId", lan ?: "")
            .apply()

        // Sync to Cloud Firestore
        try {
            val docData = mapOf(
                "userId" to userId,
                "userName" to userName,
                "employeeId" to employeeId,
                "centreCode" to centreCode,
                "centreName" to centreName,
                "role" to role,
                "lan" to (if (role == "VCO") "" else (lan ?: "")),
                "date" to todayStr,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("daily_checkins")
                .document("${todayStr}_$userId")
                .set(docData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload daily check-in to Firestore: ${e.message}")
        }

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
            return@withContext DailyDutyCheckin(
                id = "${todayStr}_$userId",
                userId = userId,
                userName = "",
                employeeId = "",
                date = todayStr,
                centreCode = code,
                centreName = name,
                role = role,
                lan = lan.takeIf { !it.isNullOrBlank() }
            )
        }

        // Fallback to Firestore
        try {
            val doc = firestore.collection("daily_checkins")
                .document("${todayStr}_$userId")
                .get()
                .await()
            if (doc.exists()) {
                val checkin = DailyDutyCheckin(
                    id = doc.id,
                    userId = doc.getString("userId") ?: userId,
                    userName = doc.getString("userName") ?: "",
                    employeeId = doc.getString("employeeId") ?: "",
                    date = todayStr,
                    centreCode = doc.getString("centreCode") ?: "",
                    centreName = doc.getString("centreName") ?: "",
                    role = doc.getString("role") ?: "IT_MANAGER",
                    lan = doc.getString("lan")?.takeIf { it.isNotBlank() }
                )
                // Cache locally
                sharedPrefs.edit()
                    .putString("daily_checkin_date_$userId", todayStr)
                    .putString("daily_centre_code_$userId", checkin.centreCode)
                    .putString("daily_centre_name_$userId", checkin.centreName)
                    .putString("daily_role_$userId", checkin.role)
                    .putString("daily_lan_$userId", checkin.lan ?: "")
                    .apply()
                return@withContext checkin
            }
        } catch (_: Exception) {}

        null
    }

    suspend fun getTodayCheckinsForCentre(centreCode: String): List<DailyDutyCheckin> = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cleanCode = centreCode.trim()
        val result = mutableListOf<DailyDutyCheckin>()

        try {
            val snapshot = firestore.collection("daily_checkins")
                .whereEqualTo("date", todayStr)
                .whereEqualTo("centreCode", cleanCode)
                .get()
                .await()

            for (doc in snapshot.documents) {
                result.add(
                    DailyDutyCheckin(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "Staff",
                        employeeId = doc.getString("employeeId") ?: "",
                        date = todayStr,
                        centreCode = cleanCode,
                        centreName = doc.getString("centreName") ?: "",
                        role = doc.getString("role") ?: "IT_MANAGER",
                        lan = doc.getString("lan")?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching checkins for centre $centreCode: ${e.message}")
        }

        result
    }

    suspend fun createCentre(code: String, name: String, district: String, state: String): TaskGroup = withContext(Dispatchers.IO) {
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
        rememberSelectedCentre(newCentre.id)
        newCentre
    }

    suspend fun updateCentre(centreId: String, code: String, name: String, district: String, state: String): TaskGroup = withContext(Dispatchers.IO) {
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

            val finalIncident = incident.copy(
                createdBy = userId,
                creatorName = userName,
                updatedAt = System.currentTimeMillis()
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
                    createdAt = System.currentTimeMillis()
                )
            )

            // 3. Attempt direct sync
            val isFirebaseOrOffline = (token?.startsWith("firebase_") == true) || 
                (token?.startsWith("offline_") == true) || 
                userId.startsWith("phone_user_")

            if (isFirebaseOrOffline) {
                // Sync to Firestore when connected, and mark locally synced
                if (networkMonitor.isCurrentlyConnected()) {
                    syncIncidentToFirestore(finalIncident)
                }
                systemIncidentDao.updateSyncStatus(finalIncident.id, "SYNCED", null)
                outboxDao.markEventSynced(outboxRowId, finalIncident.submissionId, System.currentTimeMillis())
                val now = System.currentTimeMillis()
                try {
                    TaskzApplication.instance.database.syncMetadataDao().apply {
                        val remaining = outboxDao.getPendingEventsForUser(userId).size
                        if (remaining == 0) {
                            updateSyncStatus("SYNCED", "Up to date")
                            updateLastSuccessfulSync(now)
                        } else {
                            updateSyncStatus("PENDING", "$remaining pending operations awaiting sync")
                        }
                        updateLastServerFetch(now)
                    }
                } catch (_: Exception) {}
                return@withContext Result.success(finalIncident.copy(syncStatus = "SYNCED"))
            } else {
                if (networkMonitor.isCurrentlyConnected() && !token.isNullOrBlank()) {
                    val remoteRes = supabaseClient.submitSystemIncident(token, finalIncident)
                    if (remoteRes.isSuccess) {
                        systemIncidentDao.updateSyncStatus(finalIncident.id, "SYNCED", null)
                        outboxDao.markEventSynced(outboxRowId, finalIncident.submissionId, System.currentTimeMillis())
                        return@withContext Result.success(finalIncident.copy(syncStatus = "SYNCED"))
                    } else {
                        val ex = remoteRes.exceptionOrNull()
                        Log.w(TAG, "Direct sync failed, keeping as PENDING: ${ex?.message}")
                        systemIncidentDao.updateSyncStatus(finalIncident.id, "PENDING", ex?.message)
                        SyncWorker.enqueueSync(context)
                    }
                } else {
                    SyncWorker.enqueueSync(context)
                }
            }

            Result.success(finalIncident)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save incident", e)
            Result.failure(e)
        }
    }

    suspend fun deleteIncident(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            systemIncidentDao.deleteIncident(id)
            systemIncidentDao.deleteAuditsForIncident(id)
            try {
                firestore.collection("system_incidents")
                    .document(id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete incident from Firestore: ${e.message}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete incident", e)
            Result.failure(e)
        }
    }

    suspend fun syncPendingIncidents(): Int = withContext(Dispatchers.IO) {
        val pending = systemIncidentDao.getPendingIncidents()
        val session = userPrefs.getUserSession()
        val token = com.example.TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull() ?: session.accessToken
        val userId = session.userId ?: getActiveUserId()
        val isFirebaseOrOffline = (token?.startsWith("firebase_") == true) || 
            (token?.startsWith("offline_") == true) || 
            userId.startsWith("phone_user_")

        var syncedCount = 0
        for (item in pending) {
            val domain = item.toDomain()
            if (isFirebaseOrOffline) {
                if (networkMonitor.isCurrentlyConnected()) {
                    syncIncidentToFirestore(domain)
                }
                systemIncidentDao.updateSyncStatus(item.id, "SYNCED", null)
                outboxDao.getPendingEventsForUser(userId).find { it.eventId == item.submissionId || it.taskId == item.id }?.let { ev ->
                    outboxDao.markEventSynced(ev.id, "INCIDENT_SYNCED", System.currentTimeMillis())
                }
                syncedCount++
            } else {
                if (!token.isNullOrBlank() && networkMonitor.isCurrentlyConnected()) {
                    val res = supabaseClient.submitSystemIncident(token, domain)
                    if (res.isSuccess) {
                        systemIncidentDao.updateSyncStatus(item.id, "SYNCED", null)
                        outboxDao.getPendingEventsForUser(userId).find { it.eventId == item.submissionId || it.taskId == item.id }?.let { ev ->
                            outboxDao.markEventSynced(ev.id, "INCIDENT_SYNCED", System.currentTimeMillis())
                        }
                        syncedCount++
                    } else {
                        systemIncidentDao.updateSyncStatus(item.id, "ERROR", res.exceptionOrNull()?.message)
                    }
                }
            }
        }

        // Clean up any lingering outbox events for this user if they are incidents that are now synced
        if (isFirebaseOrOffline) {
            val pendingOutbox = outboxDao.getPendingEventsForUser(userId)
            for (ev in pendingOutbox) {
                if (ev.operationType == "SYSTEM_INCIDENT_SUBMISSION") {
                    outboxDao.markEventSynced(ev.id, "INCIDENT_SYNCED", System.currentTimeMillis())
                    syncedCount++
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
