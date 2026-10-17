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
import com.example.core.datastore.UserPreferencesDataStore
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
        val token = session.accessToken
        val activeUserId = session.userId

        if (token.isNullOrBlank() || activeUserId.isNullOrBlank()) {
            Log.w(TAG, "No active user session found, skipping sync.")
            return@withContext Result.success()
        }

        // Strictly query pending events for the currently active user to prevent account data leakage
        val pendingEvents = outboxDao.getPendingEventsForUser(activeUserId)
        if (pendingEvents.isEmpty()) {
            return@withContext Result.success()
        }

        Log.d(TAG, "Processing ${pendingEvents.size} pending outbox events for user $activeUserId")
        var hasRetryableFailure = false

        for (event in pendingEvents) {
            val now = System.currentTimeMillis()
            outboxDao.markEventSending(event.id, now)

            try {
                when (event.operationType) {
                    "TASK_RESPONSE", "PROGRESS_UPDATE", "LAN_CONFIRMATION", "COMPLETION_REQUEST" -> {
                        val payload = JSONObject(event.payloadJson)
                        val res = supabaseClient.submitTaskResponse(
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
                        val res = supabaseClient.acknowledgeTaskAlarm(
                            accessToken = token,
                            taskId = event.taskId,
                            eventId = event.eventId,
                            deviceTimestamp = deviceTimestamp
                        )

                        if (res.isSuccess) {
                            outboxDao.markEventSynced(event.id, "ACK_CONFIRMED", System.currentTimeMillis())
                            db.taskResponseDao().markAcknowledgementSynced(event.eventId)
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Unknown ack error"
                            outboxDao.markEventFailed(event.id, err, "FAILED_RETRYABLE", System.currentTimeMillis())
                            hasRetryableFailure = true
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

        if (hasRetryableFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
