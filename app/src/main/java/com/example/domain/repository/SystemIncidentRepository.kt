package com.example.domain.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
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

    suspend fun syncIncidentToFirestore(incident: SystemIncident): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val incidentDoc = mapOf(
                "id" to incident.id,
                "submissionId" to incident.submissionId,
                "centreId" to (incident.centreId ?: ""),
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
                "createdBy" to (incident.createdBy ?: ""),
                "creatorName" to (incident.creatorName ?: ""),
                "syncStatus" to "SYNCED",
                "updatedAt" to FieldValue.serverTimestamp()
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
        if (existing.isEmpty()) {
            val seeded = listOf(
                TaskGroup(
                    id = "centre_141",
                    title = "141 · Yagyavalkya Institute of Technology",
                    code = "141",
                    description = "Sitapura Industrial Area, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                ),
                TaskGroup(
                    id = "centre_142",
                    title = "142 · Arya College of Engineering & IT",
                    code = "142",
                    description = "SP-42, RIICO Industrial Area, Kukas, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                ),
                TaskGroup(
                    id = "centre_143",
                    title = "143 · JECRC Foundation & University",
                    code = "143",
                    description = "Ramchandrapura, Sitapura Extension, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                ),
                TaskGroup(
                    id = "centre_144",
                    title = "144 · Poornima Institute of Engineering & Technology",
                    code = "144",
                    description = "ISI-2, RIICO Institutional Area, Sitapura, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                ),
                TaskGroup(
                    id = "centre_145",
                    title = "145 · Swami Keshvanand Institute of Technology",
                    code = "145",
                    description = "Ramnagaria, Jagatpura, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                ),
                TaskGroup(
                    id = "centre_146",
                    title = "146 · Rajasthan College of Engineering for Women",
                    code = "146",
                    description = "Bhankrota, Ajmer Road, Jaipur, Rajasthan",
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
            )
            taskGroupDao.insertGroups(seeded.map { TaskGroupEntity.fromDomain(it) })
            seeded
        } else {
            existing
        }
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
