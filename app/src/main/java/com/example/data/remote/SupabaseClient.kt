package com.example.data.remote

import android.util.Log
import com.example.core.model.PasswordResetRequest
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
    val authUserId: String,
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
            throw IOException("Supabase is not configured. Please check your SUPABASE_URL and SUPABASE_ANON_KEY.")
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
            val internalEmail = SupabaseConfig.toInternalAuthEmail(employeeId)
            val jsonBody = JSONObject().apply {
                put("email", internalEmail)
                put("password", password)
                put("data", JSONObject().apply {
                    put("employee_id", employeeId.trim())
                    put("full_name", fullName.trim())
                    put("mobile", mobile.trim())
                })
            }

            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/signup")
                .addHeader("apikey", anonKey())
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, "Sign up failed (${response.code})")
                // Check if user already exists
                if (errorMsg.contains("already registered", ignoreCase = true) ||
                    errorMsg.contains("User already exists", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Employee ID '$employeeId' is already registered."))
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

            // Note: If email confirmations are disabled in Supabase, access_token is returned immediately.
            // If email confirmation is enabled, access_token might be null until confirmed.
            Result.success(
                AuthSessionResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    authUserId = userId,
                    employeeId = employeeId.trim()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during signUp", e)
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
            val internalEmail = SupabaseConfig.toInternalAuthEmail(employeeId)
            val jsonBody = JSONObject().apply {
                put("email", internalEmail)
                put("password", password)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey())
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody, "Login failed")
                if (response.code == 400 || errorMsg.contains("invalid", ignoreCase = true) ||
                    errorMsg.contains("credentials", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Invalid Employee ID or password."))
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token")
            val userObj = json.getJSONObject("user")
            val userId = userObj.getString("id")

            Result.success(
                AuthSessionResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    authUserId = userId,
                    employeeId = employeeId.trim()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during login", e)
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
                    authUserId = userId,
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
            // Logout locally even if network fails
            Result.success(Unit)
        }
    }

    /**
     * Fetch user profile from PostgREST by authUserId.
     */
    suspend fun fetchProfileByAuthId(
        accessToken: String,
        authUserId: String
    ): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val url = "${baseUrl()}/rest/v1/profiles?auth_user_id=eq.$authUserId&select=*"
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
            Log.e(TAG, "Error fetching profile", e)
            Result.failure(e)
        }
    }

    /**
     * Insert new profile row.
     */
    suspend fun createProfile(
        accessToken: String,
        profile: UserProfile
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("auth_user_id", profile.authUserId)
                put("employee_id", profile.employeeId.trim())
                put("full_name", profile.fullName.trim())
                put("mobile", profile.mobile.trim())
                if (!profile.profilePhotoUrl.isNullOrBlank()) {
                    put("profile_photo_url", profile.profilePhotoUrl)
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
                    errorMsg.contains("idx_profiles_employee_id", ignoreCase = true)
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
     * Update safe profile fields (Full Name, Mobile, Photo).
     */
    suspend fun updateProfile(
        accessToken: String,
        authUserId: String,
        fullName: String,
        mobile: String,
        photoUrl: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("full_name", fullName.trim())
                put("mobile", mobile.trim())
                if (photoUrl != null) {
                    put("profile_photo_url", photoUrl)
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles?auth_user_id=eq.$authUserId")
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
     * Upload photo to Supabase Storage in 'profile-photos' bucket.
     */
    suspend fun uploadProfilePhoto(
        accessToken: String,
        authUserId: String,
        imageBytes: ByteArray,
        filename: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val objectPath = "$authUserId/$filename"
            val uploadUrl = "${baseUrl()}/storage/v1/object/profile-photos/$objectPath"

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

            val publicUrl = "${baseUrl()}/storage/v1/object/public/profile-photos/$objectPath"
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit a password reset request.
     */
    suspend fun submitPasswordResetRequest(
        employeeId: String,
        mobile: String,
        message: String?
    ): Result<PasswordResetRequest> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("employee_id", employeeId.trim())
                put("mobile", mobile.trim())
                if (!message.isNullOrBlank()) {
                    put("message", message.trim())
                }
                put("status", "pending")
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/password_reset_requests")
                .addHeader("apikey", anonKey())
                .addHeader("Prefer", "return=representation")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Request submission failed")))
            }

            val jsonArray = JSONArray(responseBody)
            val created = jsonArray.getJSONObject(0)
            Result.success(parseResetRequestJson(created))
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

    // ==========================================
    // JSON PARSING UTILS
    // ==========================================

    private fun parseProfileJson(json: JSONObject): UserProfile {
        return UserProfile(
            id = json.optString("id"),
            authUserId = json.optString("auth_user_id"),
            employeeId = json.optString("employee_id"),
            fullName = json.optString("full_name"),
            mobile = json.optString("mobile"),
            profilePhotoUrl = json.optString("profile_photo_url").takeIf { it.isNotBlank() },
            appRole = json.optString("app_role", "employee"),
            isActive = json.optBoolean("is_active", true),
            mustChangePassword = json.optBoolean("must_change_password", false),
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
}
