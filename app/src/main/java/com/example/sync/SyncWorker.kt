package com.example.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.TaskzApplication
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.SyncStatus
import com.example.data.local.TaskzDatabase
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
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
                ExistingWorkPolicy.REPLACE,
                syncRequest
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

        val isFirebaseOrOfflineUser = token.startsWith("firebase_") || 
            token.startsWith("offline_") || 
            activeUserId.startsWith("phone_user_")

        if (isFirebaseOrOfflineUser) {
            val remaining = outboxDao.getPendingEventsForUser(activeUserId).size
            if (remaining == 0) {
                db.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
            }
            return@withContext Result.success()
        }

        // Strictly query pending events for the currently active user to prevent account data leakage
        val pendingEvents = outboxDao.getPendingEventsForUser(activeUserId)
        if (pendingEvents.isEmpty()) {
            val remaining = outboxDao.getPendingEventsForUser(activeUserId).size
            if (remaining == 0) {
                db.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
            }
            return@withContext Result.success()
        }

        Log.d(TAG, "Processing ${pendingEvents.size} pending outbox events for user $activeUserId")
        var hasRetryableFailure = false

        for (event in pendingEvents) {
            val now = System.currentTimeMillis()
            outboxDao.markEventSending(event.id, now)

            try {
                when (event.operationType) {
                    "TASK_CREATION" -> {
                        val payload = JSONObject(event.payloadJson)
                        var res = supabaseClient.createTaskAtomic(token, payload)
                        if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
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
                            db.taskDao().updateTaskStatus(event.taskId, "open")
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Task creation sync error"
                            val isPermanent = err.contains("not authorized", ignoreCase = true) ||
                                    err.contains("inactive", ignoreCase = true) ||
                                    err.contains("cannot be blank", ignoreCase = true)
                            val status = if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE"
                            outboxDao.markEventFailed(event.id, err, status, System.currentTimeMillis())
                            if (!isPermanent) hasRetryableFailure = true
                        }
                    }

                    "GROUP_CREATION" -> {
                        val payload = JSONObject(event.payloadJson)
                        var res = supabaseClient.createGroup(token, payload)
                        if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
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
                        if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
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
                        var res = supabaseClient.submitTaskResponse(
                            accessToken = token,
                            taskId = event.taskId,
                            responseType = payload.optString("responseType", "COMPLETED_NOT_COMPLETED"),
                            responseValue = payload.optString("responseValue").takeIf { it.isNotBlank() },
                            percentage = if (payload.has("percentage") && !payload.isNull("percentage")) payload.getInt("percentage") else null,
                            numeric1 = if (payload.has("numeric1") && !payload.isNull("numeric1")) payload.getDouble("numeric1") else null,
                            numeric2 = if (payload.has("numeric2") && !payload.isNull("numeric2")) payload.getDouble("numeric2") else null,
                            text = payload.optString("textValue").takeIf { it.isNotBlank() },
                            lanNumber = payload.optString("lanNumber").takeIf { it.isNotBlank() },
                            idempotencyKey = event.eventId,
                            deviceTimestamp = payload.optString("deviceTimestamp").takeIf { it.isNotBlank() }
                        )

                        if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
                            val forceRes = authRepo.forceRefreshAccessToken()
                            if (forceRes.isSuccess) {
                                token = forceRes.getOrThrow()
                                res = supabaseClient.submitTaskResponse(
                                    accessToken = token,
                                    taskId = event.taskId,
                                    responseType = payload.optString("responseType", "COMPLETED_NOT_COMPLETED"),
                                    responseValue = payload.optString("responseValue").takeIf { it.isNotBlank() },
                                    percentage = if (payload.has("percentage") && !payload.isNull("percentage")) payload.getInt("percentage") else null,
                                    numeric1 = if (payload.has("numeric1") && !payload.isNull("numeric1")) payload.getDouble("numeric1") else null,
                                    numeric2 = if (payload.has("numeric2") && !payload.isNull("numeric2")) payload.getDouble("numeric2") else null,
                                    text = payload.optString("textValue").takeIf { it.isNotBlank() },
                                    lanNumber = payload.optString("lanNumber").takeIf { it.isNotBlank() },
                                    idempotencyKey = event.eventId,
                                    deviceTimestamp = payload.optString("deviceTimestamp").takeIf { it.isNotBlank() }
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
                            val err = res.exceptionOrNull()?.message ?: "Unknown server error"
                            val isPermanent = err.contains("not found", ignoreCase = true) ||
                                    err.contains("cancelled", ignoreCase = true) ||
                                    err.contains("not authorized", ignoreCase = true)

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

                        if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
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
                            val err = res.exceptionOrNull()?.message ?: "Unknown ack error"
                            outboxDao.markEventFailed(event.id, err, "FAILED_RETRYABLE", System.currentTimeMillis())
                            hasRetryableFailure = true
                        }
                    }

                    "SYSTEM_INCIDENT_SUBMISSION" -> {
                        val localIncident = db.systemIncidentDao().getIncidentBySubmissionId(event.eventId)
                        if (localIncident != null) {
                            var res = supabaseClient.submitSystemIncident(
                                accessToken = token,
                                incident = localIncident.toDomain()
                            )
                            if (res.isFailure && res.exceptionOrNull()?.message?.contains("JWT", ignoreCase = true) == true) {
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

                    else -> {
                        outboxDao.markEventFailed(
                            event.id,
                            "Unsupported operation: ${event.operationType}",
                            "FAILED_PERMANENT",
                            System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error on event ${event.id}", e)
                outboxDao.markEventFailed(event.id, e.message ?: "Exception", "FAILED_RETRYABLE", System.currentTimeMillis())
                hasRetryableFailure = true
            }
        }

        val remainingPending = outboxDao.getPendingEventsForUser(activeUserId).size
        if (!hasRetryableFailure && remainingPending == 0) {
            db.syncMetadataDao().updateLastSuccessfulSync(System.currentTimeMillis())
            db.syncMetadataDao().updateSyncStatus(SyncStatus.SYNCED.name, "Up to date")
            Result.success()
        } else if (hasRetryableFailure) {
            db.syncMetadataDao().updateSyncStatus(
                SyncStatus.PENDING.name,
                "$remainingPending pending operations awaiting sync"
            )
            Result.retry()
        } else {
            Result.success()
        }
    }
}
