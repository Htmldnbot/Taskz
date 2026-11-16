package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncStatus
import com.example.data.local.TaskzDatabase
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TaskzSyncWorker"
        const val WORK_NAME = "taskz_offline_sync_work"
        const val EXPEDITED_WORK_NAME = "taskz_expedited_sync_work"
        const val KEY_FORCE_FULL = "force_full"

        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                syncRequest
            )
        }

        fun enqueueExpeditedSync(context: Context, forceFull: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(KEY_FORCE_FULL to forceFull))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                EXPEDITED_WORK_NAME,
                if (forceFull) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "taskz_periodic_sync_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = TaskzDatabase.getInstance(applicationContext)
        val userPrefs = UserPreferencesDataStore(applicationContext)
        val supabaseClient = SupabaseClient()
        val outboxDao = db.outboxDao()

        val session = userPrefs.getUserSession()
        val activeUserId = session.userId

        if (activeUserId.isNullOrBlank() || !session.isAuthenticated) {
            Log.w(TAG, "No active user session found, skipping sync.")
            return@withContext Result.success()
        }

        val authRepo = TaskzApplication.instance.authRepository

        // 1. Validate or refresh token before processing pending operations
        val tokenRes = authRepo.getValidAccessToken()
        if (tokenRes.isFailure) {
            val err = tokenRes.exceptionOrNull()!!
            if (authRepo.isNetworkFailure(err)) {
                Log.w(TAG, "Network unavailable for sync; will retry with backoff")
                return@withContext Result.retry()
            } else {
                // Authentication failure: token refresh failed or revoked.
                // CRITICAL: Pause synchronization without deleting any pending operations!
                Log.e(TAG, "Authentication failed before processing outbox events: ${err.message}. Pausing synchronization.")
                outboxDao.resetSendingToPendingForUser(activeUserId)
                db.syncMetadataDao().updateSyncStatus(
                    SyncStatus.ERROR.name,
                    "Authentication required: Please sign in to resume synchronization"
                )
                return@withContext Result.failure()
            }
        }

        var token = tokenRes.getOrThrow()

        // Clean up any synthetic pre-migration legacy outbox entries and claim strictly active user's queue atomically
        val pendingEvents = OutboxSyncCoordinator.mutex.withLock {
            outboxDao.deleteLegacyPreMigrationEvents(activeUserId)
            outboxDao.claimEventsForSync(activeUserId)
        }
        val forceFull = inputData.getBoolean(KEY_FORCE_FULL, false)
        if (pendingEvents.isEmpty()) {
            runCatching { TaskzApplication.instance.taskzSyncManager.syncNow(forceFull = forceFull) }
            val remaining = outboxDao.getPendingEventsForUser(activeUserId).size
            if (remaining == 0) {
                db.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
            }
            return@withContext Result.success()
        }

        Log.d(TAG, "Processing ${pendingEvents.size} claimed outbox events for user $activeUserId")
        var hasRetryableFailure = false
        var authPaused = false

        for (event in pendingEvents) {
            try {
                when (event.operationType) {
                    "TASK_CREATION" -> {
                        val payload = sanitizeTaskPayload(JSONObject(event.payloadJson))
                        var res = supabaseClient.createTaskAtomic(token, payload)
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.createTaskAtomic(token, payload)
                            }
                        }

                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val isDuplicate = serverObj.optBoolean("is_duplicate", false)
                            val serverTaskId = serverObj.optString("task_id", event.taskId)
                            if (isDuplicate) {
                                Log.i(TAG, "Duplicate task creation detected via client_operation_id; reconciled with server task $serverTaskId")
                            }
                            outboxDao.markEventSynced(event.id, serverTaskId, System.currentTimeMillis())
                            if (!event.taskId.isNullOrBlank()) {
                                db.taskDao().updateTaskStatus(event.taskId, "open")
                            }
                        } else {
                            val ex = res.exceptionOrNull()
                            val err = ex?.message ?: "Task creation sync error"
                            val isPermanent = isPermanentError(ex, err)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "TASK_DELETION" -> {
                        val taskId = event.taskId ?: JSONObject(event.payloadJson).optString("task_id")
                        val res = supabaseClient.deleteTask(token, taskId)
                        if (res.isSuccess) {
                            outboxDao.markEventSynced(event.id, "DELETED", System.currentTimeMillis())
                        } else {
                            val ex = res.exceptionOrNull()
                            val err = ex?.message ?: "Task deletion sync error"
                            val isPermanent = isPermanentError(ex, err)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "GROUP_CREATION" -> {
                        val payload = JSONObject(event.payloadJson)
                        var res = supabaseClient.createGroup(token, payload)
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.createGroup(token, payload)
                            }
                        }

                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val serverGroupId = serverObj.optString("id", event.taskId)
                            outboxDao.markEventSynced(event.id, serverGroupId, System.currentTimeMillis())
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Group creation sync error"
                            val isPermanent = err.contains("duplicate", ignoreCase = true) ||
                                    err.contains("unauthorized", ignoreCase = true)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "SHIFT_CREATION" -> {
                        val payload = JSONObject(event.payloadJson)
                        var res = supabaseClient.createShift(token, payload)
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.createShift(token, payload)
                            }
                        }

                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val serverShiftId = serverObj.optString("id", event.taskId)
                            outboxDao.markEventSynced(event.id, serverShiftId, System.currentTimeMillis())
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Shift creation sync error"
                            val isPermanent = err.contains("duplicate", ignoreCase = true) ||
                                    err.contains("unauthorized", ignoreCase = true)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "TASK_RESPONSE", "PROGRESS_UPDATE", "LAN_CONFIRMATION", "COMPLETION_REQUEST" -> {
                        val payload = JSONObject(event.payloadJson)
                        val rVal = payload.optString("responseValue").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val txt = payload.optString("textValue").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val lan = payload.optString("lanNumber").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val devTs = payload.optString("deviceTimestamp").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val dynMap = buildMap<String, String> {
                            val dynObj = payload.optJSONObject("dynamic_values") ?: payload.optJSONObject("dynamicValues")
                            dynObj?.keys()?.forEach { key ->
                                val v = dynObj.optString(key, "")
                                if (v.isNotBlank() && !v.equals("null", true)) put(key, v)
                            }
                        }
                        var res = supabaseClient.submitTaskResponse(
                            accessToken = token,
                            taskId = event.taskId,
                            responseType = payload.optString("responseType", "COMPLETED_NOT_COMPLETED"),
                            responseValue = rVal,
                            percentage = if (payload.has("percentage") && !payload.isNull("percentage")) payload.getInt("percentage") else null,
                            numeric1 = if (payload.has("numeric1") && !payload.isNull("numeric1")) payload.getDouble("numeric1") else null,
                            numeric2 = if (payload.has("numeric2") && !payload.isNull("numeric2")) payload.getDouble("numeric2") else null,
                            text = txt,
                            lanNumber = lan,
                            idempotencyKey = event.eventId,
                            deviceTimestamp = devTs,
                            dynamicValues = dynMap.takeIf { it.isNotEmpty() }
                        )

                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.submitTaskResponse(
                                    accessToken = token,
                                    taskId = event.taskId,
                                    responseType = payload.optString("responseType", "COMPLETED_NOT_COMPLETED"),
                                    responseValue = rVal,
                                    percentage = if (payload.has("percentage") && !payload.isNull("percentage")) payload.getInt("percentage") else null,
                                    numeric1 = if (payload.has("numeric1") && !payload.isNull("numeric1")) payload.getDouble("numeric1") else null,
                                    numeric2 = if (payload.has("numeric2") && !payload.isNull("numeric2")) payload.getDouble("numeric2") else null,
                                    text = txt,
                                    lanNumber = lan,
                                    idempotencyKey = event.eventId,
                                    deviceTimestamp = devTs,
                                    dynamicValues = dynMap.takeIf { it.isNotEmpty() }
                                )
                            }
                        }

                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val respId = serverObj.optString("response_id", event.eventId)
                            outboxDao.markEventSynced(event.id, respId, System.currentTimeMillis())
                            db.taskResponseDao().markResponseSynced(event.eventId)

                            // Update local task progress/status if returned
                            val newStatus = serverObj.optString("task_status")
                            if (newStatus.isNotBlank()) {
                                db.taskDao().updateTaskStatus(event.taskId, newStatus)
                            }
                        } else {
                            val ex = res.exceptionOrNull()
                            val err = ex?.message ?: "Unknown server error"
                            val isPermanent = isPermanentError(ex, err)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "TASK_ACKNOWLEDGEMENT" -> {
                        val payload = JSONObject(event.payloadJson)
                        val deviceTimestamp = payload.optString("deviceTimestamp", java.time.Instant.now().toString())
                        var res = supabaseClient.acknowledgeTaskAlarm(
                            accessToken = token,
                            taskId = event.taskId,
                            eventId = event.eventId,
                            deviceTimestamp = deviceTimestamp
                        )

                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.acknowledgeTaskAlarm(
                                    accessToken = token,
                                    taskId = event.taskId,
                                    eventId = event.eventId,
                                    deviceTimestamp = deviceTimestamp
                                )
                            }
                        }

                        if (res.isSuccess) {
                            outboxDao.markEventSynced(event.id, "ACK_CONFIRMED", System.currentTimeMillis())
                            db.taskResponseDao().markAcknowledgementSynced(event.eventId)
                        } else {
                            val ex = res.exceptionOrNull()
                            val err = ex?.message ?: "Unknown ack error"
                            val isPermanent = isPermanentError(ex, err)
                            outboxDao.markEventFailed(event.id, err, if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE", System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "SYSTEM_INCIDENT_SUBMISSION" -> {
                        val localIncident = db.systemIncidentDao().getIncidentBySubmissionId(event.eventId)
                        if (localIncident != null) {
                            var res = supabaseClient.submitSystemIncident(
                                accessToken = token,
                                incident = localIncident.toDomain()
                            )
                            if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                                val forceRes = authRepo.forceRefreshAccessToken()
                                if (forceRes.isSuccess) {
                                    token = forceRes.getOrThrow()
                                    res = supabaseClient.submitSystemIncident(
                                        accessToken = token,
                                        incident = localIncident.toDomain()
                                    )
                                }
                            }

                            if (res.isSuccess) {
                                outboxDao.markEventSynced(event.id, "INCIDENT_SYNCED", System.currentTimeMillis())
                                db.systemIncidentDao().updateSyncStatus(localIncident.id, "SYNCED", null)
                            } else {
                                val err = res.exceptionOrNull()?.message ?: "Unknown incident sync error"
                                outboxDao.markEventFailed(event.id, err, "FAILED_RETRYABLE", System.currentTimeMillis())
                                db.systemIncidentDao().updateSyncStatus(localIncident.id, "ERROR", err)
                                hasRetryableFailure = true
                            }
                        } else {
                            outboxDao.markEventSynced(event.id, "RECORD_REMOVED", System.currentTimeMillis())
                        }
                    }

                    "INCIDENT_ESCALATE" -> {
                        val payload = JSONObject(event.payloadJson)
                        val incidentId = payload.optString("incidentId", event.taskId)
                        val targetLevel = payload.optString("targetLevel", "VCO")
                        val reason = payload.optString("reason").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        var res = supabaseClient.escalateSystemIncident(
                            accessToken = token,
                            incidentId = incidentId,
                            targetLevel = targetLevel,
                            reason = reason,
                            clientOperationId = event.eventId
                        )
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull())) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.escalateSystemIncident(
                                    accessToken = token,
                                    incidentId = incidentId,
                                    targetLevel = targetLevel,
                                    reason = reason,
                                    clientOperationId = event.eventId
                                )
                            }
                        }
                        if (res.isSuccess) {
                            outboxDao.markEventSynced(event.id, "INCIDENT_ESCALATED", System.currentTimeMillis())
                            db.systemIncidentDao().updateSyncStatus(incidentId, "SYNCED", null)
                        } else {
                            val ex = res.exceptionOrNull()
                            when {
                                ex is SupabaseHttpException && ex.statusCode == 401 -> {
                                    outboxDao.resetSendingToPendingForUser(activeUserId)
                                    authPaused = true
                                }
                                ex is SupabaseHttpException && ex.isConflict -> {
                                    outboxDao.markEventSynced(event.id, "CONFLICT_SERVER_WINS", System.currentTimeMillis())
                                }
                                else -> {
                                    val err = ex?.message ?: "Incident escalation sync error"
                                    val isPermanent = isPermanentError(ex, err) ||
                                        err.contains("invalid escalation", true) ||
                                        err.contains("sequential escalation", true) ||
                                        err.contains("resolved", true)
                                    outboxDao.markEventFailed(
                                        event.id,
                                        err,
                                        if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE",
                                        System.currentTimeMillis()
                                    )
                                    if (!isPermanent) hasRetryableFailure = true
                                }
                            }
                        }
                    }

                    "TASK_UPDATE" -> {
                        val payload = JSONObject(event.payloadJson)
                        val taskId = payload.optString("task_id", event.taskId)
                        val updates = payload.optJSONObject("updates") ?: payload
                        var res = supabaseClient.updateTaskFields(token, taskId, updates)
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull())) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.updateTaskFields(token, taskId, updates)
                            }
                        }
                        if (res.isSuccess) {
                            outboxDao.markEventSynced(event.id, "TASK_UPDATED", System.currentTimeMillis())
                        } else {
                            val ex = res.exceptionOrNull()
                            when {
                                ex is SupabaseHttpException && ex.isAuthFailure -> {
                                    outboxDao.resetSendingToPendingForUser(activeUserId)
                                    authPaused = true
                                }
                                ex is SupabaseHttpException && ex.isConflict -> {
                                    outboxDao.markEventSynced(event.id, "CONFLICT_SERVER_WINS", System.currentTimeMillis())
                                }
                                else -> {
                                    val err = ex?.message ?: "Task update sync error"
                                    val isPermanent = isPermanentError(ex, err)
                                    outboxDao.markEventFailed(
                                        event.id,
                                        err,
                                        if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE",
                                        System.currentTimeMillis()
                                    )
                                    if (!isPermanent) hasRetryableFailure = true
                                }
                            }
                        }
                    }

                    else -> {
                        outboxDao.markEventFailed(
                            event.id,
                            "Unsupported operation: ${event.operationType}",
                            "FAILED_PERMANENT",
                            System.currentTimeMillis()
                        )
                    }
                }
                if (authPaused) break
            } catch (e: SupabaseHttpException) {
                Log.w(TAG, "HTTP ${e.statusCode} on event ${event.id}: ${e.message}")
                when {
                    e.statusCode == 401 -> {
                        outboxDao.resetSendingToPendingForUser(activeUserId)
                        authPaused = true
                        break
                    }
                    e.statusCode == 403 || isPermanentError(e, e.message) -> {
                        outboxDao.markEventFailed(event.id, e.message, "FAILED_PERMANENT", System.currentTimeMillis())
                    }
                    e.isConflict -> {
                        outboxDao.markEventSynced(event.id, "CONFLICT_SERVER_WINS", System.currentTimeMillis())
                    }
                    else -> {
                        outboxDao.markEventFailed(event.id, e.message, "FAILED_RETRYABLE", System.currentTimeMillis())
                        hasRetryableFailure = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error on event ${event.id}", e)
                outboxDao.markEventFailed(event.id, e.message ?: "Exception", "FAILED_RETRYABLE", System.currentTimeMillis())
                hasRetryableFailure = true
            }
        }

        if (authPaused) {
            db.syncMetadataDao().updateSyncStatus(
                SyncStatus.ERROR.name,
                "Authentication paused: Please sign in again to resume synchronization"
            )
            return@withContext Result.failure()
        }

        runCatching { TaskzApplication.instance.taskzSyncManager.syncNow(forceFull = forceFull) }

        val remainingEvents = outboxDao.getPendingEventsForUser(activeUserId)
        val remainingPending = remainingEvents.size
        if (!hasRetryableFailure && remainingPending == 0) {
            db.syncMetadataDao().updateLastSuccessfulSync(System.currentTimeMillis())
            db.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
            Result.success()
        } else if (hasRetryableFailure || remainingPending > 0) {
            val latestErr = remainingEvents.firstNotNullOfOrNull { it.lastError?.takeIf { e -> e.isNotBlank() } }
            val msg = if (latestErr != null) {
                "$remainingPending pending — $latestErr"
            } else {
                "$remainingPending pending operations awaiting sync"
            }
            db.syncMetadataDao().updateSyncStatus(SyncStatus.PENDING.name, msg)
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun sanitizeTaskPayload(obj: JSONObject): JSONObject {
        val gid = obj.optString("group_id")
        if (gid.isNotBlank() && !com.example.core.network.SupabaseConfig.isValidUuid(gid)) {
            obj.remove("group_id")
        }
        val sid = obj.optString("shift_id")
        if (sid.isNotBlank() && !com.example.core.network.SupabaseConfig.isValidUuid(sid)) {
            obj.remove("shift_id")
        }
        val vis = obj.optString("visibility", "everyone")
        obj.put("visibility", if (vis.equals("restricted", ignoreCase = true)) "restricted" else "everyone")

        val rawUsers = obj.optJSONArray("assigned_user_ids")
        if (rawUsers != null) {
            val cleanUsers = org.json.JSONArray()
            for (i in 0 until rawUsers.length()) {
                val u = rawUsers.optString(i)
                if (com.example.core.network.SupabaseConfig.isValidUuid(u)) cleanUsers.put(u)
            }
            obj.put("assigned_user_ids", cleanUsers)
        }

        val rawReqs = obj.optJSONArray("requirements")
        if (rawReqs != null) {
            val cleanReqs = org.json.JSONArray()
            for (i in 0 until rawReqs.length()) {
                val reqObj = rawReqs.optJSONObject(i) ?: continue
                if (com.example.core.network.SupabaseConfig.isValidUuid(reqObj.optString("role_id"))) {
                    cleanReqs.put(reqObj)
                }
            }
            obj.put("requirements", cleanReqs)
        }
        return obj
    }

    private fun isAuthExpiredError(err: Throwable?): Boolean {
        if (err is SupabaseHttpException && err.statusCode == 401) return true
        return isAuthExpiredError(err?.message)
    }

    private fun isAuthExpiredError(msg: String?): Boolean {
        if (msg.isNullOrBlank()) return false
        return msg.contains("JWT", ignoreCase = true) ||
            msg.contains("401", ignoreCase = true) ||
            msg.contains("Unauthorized", ignoreCase = true)
    }

    private fun isPermanentError(err: Throwable?, msg: String): Boolean {
        if (err is SupabaseHttpException && err.statusCode in setOf(400, 403, 404, 422)) {
            return true
        }
        return msg.contains("forbidden", ignoreCase = true) ||
            msg.contains("not authorized", ignoreCase = true) ||
            msg.contains("unauthorized", ignoreCase = true) ||
            msg.contains("permission revoked", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("cancelled", ignoreCase = true) ||
            msg.contains("inactive", ignoreCase = true) ||
            msg.contains("cannot be blank", ignoreCase = true) ||
            msg.contains("violates foreign key", ignoreCase = true) ||
            msg.contains("invalid input syntax", ignoreCase = true) ||
            msg.contains("already resolved", ignoreCase = true) ||
            msg.contains("sequential escalation", ignoreCase = true)
    }
}
