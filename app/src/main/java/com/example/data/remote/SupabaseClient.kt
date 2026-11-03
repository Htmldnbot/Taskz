package com.example.data.remote

import android.util.Log
import com.example.core.model.PasswordResetRequest
import com.example.core.model.SystemIncident
import com.example.core.model.SystemIncidentAudit
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AuthSessionResponse(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val employeeId: String
)

class SupabaseClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TaskzSupabase"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }

    private fun baseUrl(): String = SupabaseConfig.getActiveUrl().trimEnd('/')
    private fun anonKey(): String = SupabaseConfig.getActiveKey()

    private fun checkConfig() {
        if (!SupabaseConfig.isConfigured) {
            throw IOException("Supabase is not configured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY.")
        }
    }

    /**
     * Sign up a new employee.
     * Uses deterministic auth identifier derived from Employee ID.
     */
    suspend fun signUp(
        employeeId: String,
        password: String,
        fullName: String,
        mobile: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val normalized = SupabaseConfig.normalizeEmployeeId(employeeId)
            val internalEmail = SupabaseConfig.toInternalAuthEmail(normalized)
            val jsonBody = JSONObject().apply {
                put("email", internalEmail)
                put("password", password)
                put("data", JSONObject().apply {
                    put("employee_id", normalized)
                    put("full_name", fullName.trim())
                    put("mobile", mobile.trim())
                })
            }

            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/signup")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, "Sign up failed (${response.code})")
                if (response.code == 429 ||
                    errorMsg.contains("rate limit", ignoreCase = true) ||
                    errorMsg.contains("over_email_send_rate_limit", ignoreCase = true) ||
                    errorMsg.contains("email_address_invalid", ignoreCase = true) ||
                    (errorMsg.contains("invalid", ignoreCase = true) && errorMsg.contains("email", ignoreCase = true))
                ) {
                    return@withContext Result.failure(
                        IOException(
                            "Email confirmation is enabled in your Supabase project. " +
                            "In Supabase Dashboard -> Authentication -> Providers -> Email, please toggle OFF 'Confirm email'. " +
                            "Since Taskz uses Employee ID + Password login, email confirmation must be disabled."
                        )
                    )
                }
                if (errorMsg.contains("already registered", ignoreCase = true) ||
                    errorMsg.contains("User already exists", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Employee ID '$employeeId' is already registered."))
                }
                if (errorMsg.contains("Database error saving new user", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IOException("Database validation rejected registration. Check that Full Name has at least 2 characters, Employee ID is alphanumeric, and Mobile has country code (e.g. +91 9876543210).")
                    )
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val userObj = json.optJSONObject("user")
            val userId = userObj?.optString("id") ?: json.optString("id")

            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IOException("Invalid response from auth server: user ID missing"))
            }

            Result.success(
                AuthSessionResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    userId = userId,
                    employeeId = employeeId.trim()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Sign up request error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Log in an existing employee with Employee ID and Password.
     */
    suspend fun login(
        employeeId: String,
        password: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val normalized = SupabaseConfig.normalizeEmployeeId(employeeId)
            val candidateEmails = listOf(
                SupabaseConfig.toInternalAuthEmail(normalized),
                SupabaseConfig.toLegacyAuthEmail(normalized),
                "emp_${normalized.lowercase()}@taskzapp.org",
                "${normalized.lowercase()}@taskzapp.org",
                "${normalized.lowercase()}@taskzapp.com"
            )

            var lastErrorMsg = "Login failed"
            for (email in candidateEmails) {
                val jsonBody = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }

                val request = Request.Builder()
                    .url("${baseUrl()}/auth/v1/token?grant_type=password")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer ${anonKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val userObj = json.getJSONObject("user")
                    val userId = userObj.getString("id")

                    return@withContext Result.success(
                        AuthSessionResponse(
                            accessToken = accessToken,
                            refreshToken = refreshToken.takeIf { it.isNotBlank() },
                            userId = userId,
                            employeeId = employeeId.trim()
                        )
                    )
                } else {
                    lastErrorMsg = parseErrorMessage(responseBody, "Login failed")
                }
            }

            if (lastErrorMsg.contains("invalid", ignoreCase = true) ||
                lastErrorMsg.contains("credentials", ignoreCase = true)
            ) {
                return@withContext Result.failure(IOException("Invalid Employee ID or password."))
            }
            Result.failure(IOException(lastErrorMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh auth session.
     */
    suspend fun refreshSession(refreshToken: String): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val jsonBody = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Session expired. Please log in again."))
            }

            val json = JSONObject(responseBody)
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token")
            val userObj = json.getJSONObject("user")
            val userId = userObj.getString("id")

            Result.success(
                AuthSessionResponse(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken.takeIf { it.isNotBlank() },
                    userId = userId,
                    employeeId = ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Terminate server session on logout.
     */
    suspend fun logout(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!SupabaseConfig.isConfigured || accessToken.isBlank()) return@withContext Result.success(Unit)
            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/logout")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * Fetch user profile from PostgREST by id (public.profiles.id = auth.users.id).
     */
    suspend fun fetchProfileById(
        accessToken: String,
        userId: String
    ): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val url = "${baseUrl()}/rest/v1/profiles?id=eq.$userId&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load profile")))
            }

            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) {
                return@withContext Result.success(null)
            }

            val item = jsonArray.getJSONObject(0)
            Result.success(parseProfileJson(item))
        } catch (e: Exception) {
            Log.w(TAG, "Notice fetching profile: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Insert new profile row if needed (note: on_auth_user_created trigger in DB usually creates it).
     */
    suspend fun createProfile(
        accessToken: String,
        profile: UserProfile
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("id", profile.id)
                put("employee_id", SupabaseConfig.normalizeEmployeeId(profile.employeeId))
                put("full_name", profile.fullName.trim())
                put("mobile", profile.mobile.trim())
                if (!profile.profilePhotoPath.isNullOrBlank()) {
                    put("profile_photo_path", profile.profilePhotoPath)
                }
                put("app_role", "employee")
                put("is_active", true)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Prefer", "return=representation")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, "Failed to create profile")
                if (errorMsg.contains("duplicate key", ignoreCase = true) ||
                    errorMsg.contains("profiles_employee_id_unique", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Employee ID is already in use by another account."))
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            val jsonArray = JSONArray(responseBody)
            val created = jsonArray.getJSONObject(0)
            Result.success(parseProfileJson(created))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update safe profile fields (Full Name, Mobile, Photo Path).
     * Where profiles.id = userId.
     */
    suspend fun updateProfile(
        accessToken: String,
        userId: String,
        fullName: String,
        mobile: String,
        photoPath: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("full_name", fullName.trim())
                put("mobile", mobile.trim())
                if (photoPath != null) {
                    put("profile_photo_path", photoPath)
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles?id=eq.$userId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Prefer", "return=representation")
                .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to update profile")))
            }

            val jsonArray = JSONArray(responseBody)
            val updated = jsonArray.getJSONObject(0)
            Result.success(parseProfileJson(updated))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload photo to private Supabase Storage bucket 'profile-photos'.
     * Path convention: USER_UUID/avatar_timestamp.jpg
     * Returns: The relative storage path "USER_UUID/filename.jpg" (NOT a permanent public URL).
     */
    suspend fun uploadProfilePhoto(
        accessToken: String,
        userId: String,
        imageBytes: ByteArray,
        filename: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val storagePath = "$userId/$filename"
            val uploadUrl = "${baseUrl()}/storage/v1/object/profile-photos/$storagePath"

            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("x-upsert", "true")
                .post(imageBytes.toRequestBody(JPEG_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to upload photo")))
            }

            Result.success(storagePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a short-lived signed URL for a private profile photo in 'profile-photos' bucket.
     */
    suspend fun getSignedProfilePhotoUrl(
        accessToken: String,
        photoPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val signUrl = "${baseUrl()}/storage/v1/object/sign/profile-photos/$photoPath"
            val jsonBody = JSONObject().apply {
                put("expiresIn", 7200) // 2 hours
            }

            val request = Request.Builder()
                .url(signUrl)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to generate signed image URL")))
            }

            val json = JSONObject(responseBody)
            val signedPart = json.getString("signedURL")
            val fullSignedUrl = "${baseUrl()}/storage/v1$signedPart"
            Result.success(fullSignedUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an old profile photo from private 'profile-photos' bucket.
     */
    suspend fun deleteProfilePhoto(
        accessToken: String,
        photoPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val deleteUrl = "${baseUrl()}/storage/v1/object/profile-photos"
            val jsonBody = JSONObject().apply {
                put("prefixes", JSONArray().apply { put(photoPath) })
            }

            val request = Request.Builder()
                .url(deleteUrl)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .delete(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Call Supabase database function: public.record_server_fetch()
     * Records real server sync fetch timestamp in public.user_sync_state.
     */
    suspend fun recordServerFetch(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/record_server_fetch")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "record_server_fetch failed")))
            }

            Result.success(responseBody.trim('"'))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a password reset request via controlled RPC function.
     * Prevents account enumeration and does not allow direct table manipulation.
     */
    suspend fun submitPasswordResetRequest(
        employeeId: String,
        mobile: String,
        message: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_employee_id", employeeId.trim())
                put("p_mobile", mobile.trim())
                if (!message.isNullOrBlank()) {
                    put("p_message", message.trim())
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/submit_password_reset_request")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Request submission failed")))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // ADMIN OPERATIONS
    // ==========================================

    /**
     * Admin: Fetch all users.
     */
    suspend fun fetchAllProfiles(accessToken: String): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles?select=*&order=created_at.desc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load users")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until array.length()) {
                list.add(parseProfileJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin: Activate or Deactivate user account.
     */
    suspend fun updateUserActiveStatus(
        accessToken: String,
        profileId: String,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("is_active", isActive)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles?id=eq.$profileId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to update account status")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin: Fetch all pending password reset requests.
     */
    suspend fun fetchPasswordResetRequests(accessToken: String): Result<List<PasswordResetRequest>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/password_reset_requests?select=*&order=created_at.desc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load requests")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<PasswordResetRequest>()
            for (i in 0 until array.length()) {
                list.add(parseResetRequestJson(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin: Call Edge Function to reset an employee's password server-side.
     */
    suspend fun callAdminResetPassword(
        accessToken: String,
        requestId: String?,
        employeeId: String,
        newTemporaryPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                if (!requestId.isNullOrBlank()) put("requestId", requestId)
                put("employeeId", employeeId.trim())
                put("newTemporaryPassword", newTemporaryPassword)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/functions/v1/admin-reset-password")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Admin reset function failed (${response.code})")))
            }

            val json = JSONObject(responseBody)
            val msg = json.optString("message", "Password reset successfully")
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit task response via RPC function: public.submit_task_response()
     */
    /**
     * Submit task response via RPC function: public.submit_task_response()
     */
    suspend fun submitTaskResponse(
        accessToken: String,
        taskId: String,
        responseType: String,
        responseValue: String? = null,
        percentage: Int? = null,
        numeric1: Double? = null,
        numeric2: Double? = null,
        text: String? = null,
        lanNumber: String? = null,
        idempotencyKey: String? = null,
        deviceTimestamp: String? = null,
        dynamicValues: Map<String, String>? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task_id", taskId)
                put("p_response_type", responseType)
                if (responseValue != null) put("p_response_value", responseValue)
                if (percentage != null) put("p_percentage", percentage)
                if (numeric1 != null) put("p_numeric_1", numeric1)
                if (numeric2 != null) put("p_numeric_2", numeric2)
                if (text != null) put("p_text", text)
                if (lanNumber != null) put("p_lan_number", lanNumber)
                if (idempotencyKey != null) put("p_idempotency_key", idempotencyKey)
                if (deviceTimestamp != null) put("p_device_timestamp", deviceTimestamp)
                if (dynamicValues != null && dynamicValues.isNotEmpty()) {
                    put("p_dynamic_values", JSONObject(dynamicValues))
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/submit_task_response")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Submit response failed (${response.code})")))
            }
            Result.success(JSONObject(responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Acknowledge task alarm via RPC function: public.acknowledge_task_alarm()
     */
    suspend fun acknowledgeTaskAlarm(
        accessToken: String,
        taskId: String,
        eventId: String,
        deviceTimestamp: String = java.time.Instant.now().toString()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task_id", taskId)
                put("p_event_id", eventId)
                put("p_device_timestamp", deviceTimestamp)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/acknowledge_task_alarm")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Acknowledge alarm failed (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch Sync Offline Outbox Events via RPC function: public.sync_offline_events()
     */
    suspend fun batchSyncOfflineEvents(
        accessToken: String,
        eventsArray: JSONArray
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_events", eventsArray)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/sync_offline_events")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Batch sync failed (${response.code})")))
            }
            Result.success(JSONObject(responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get Real-time Operations Dashboard data via RPC function: public.get_task_operations_dashboard()
     */
    suspend fun getTaskOperationsDashboard(
        accessToken: String,
        taskId: String
    ): Result<com.example.core.model.TaskOperationsDashboardData> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task_id", taskId)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/get_task_operations_dashboard")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load operations dashboard (${response.code})")))
            }

            val json = JSONObject(responseBody)
            val lansArray = json.optJSONArray("lan_statuses") ?: JSONArray()
            val lanList = mutableListOf<com.example.core.model.LanStatusItem>()
            for (i in 0 until lansArray.length()) {
                val item = lansArray.getJSONObject(i)
                lanList.add(
                    com.example.core.model.LanStatusItem(
                        lanNumber = item.optString("lan_number"),
                        status = item.optString("status", "pending"),
                        progress = item.optInt("progress", 0),
                        employeeName = item.optString("employee_name").takeIf { it.isNotBlank() },
                        employeeId = item.optString("employee_id").takeIf { it.isNotBlank() },
                        lastResponseTime = item.optString("last_response_time").takeIf { it.isNotBlank() },
                        completedAt = item.optString("completed_at").takeIf { it.isNotBlank() },
                        isOverdue = item.optBoolean("is_overdue", false)
                    )
                )
            }

            val respsArray = json.optJSONArray("recent_responses") ?: JSONArray()
            val respList = mutableListOf<com.example.core.model.TaskResponseItem>()
            for (i in 0 until respsArray.length()) {
                val r = respsArray.getJSONObject(i)
                respList.add(
                    com.example.core.model.TaskResponseItem(
                        id = r.optString("id"),
                        taskId = taskId,
                        userId = "",
                        employeeId = r.optString("employee_id"),
                        employeeName = r.optString("employee_name").takeIf { it.isNotBlank() },
                        lanNumber = r.optString("lan_number").takeIf { it.isNotBlank() },
                        responseType = runCatching { com.example.core.model.ResponseType.valueOf(r.optString("response_type")) }.getOrDefault(com.example.core.model.ResponseType.COMPLETED_NOT_COMPLETED),
                        responseValue = r.optString("response_value").takeIf { it.isNotBlank() },
                        percentageValue = if (r.has("percentage") && !r.isNull("percentage")) r.getInt("percentage") else null,
                        submittedAt = r.optString("submitted_at"),
                        isPendingSync = false,
                        isLate = r.optBoolean("is_late", false),
                        delaySeconds = r.optInt("delay_seconds", 0)
                    )
                )
            }

            val data = com.example.core.model.TaskOperationsDashboardData(
                taskId = json.optString("task_id", taskId),
                title = json.optString("title", ""),
                taskType = json.optString("task_type", "SIMPLE"),
                importance = json.optString("importance", "NORMAL"),
                status = json.optString("status", "open"),
                completionPolicy = json.optString("completion_policy", "SINGLE_ASSIGNEE"),
                executionTime = json.optString("execution_time").takeIf { it.isNotBlank() },
                deadlineTime = json.optString("deadline_time").takeIf { it.isNotBlank() },
                currentProgress = json.optInt("current_progress", 0),
                totalLans = json.optInt("total_lans", 0),
                completedLans = json.optInt("completed_lans", 0),
                pendingLans = json.optInt("pending_lans", 0),
                overdueLans = json.optInt("overdue_lans", 0),
                totalParticipants = json.optInt("total_participants", 0),
                acknowledgedCount = json.optInt("acknowledged_count", 0),
                lanStatuses = lanList,
                recentResponses = respList
            )
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // BATCH 1: SERVER-BACKED TASKS, GROUPS, SHIFTS & MULTI-USER API
    // ==========================================

    /**
     * Atomically creates task on Supabase with idempotency via client_operation_id.
     */
    suspend fun createTaskAtomic(
        accessToken: String,
        taskPayload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val requestBody = JSONObject().apply {
                put("p_task", taskPayload)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/create_task_atomic")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to create task on server (${response.code})")))
            }

            val json = JSONObject(responseBody)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all tasks visible to current user (creator, assigned, or group member) from Supabase.
     */
    suspend fun fetchTasks(accessToken: String): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val url = "${baseUrl()}/rest/v1/tasks?select=*,task_participants(*),task_role_requirements(*),task_groups(id,title),shifts(id,name)&order=created_at.desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to fetch tasks (${response.code})")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search employee directory via Supabase RPC search_employee_directory.
     * Returns real profiles matching query.
     */
    suspend fun searchEmployeeDirectory(
        accessToken: String,
        query: String
    ): Result<List<UserProfile>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_query", query.trim())
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/search_employee_directory")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // If RPC not found, fallback to direct profiles query
                val fallbackReq = Request.Builder()
                    .url("${baseUrl()}/rest/v1/profiles?select=*&is_active=eq.true&order=full_name.asc&limit=100")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val fallbackResp = okHttpClient.newCall(fallbackReq).execute()
                val fbBody = fallbackResp.body?.string() ?: ""
                if (fallbackResp.isSuccessful) {
                    val arr = JSONArray(fbBody)
                    val res = mutableListOf<UserProfile>()
                    for (i in 0 until arr.length()) {
                        val p = parseProfileJson(arr.getJSONObject(i))
                        if (query.isBlank() || p.fullName.contains(query, ignoreCase = true) || p.employeeId.contains(query, ignoreCase = true)) {
                            res.add(p)
                        }
                    }
                    return@withContext Result.success(res)
                }
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Employee search failed")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    UserProfile(
                        id = obj.optString("id"),
                        employeeId = obj.optString("employee_id"),
                        fullName = obj.optString("full_name"),
                        mobile = obj.optString("mobile", ""),
                        profilePhotoPath = obj.optString("profile_photo_path").takeIf { it.isNotBlank() },
                        appRole = "employee",
                        isActive = true
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves an employeeId string (e.g. "EMP1024") to their actual profiles.id UUID.
     */
    suspend fun resolveEmployeeToUuid(accessToken: String, idOrEmployeeId: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = idOrEmployeeId.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Employee ID cannot be blank"))
        }
        val isUuid = try {
            java.util.UUID.fromString(trimmed)
            true
        } catch (_: Exception) {
            false
        }
        if (isUuid) {
            return@withContext Result.success(trimmed)
        }

        try {
            checkConfig()
            val encodedEmpId = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles?employee_id=eq.$encodedEmpId&select=id,is_active&limit=1")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Failed to query employee profile: ${response.code}"))
            }

            val array = JSONArray(body)
            if (array.length() == 0) {
                return@withContext Result.failure(NoSuchElementException("No employee profile found for: $trimmed"))
            }

            val obj = array.getJSONObject(0)
            val uuid = obj.getString("id")
            val isActive = obj.optBoolean("is_active", true)
            if (!isActive) {
                return@withContext Result.failure(IllegalStateException("Employee $trimmed is inactive"))
            }

            Result.success(uuid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Fetch groups from Supabase.
     */
    suspend fun fetchGroups(accessToken: String): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_groups?select=*,group_members(*)&order=created_at.desc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load groups")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create Group on Supabase.
     */
    suspend fun createGroup(
        accessToken: String,
        groupPayload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_groups")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(groupPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to create group on server")))
            }

            val array = JSONArray(responseBody)
            if (array.length() > 0) {
                Result.success(array.getJSONObject(0))
            } else {
                Result.success(groupPayload)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update Group on Supabase.
     */
    suspend fun updateGroup(
        accessToken: String,
        groupId: String,
        groupPayload: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_groups?id=eq.$groupId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .patch(groupPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to update group on server")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch shifts from Supabase.
     */
    suspend fun fetchShifts(accessToken: String): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/shifts?select=*&order=starts_at.asc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to load shifts")))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<JSONObject>()
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create Shift on Supabase.
     */
    suspend fun createShift(
        accessToken: String,
        shiftPayload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/shifts")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(shiftPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to create shift on server")))
            }

            val array = JSONArray(responseBody)
            if (array.length() > 0) {
                Result.success(array.getJSONObject(0))
            } else {
                Result.success(shiftPayload)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update Shift on Supabase.
     */
    suspend fun updateShift(
        accessToken: String,
        shiftId: String,
        shiftPayload: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/shifts?id=eq.$shiftId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .patch(shiftPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to update shift on server")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copy shift with relative time offsets via Supabase RPC.
     */
    suspend fun copyShift(
        accessToken: String,
        sourceShiftId: String,
        newShiftName: String,
        offsetMinutes: Int,
        copyManpower: Boolean = true,
        copyAssignments: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_source_shift", sourceShiftId)
                put("p_new_name", newShiftName)
                put("p_offset_minutes", offsetMinutes)
                put("p_copy_manpower", copyManpower)
                put("p_copy_assignments", copyAssignments)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/copy_shift")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()?.trim('"', ' ', '\n', '\r') ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to copy shift on server")))
            }

            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Assign user to task via Supabase RPC.
     */
    suspend fun assignUserToTask(
        accessToken: String,
        taskId: String,
        userId: String,
        roleId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task", taskId)
                put("p_user", userId)
                if (!roleId.isNullOrBlank()) {
                    put("p_role", roleId)
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/assign_user_to_task")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to assign user to task")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Request task join via Supabase RPC.
     */
    suspend fun requestTaskJoin(
        accessToken: String,
        taskId: String,
        roleId: String? = null,
        message: String? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task", taskId)
                if (!roleId.isNullOrBlank()) put("p_role", roleId)
                if (!message.isNullOrBlank()) put("p_message", message)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/request_task_join")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to submit join request")))
            }
            Result.success(JSONObject(responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Review task join request (approve or reject) via Supabase RPC.
     */
    suspend fun reviewTaskJoin(
        accessToken: String,
        requestId: String,
        approve: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_request", requestId)
                put("p_approve", approve)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/review_task_join")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to review join request")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leave task via Supabase RPC.
     */
    suspend fun leaveTask(
        accessToken: String,
        taskId: String,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_task", taskId)
                if (!reason.isNullOrBlank()) put("p_reason", reason)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/leave_task")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to leave task")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove task participant via Supabase RPC.
     */
    suspend fun removeParticipant(
        accessToken: String,
        participantId: String,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("p_participant", participantId)
                if (!reason.isNullOrBlank()) put("p_reason", reason)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/remove_task_participant")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to remove participant")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // JSON PARSING UTILS
    // ==========================================

    private fun parseProfileJson(json: JSONObject): UserProfile {
        return UserProfile(
            id = json.optString("id"),
            employeeId = json.optString("employee_id"),
            fullName = json.optString("full_name"),
            mobile = json.optString("mobile"),
            profilePhotoPath = json.optString("profile_photo_path").takeIf { it.isNotBlank() },
            appRole = json.optString("app_role", "employee"),
            isActive = json.optBoolean("is_active", true),
            mustChangePassword = json.optBoolean("must_change_password", false),
            lastSeenAt = json.optString("last_seen_at").takeIf { it.isNotBlank() },
            createdAt = json.optString("created_at"),
            updatedAt = json.optString("updated_at")
        )
    }

    private fun parseResetRequestJson(json: JSONObject): PasswordResetRequest {
        return PasswordResetRequest(
            id = json.optString("id"),
            employeeId = json.optString("employee_id"),
            mobile = json.optString("mobile"),
            message = json.optString("message").takeIf { it.isNotBlank() },
            status = json.optString("status", "pending"),
            createdAt = json.optString("created_at"),
            resolvedAt = json.optString("resolved_at").takeIf { it.isNotBlank() },
            resolvedBy = json.optString("resolved_by").takeIf { it.isNotBlank() }
        )
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("msg", json.optString("message", json.optString("error_description", json.optString("error", fallback))))
        } catch (_: Exception) {
            fallback
        }
    }

    // ==========================================
    // SYSTEM INCIDENT REGISTER API
    // ==========================================

    suspend fun submitSystemIncident(
        accessToken: String,
        incident: SystemIncident
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val payload = JSONObject().apply {
                put("id", incident.id)
                put("submission_id", incident.submissionId)
                if (!incident.centreId.isNullOrBlank()) put("centre_id", incident.centreId)
                if (!incident.examSessionId.isNullOrBlank()) put("exam_session_id", incident.examSessionId)
                put("state_name", incident.stateName)
                put("district_name", incident.districtName)
                put("centre_code", incident.centreCode)
                put("centre_name", incident.centreName)
                put("exam_date", incident.examDate)
                put("exam_name", incident.examName)
                put("shift_name", incident.shiftName)
                put("lan_name", incident.lanName)
                put("mac_address", incident.macAddress)
                put("ip_address", incident.ipAddress)
                put("locked_system_number", incident.lockedSystemNumber)
                if (incident.candidateShifted && !incident.newlyAllocatedSystemNumber.isNullOrBlank()) {
                    put("newly_allocated_system_number", incident.newlyAllocatedSystemNumber)
                } else {
                    put("newly_allocated_system_number", JSONObject.NULL)
                }
                put("candidate_shifted", incident.candidateShifted)
                put("incident_time", java.time.Instant.ofEpochMilli(incident.incidentTimestampMillis).toString())
                put("reason", incident.reason)
                put("it_manager_name", incident.itManagerName)
                put("created_by", incident.createdBy)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/system_incidents")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException(parseErrorMessage(responseBody, "Failed to submit system incident (Code: ${response.code})"))
                )
            }

            val jsonArray = JSONArray(responseBody)
            val resultObj = if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else payload
            Result.success(resultObj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSystemIncidents(
        accessToken: String,
        centreId: String? = null
    ): Result<List<SystemIncident>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val urlBuilder = StringBuilder("${baseUrl()}/rest/v1/system_incidents?select=*&order=created_at.desc")
            if (!centreId.isNullOrBlank()) {
                urlBuilder.append("&centre_id=eq.$centreId")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException(parseErrorMessage(responseBody, "Failed to fetch incidents"))
                )
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<SystemIncident>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(parseSystemIncidentJson(obj))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSystemIncidentJson(json: JSONObject): SystemIncident {
        val incidentTimeRaw = json.optString("incident_time")
        val timestampMillis = try {
            java.time.Instant.parse(incidentTimeRaw).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val displayTime = try {
            val zdt = java.time.Instant.ofEpochMilli(timestampMillis).atZone(java.time.ZoneId.systemDefault())
            zdt.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (_: Exception) {
            "10:00 AM"
        }

        return SystemIncident(
            id = json.optString("id"),
            submissionId = json.optString("submission_id"),
            centreId = json.optString("centre_id").takeIf { it.isNotBlank() },
            examSessionId = json.optString("exam_session_id").takeIf { it.isNotBlank() },
            stateName = json.optString("state_name"),
            districtName = json.optString("district_name"),
            centreCode = json.optString("centre_code"),
            centreName = json.optString("centre_name"),
            examDate = json.optString("exam_date"),
            examName = json.optString("exam_name"),
            shiftName = json.optString("shift_name"),
            lanName = json.optString("lan_name"),
            macAddress = json.optString("mac_address"),
            ipAddress = json.optString("ip_address"),
            lockedSystemNumber = json.optString("locked_system_number"),
            newlyAllocatedSystemNumber = json.optString("newly_allocated_system_number").takeIf { it.isNotBlank() && it != "null" },
            candidateShifted = json.optBoolean("candidate_shifted", false),
            incidentTime = displayTime,
            incidentTimestampMillis = timestampMillis,
            reason = json.optString("reason"),
            itManagerName = json.optString("it_manager_name"),
            createdBy = json.optString("created_by"),
            syncStatus = "SYNCED"
        )
    }
}
