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

    fun getAuthBearer(accessToken: String): String {
        val trimmed = accessToken.trim()
        if (trimmed.isBlank() ||
            trimmed.startsWith("offline_") ||
            trimmed.split(".").size != 3
        ) {
            return ""
        }
        return trimmed
    }

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
     * Calls the server-side employee-login Edge Function first (resolves any Employee ID
     * regardless of whether the user registered with Email, Google, or Employee ID),
     * and falls back to legacy internal auth email lookup only if the Edge Function is 404.
     */
    suspend fun login(
        employeeId: String,
        password: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val normalized = SupabaseConfig.normalizeEmployeeId(employeeId)

            val edgeBody = JSONObject().apply {
                put("employee_id", normalized)
                put("password", password)
            }
            val edgeRequest = Request.Builder()
                .url("${baseUrl()}/functions/v1/employee-login")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .addHeader("Content-Type", "application/json")
                .post(edgeBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val edgeResponse = okHttpClient.newCall(edgeRequest).execute()
            val edgeRespBody = edgeResponse.body?.string() ?: ""

            if (edgeResponse.isSuccessful) {
                val json = JSONObject(edgeRespBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")
                val resolvedEmpId = json.optString("employee_id", normalized).ifBlank { normalized }
                return@withContext Result.success(
                    AuthSessionResponse(
                        accessToken = accessToken,
                        refreshToken = refreshToken.takeIf { it.isNotBlank() },
                        userId = userId,
                        employeeId = resolvedEmpId
                    )
                )
            }

            if (edgeResponse.code == 401 || edgeResponse.code == 403 || edgeResponse.code == 400) {
                val parsed = parseErrorMessage(edgeRespBody, "Invalid Employee ID or password.")
                return@withContext Result.failure(IOException(parsed))
            }

            val isEdgeUnavailable = edgeResponse.code == 404 ||
                edgeRespBody.contains("NOT_FOUND", ignoreCase = true) ||
                edgeRespBody.contains("Requested function was not found", ignoreCase = true)
            if (!isEdgeUnavailable) {
                return@withContext Result.failure(
                    IOException(parseErrorMessage(edgeRespBody, "Employee login failed (${edgeResponse.code})"))
                )
            }

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
                            employeeId = normalized
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
     * Native Supabase Auth Google ID-token sign-in (with cryptographic raw nonce verification).
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
                put("nonce", rawNonce)
            }
            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val parsed = parseErrorMessage(responseBody, "Google authentication failed (${response.code})")
                return@withContext Result.failure(IOException(parsed))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val userObj = json.optJSONObject("user")
            val userId = userObj?.optString("id").orEmpty()
            if (accessToken.isBlank() || userId.isBlank()) {
                return@withContext Result.failure(IOException("Supabase did not return a valid session for Google sign-in."))
            }

            Result.success(
                AuthSessionResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken.takeIf { it.isNotBlank() },
                    userId = userId,
                    employeeId = ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send password reset email via native Supabase Auth /auth/v1/recover.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("email", email.trim().lowercase())
            }
            val request = Request.Builder()
                .url("${baseUrl()}/auth/v1/recover")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${anonKey()}")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 429) {
                    return@withContext Result.failure(IOException("Too many reset emails requested. Please try again later or use 'Contact Administrator'."))
                }
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to send reset email (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Complete or update profile details securely for Google / first-time users via RPC complete_my_profile().
     */
    suspend fun completeMyProfile(
        accessToken: String,
        employeeId: String,
        fullName: String,
        mobile: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val normalizedEmp = SupabaseConfig.normalizeEmployeeId(employeeId)
            val normalizedMobile = SupabaseConfig.normalizeMobile(mobile)
            val body = JSONObject().apply {
                put("p_employee_id", normalizedEmp)
                put("p_full_name", fullName.trim())
                put("p_mobile", normalizedMobile)
            }
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/complete_my_profile")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val isMissingRpc = response.code == 404 ||
                    responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("Could not find the function", ignoreCase = true)
                if (isMissingRpc) {
                    val userId = extractUserIdFromJwt(bearer)
                        ?: return@withContext Result.failure(IOException("401 Authentication required"))
                    val existing = fetchProfileById(bearer, userId).getOrNull()
                    return@withContext if (existing == null) {
                        createProfile(
                            bearer,
                            UserProfile(
                                id = userId,
                                employeeId = normalizedEmp,
                                fullName = fullName.trim(),
                                mobile = normalizedMobile,
                                appRole = "employee",
                                isActive = true
                            )
                        )
                    } else {
                        updateProfile(bearer, userId, fullName.trim(), normalizedMobile, existing.profilePhotoPath)
                    }
                }
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to save profile (${response.code})")))
            }

            Result.success(parseProfileJson(JSONObject(responseBody)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign up with an explicit auth email (used for Google Sign-In bridge) and normalized Employee ID.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        employeeId: String,
        fullName: String,
        mobile: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
            val jsonBody = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
                put("data", JSONObject().apply {
                    put("employee_id", normalizedEmpId)
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
                if (errorMsg.contains("already registered", ignoreCase = true) ||
                    errorMsg.contains("User already exists", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Account is already registered. Kindly sign in."))
                }
                if (errorMsg.contains("Database error saving new user", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IOException("Employee ID '$normalizedEmpId' may already be registered or profile fields failed validation.")
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
                    employeeId = normalizedEmpId
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Log in with an explicit auth email and password to obtain a Supabase Auth session.
     */
    suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<AuthSessionResponse> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val jsonBody = JSONObject().apply {
                put("email", email.trim().lowercase())
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

            if (!response.isSuccessful) {
                val errMsg = parseErrorMessage(responseBody, "Login failed")
                return@withContext Result.failure(IOException(errMsg))
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
                    userId = userId,
                    employeeId = ""
                )
            )
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
            val bearer = getAuthBearer(accessToken)
            val candidates = SupabaseConfig.mobileFormatCandidates(profile.mobile)
                .ifEmpty { listOf(profile.mobile.trim()) }

            var lastError = "Failed to create profile"
            for (mobileCandidate in candidates) {
                val body = JSONObject().apply {
                    put("id", profile.id)
                    put("employee_id", SupabaseConfig.normalizeEmployeeId(profile.employeeId))
                    put("full_name", profile.fullName.trim())
                    put("mobile", mobileCandidate)
                    if (!profile.profilePhotoPath.isNullOrBlank()) {
                        put("profile_photo_path", profile.profilePhotoPath)
                    }
                    put("app_role", "employee")
                    put("is_active", true)
                }

                val request = Request.Builder()
                    .url("${baseUrl()}/rest/v1/profiles")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer $bearer")
                    .addHeader("Prefer", "return=representation")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonArray = JSONArray(responseBody)
                    val created = jsonArray.getJSONObject(0)
                    return@withContext Result.success(parseProfileJson(created))
                }

                val errorMsg = parseErrorMessage(responseBody, "Failed to create profile")
                if (errorMsg.contains("duplicate key", ignoreCase = true) ||
                    errorMsg.contains("profiles_employee_id_unique", ignoreCase = true)
                ) {
                    return@withContext Result.failure(IOException("Employee ID is already in use by another account."))
                }
                lastError = errorMsg
                if (!errorMsg.contains("profiles_mobile_check", ignoreCase = true)) {
                    return@withContext Result.failure(IOException(errorMsg))
                }
            }
            Result.failure(IOException(lastError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update safe profile fields (Full Name, Mobile, Photo Path).
     * Tries canonical E.164 first, then RPC update_my_profile, then legacy mobile formats if a legacy constraint rejects E.164.
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
            val bearer = getAuthBearer(accessToken)
            val candidates = SupabaseConfig.mobileFormatCandidates(mobile)
                .ifEmpty { listOf(mobile.trim()) }

            var lastError = "Failed to update profile"
            for (idx in candidates.indices) {
                val mobileCandidate = candidates[idx]
                val body = JSONObject().apply {
                    put("full_name", fullName.trim())
                    put("mobile", mobileCandidate)
                    if (photoPath != null) {
                        put("profile_photo_path", photoPath)
                    }
                }

                val request = Request.Builder()
                    .url("${baseUrl()}/rest/v1/profiles?id=eq.$userId")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer $bearer")
                    .addHeader("Prefer", "return=representation")
                    .patch(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonArray = JSONArray(responseBody)
                    if (jsonArray.length() > 0) {
                        return@withContext Result.success(parseProfileJson(jsonArray.getJSONObject(0)))
                    }
                    val fetched = fetchProfileById(bearer, userId).getOrNull()
                    if (fetched != null) {
                        return@withContext Result.success(fetched)
                    }
                    return@withContext Result.failure(IOException("Profile update returned empty response"))
                }

                val errorMsg = parseErrorMessage(responseBody, "Failed to update profile (${response.code})")
                lastError = errorMsg
                val isMobileCheckError = errorMsg.contains("profiles_mobile_check", ignoreCase = true) ||
                    responseBody.contains("profiles_mobile_check", ignoreCase = true)

                if (idx == 0 && isMobileCheckError) {
                    // Try RPC update_my_profile if migration 012 is applied
                    val rpcRes = tryUpdateProfileViaRpc(bearer, fullName.trim(), mobileCandidate, photoPath)
                    if (rpcRes.isSuccess) return@withContext rpcRes
                }

                if (!isMobileCheckError) {
                    return@withContext Result.failure(IOException(errorMsg))
                }
            }
            Result.failure(IOException(lastError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryUpdateProfileViaRpc(
        bearer: String,
        fullName: String,
        mobile: String,
        photoPath: String?
    ): Result<UserProfile> {
        val body = JSONObject().apply {
            put("p_full_name", fullName)
            put("p_mobile", mobile)
            if (photoPath != null) put("p_profile_photo_path", photoPath)
        }
        val req = Request.Builder()
            .url("${baseUrl()}/rest/v1/rpc/update_my_profile")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val resp = okHttpClient.newCall(req).execute()
        val respBody = resp.body?.string() ?: ""
        return if (resp.isSuccessful && respBody.isNotBlank()) {
            Result.success(parseProfileJson(JSONObject(respBody)))
        } else {
            Result.failure(IOException(parseErrorMessage(respBody, "RPC update_my_profile failed")))
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
     * Falls back cleanly to 9-param signature (003) and PostgREST tables when schema/RPC mismatches occur.
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
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }

            fun buildRpcBody(includeExtendedParams: Boolean): JSONObject = JSONObject().apply {
                put("p_task_id", taskId)
                put("p_response_type", responseType)
                if (responseValue != null) put("p_response_value", responseValue)
                if (percentage != null) put("p_percentage", percentage)
                if (numeric1 != null) put("p_numeric_1", numeric1)
                if (numeric2 != null) put("p_numeric_2", numeric2)
                if (text != null) put("p_text", text)
                if (lanNumber != null) put("p_lan_number", lanNumber)
                if (idempotencyKey != null) put("p_idempotency_key", idempotencyKey)
                if (includeExtendedParams) {
                    if (deviceTimestamp != null) put("p_device_timestamp", deviceTimestamp)
                    if (dynamicValues != null && dynamicValues.isNotEmpty()) {
                        put("p_dynamic_values", JSONObject(dynamicValues))
                    }
                }
            }

            fun executeRpc(body: JSONObject): Pair<Int, String> {
                val request = Request.Builder()
                    .url("${baseUrl()}/rest/v1/rpc/submit_task_response")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer $bearer")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val respStr = response.body?.string() ?: ""
                return response.code to respStr
            }

            var (code, responseBody) = executeRpc(buildRpcBody(includeExtendedParams = true))

            if (code !in 200..299) {
                if (code == 401) {
                    val parsed = parseErrorMessage(responseBody, "401 Unauthorized")
                    val msg = if (parsed.contains("401")) parsed else "$parsed (401)"
                    return@withContext Result.failure(IOException(msg))
                }

                val isMissingOrMismatchedRpc = code == 404 ||
                    code == 300 ||
                    responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("PGRST203", ignoreCase = true) ||
                    responseBody.contains("Could not find the function", ignoreCase = true) ||
                    responseBody.contains("Could not choose the best candidate", ignoreCase = true)

                if (isMissingOrMismatchedRpc) {
                    val retry = executeRpc(buildRpcBody(includeExtendedParams = false))
                    code = retry.first
                    responseBody = retry.second
                    if (code in 200..299) {
                        return@withContext Result.success(JSONObject(responseBody))
                    }
                }

                val shouldUseRestFallback = code == 404 ||
                    code == 300 ||
                    responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("PGRST203", ignoreCase = true) ||
                    responseBody.contains("Could not find the function", ignoreCase = true) ||
                    responseBody.contains("Could not choose the best candidate", ignoreCase = true) ||
                    responseBody.contains("does not accept responses", ignoreCase = true) ||
                    responseBody.contains("not authorized", ignoreCase = true) ||
                    responseBody.contains("User profile not found", ignoreCase = true)

                if (shouldUseRestFallback) {
                    Log.w(TAG, "RPC submit_task_response unavailable or blocked ($code: $responseBody); falling back to PostgREST")
                    return@withContext submitTaskResponseViaRestFallback(
                        bearer = bearer,
                        taskId = taskId,
                        responseType = responseType,
                        responseValue = responseValue,
                        percentage = percentage,
                        numeric1 = numeric1,
                        numeric2 = numeric2,
                        text = text,
                        lanNumber = lanNumber,
                        idempotencyKey = idempotencyKey,
                        deviceTimestamp = deviceTimestamp,
                        dynamicValues = dynamicValues
                    )
                }

                return@withContext Result.failure(
                    IOException(parseErrorMessage(responseBody, "Submit response failed ($code)"))
                )
            }

            Result.success(JSONObject(responseBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun submitTaskResponseViaRestFallback(
        bearer: String,
        taskId: String,
        responseType: String,
        responseValue: String?,
        percentage: Int?,
        numeric1: Double?,
        numeric2: Double?,
        text: String?,
        lanNumber: String?,
        idempotencyKey: String?,
        deviceTimestamp: String?,
        dynamicValues: Map<String, String>? = null
    ): Result<JSONObject> {
        val userId = extractUserIdFromJwt(bearer)
            ?: return Result.failure(IOException("401 Authentication required: invalid user session"))

        // 1. Check idempotency key if present
        if (!idempotencyKey.isNullOrBlank()) {
            val encodedKey = java.net.URLEncoder.encode(idempotencyKey, "UTF-8")
            val checkReq = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_responses?idempotency_key=eq.$encodedKey&select=id&limit=1")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .get()
                .build()
            runCatching {
                okHttpClient.newCall(checkReq).execute().use { resp ->
                    val b = resp.body?.string() ?: "[]"
                    if (resp.isSuccessful) {
                        val arr = JSONArray(b)
                        if (arr.length() > 0) {
                            val existingId = arr.getJSONObject(0).optString("id", idempotencyKey)
                            return Result.success(JSONObject().apply {
                                put("success", true)
                                put("response_id", existingId)
                                put("task_id", taskId)
                                put("is_duplicate", true)
                            })
                        }
                    }
                }
            }
        }

        // 2. Fetch employee_id & full_name from profiles
        var employeeId = "EMP"
        var fullName: String? = null
        val profReq = Request.Builder()
            .url("${baseUrl()}/rest/v1/profiles?id=eq.$userId&select=employee_id,full_name&limit=1")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(profReq).execute().use { resp ->
                val b = resp.body?.string() ?: "[]"
                if (resp.isSuccessful) {
                    val obj = JSONArray(b).optJSONObject(0)
                    if (obj != null) {
                        employeeId = obj.optString("employee_id").ifBlank { "EMP" }
                        fullName = obj.optString("full_name").takeIf { it.isNotBlank() && !it.equals("null", true) }
                    }
                }
            }
        }

        // 3. Insert into public.task_responses
        val insertBody = JSONObject().apply {
            put("task_id", taskId)
            put("user_id", userId)
            put("employee_id", employeeId)
            if (!fullName.isNullOrBlank()) put("employee_name", fullName)
            if (!lanNumber.isNullOrBlank()) put("lan_number", lanNumber)
            put("response_type", responseType)
            if (!responseValue.isNullOrBlank()) put("response_value", responseValue)
            if (percentage != null) put("percentage_value", percentage)
            if (numeric1 != null) put("numeric_value_1", numeric1)
            if (numeric2 != null) put("numeric_value_2", numeric2)
            if (!text.isNullOrBlank()) put("text_value", text)
            if (!idempotencyKey.isNullOrBlank()) put("idempotency_key", idempotencyKey)
            if (!deviceTimestamp.isNullOrBlank()) put("device_timestamp", deviceTimestamp)
            if (!dynamicValues.isNullOrEmpty()) put("dynamic_values", JSONObject(dynamicValues))
        }

        val insertReq = Request.Builder()
            .url("${baseUrl()}/rest/v1/task_responses")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(insertBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val insertResp = okHttpClient.newCall(insertReq).execute()
        val insertRespBody = insertResp.body?.string() ?: ""

        if (!insertResp.isSuccessful) {
            if (insertResp.code == 409) {
                return Result.success(JSONObject().apply {
                    put("success", true)
                    put("response_id", idempotencyKey ?: java.util.UUID.randomUUID().toString())
                    put("task_id", taskId)
                    put("is_duplicate", true)
                })
            }
            val parsed = parseErrorMessage(insertRespBody, "Failed to insert response (${insertResp.code})")
            val msg = if (insertResp.code == 401 && !parsed.contains("401")) "$parsed (401)" else parsed
            return Result.failure(IOException(msg))
        }

        val createdObj = runCatching { JSONArray(insertRespBody).optJSONObject(0) }.getOrNull()
        val responseId = createdObj?.optString("id")?.takeIf { it.isNotBlank() }
            ?: idempotencyKey
            ?: java.util.UUID.randomUUID().toString()

        // 4. Inspect task completion policy & update tasks table status best-effort
        var targetPct = 100
        var completionPolicy = "SINGLE_ASSIGNEE"
        var hasMultiLanTargets = false
        val taskReq = Request.Builder()
            .url("${baseUrl()}/rest/v1/tasks?id=eq.$taskId&select=completion_policy,target_percentage,lan_target_list&limit=1")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(taskReq).execute().use { resp ->
                val b = resp.body?.string() ?: "[]"
                if (resp.isSuccessful) {
                    val obj = JSONArray(b).optJSONObject(0)
                    if (obj != null) {
                        completionPolicy = obj.optString("completion_policy", "SINGLE_ASSIGNEE")
                        targetPct = obj.optInt("target_percentage", 100)
                        val lans = obj.optJSONArray("lan_target_list")
                        hasMultiLanTargets = lans != null && lans.length() > 0
                    }
                }
            }
        }

        val isPositiveCompletion = (percentage != null && percentage >= targetPct) ||
            responseValue.equals("YES", ignoreCase = true) ||
            responseValue.equals("COMPLETED", ignoreCase = true)

        val newStatus = when {
            !lanNumber.isNullOrBlank() && completionPolicy == "ALL_SELECTED_LANS" && hasMultiLanTargets -> "in_progress"
            isPositiveCompletion && completionPolicy == "CREATOR_CONFIRMATION" -> "pending_confirmation"
            isPositiveCompletion -> "completed"
            else -> "in_progress"
        }

        val patchBody = JSONObject().apply {
            put("status", newStatus)
            put("last_progress_update", deviceTimestamp ?: java.time.Instant.now().toString())
            if (percentage != null) {
                put("current_progress", percentage)
            } else if (newStatus == "completed") {
                put("current_progress", 100)
            }
        }
        val patchReq = Request.Builder()
            .url("${baseUrl()}/rest/v1/tasks?id=eq.$taskId")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(patchBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching { okHttpClient.newCall(patchReq).execute().close() }

        return Result.success(JSONObject().apply {
            put("success", true)
            put("response_id", responseId)
            put("task_id", taskId)
            put("task_status", newStatus)
        })
    }

    /**
     * Fetch task responses from Supabase PostgREST for real-time visibility across users.
     */
    suspend fun fetchTaskResponses(
        accessToken: String,
        taskId: String? = null
    ): Result<List<com.example.core.model.TaskResponseItem>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }

            val url = if (!taskId.isNullOrBlank()) {
                val encoded = java.net.URLEncoder.encode(taskId, "UTF-8")
                "${baseUrl()}/rest/v1/task_responses?task_id=eq.$encoded&select=*&order=submitted_at.desc&limit=200"
            } else {
                "${baseUrl()}/rest/v1/task_responses?select=*&order=submitted_at.desc&limit=500"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val parsed = parseErrorMessage(responseBody, "Failed to fetch task responses (${response.code})")
                val msg = if (response.code == 401 && !parsed.contains("401")) "$parsed (401)" else parsed
                return@withContext Result.failure(IOException(msg))
            }

            val array = JSONArray(responseBody)
            val list = mutableListOf<com.example.core.model.TaskResponseItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                fun optClean(k: String): String? {
                    if (!obj.has(k) || obj.isNull(k)) return null
                    return obj.optString(k, "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
                }

                val dynMap = mutableMapOf<String, String>()
                val dynObj = obj.optJSONObject("dynamic_values")
                if (dynObj != null) {
                    dynObj.keys().forEach { key ->
                        val v = dynObj.optString(key, "")
                        if (v.isNotBlank() && !v.equals("null", true)) dynMap[key] = v
                    }
                }

                list.add(
                    com.example.core.model.TaskResponseItem(
                        id = obj.optString("id"),
                        taskId = obj.optString("task_id"),
                        userId = obj.optString("user_id"),
                        employeeId = obj.optString("employee_id"),
                        employeeName = optClean("employee_name"),
                        lanNumber = optClean("lan_number"),
                        responseType = runCatching {
                            com.example.core.model.ResponseType.valueOf(obj.optString("response_type"))
                        }.getOrDefault(com.example.core.model.ResponseType.COMPLETED_NOT_COMPLETED),
                        responseValue = optClean("response_value"),
                        percentageValue = if (obj.has("percentage_value") && !obj.isNull("percentage_value")) obj.getInt("percentage_value") else null,
                        numericValue1 = if (obj.has("numeric_value_1") && !obj.isNull("numeric_value_1")) obj.getDouble("numeric_value_1") else null,
                        numericValue2 = if (obj.has("numeric_value_2") && !obj.isNull("numeric_value_2")) obj.getDouble("numeric_value_2") else null,
                        textValue = optClean("text_value"),
                        dynamicValues = dynMap,
                        submittedAt = obj.optString("submitted_at"),
                        isPendingSync = false,
                        isLate = obj.optBoolean("is_late", false),
                        delaySeconds = obj.optInt("delay_seconds", 0)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Acknowledge task alarm via RPC function: public.acknowledge_task_alarm()
     * Falls back cleanly to PostgREST tables if RPC is unavailable.
     */
    suspend fun acknowledgeTaskAlarm(
        accessToken: String,
        taskId: String,
        eventId: String,
        deviceTimestamp: String = java.time.Instant.now().toString()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }

            val body = JSONObject().apply {
                put("p_task_id", taskId)
                put("p_event_id", eventId)
                put("p_device_timestamp", deviceTimestamp)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/acknowledge_task_alarm")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    val parsed = parseErrorMessage(responseBody, "401 Unauthorized")
                    val msg = if (parsed.contains("401")) parsed else "$parsed (401)"
                    return@withContext Result.failure(IOException(msg))
                }
                val isMissingRpc = response.code == 404 ||
                    response.code == 300 ||
                    responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("PGRST203", ignoreCase = true) ||
                    responseBody.contains("Could not find the function", ignoreCase = true) ||
                    responseBody.contains("Could not choose the best candidate", ignoreCase = true) ||
                    responseBody.contains("Not authorized", ignoreCase = true)
                if (isMissingRpc) {
                    val userId = extractUserIdFromJwt(bearer)
                        ?: return@withContext Result.failure(IOException("401 Authentication required"))
                    var employeeId = "EMP"
                    runCatching {
                        val profReq = Request.Builder()
                            .url("${baseUrl()}/rest/v1/profiles?id=eq.$userId&select=employee_id&limit=1")
                            .addHeader("apikey", anonKey())
                            .addHeader("Authorization", "Bearer $bearer")
                            .get()
                            .build()
                        okHttpClient.newCall(profReq).execute().use { r ->
                            val b = r.body?.string() ?: "[]"
                            if (r.isSuccessful) {
                                employeeId = JSONArray(b).optJSONObject(0)?.optString("employee_id")?.ifBlank { "EMP" } ?: "EMP"
                            }
                        }
                    }
                    val ackPayload = JSONObject().apply {
                        put("task_id", taskId)
                        put("user_id", userId)
                        put("employee_id", employeeId)
                        put("event_id", eventId)
                        put("device_timestamp", deviceTimestamp)
                    }
                    val ackReq = Request.Builder()
                        .url("${baseUrl()}/rest/v1/task_acknowledgements")
                        .addHeader("apikey", anonKey())
                        .addHeader("Authorization", "Bearer $bearer")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                        .post(ackPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    val ackResp = okHttpClient.newCall(ackReq).execute()
                    val ackBody = ackResp.body?.string() ?: ""
                    if (!ackResp.isSuccessful && ackResp.code != 409) {
                        val parsed = parseErrorMessage(ackBody, "Acknowledge alarm failed (${ackResp.code})")
                        return@withContext Result.failure(IOException(parsed))
                    }
                    val patchReq = Request.Builder()
                        .url("${baseUrl()}/rest/v1/tasks?id=eq.$taskId")
                        .addHeader("apikey", anonKey())
                        .addHeader("Authorization", "Bearer $bearer")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .patch(JSONObject().apply {
                            put("is_acknowledged", true)
                            put("acknowledged_at", deviceTimestamp)
                        }.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    runCatching { okHttpClient.newCall(patchReq).execute().close() }
                    return@withContext Result.success(Unit)
                }
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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

    private fun extractUserIdFromJwt(bearer: String): String? {
        return try {
            val parts = bearer.split(".")
            if (parts.size < 2) return null
            var b64 = parts[1].replace('-', '+').replace('_', '/')
            while (b64.length % 4 != 0) b64 += "="
            val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            json.optString("sub").takeIf { SupabaseConfig.isValidUuid(it) }
        } catch (_: Throwable) {
            try {
                val parts = bearer.split(".")
                if (parts.size < 2) return null
                var b64 = parts[1].replace('-', '+').replace('_', '/')
                while (b64.length % 4 != 0) b64 += "="
                val decoded = java.util.Base64.getDecoder().decode(b64)
                val json = JSONObject(String(decoded, Charsets.UTF_8))
                json.optString("sub").takeIf { SupabaseConfig.isValidUuid(it) }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Atomically creates task on Supabase with idempotency via client_operation_id.
     * Falls back cleanly to PostgREST table inserts if the create_task_atomic RPC has not been deployed.
     */
    suspend fun createTaskAtomic(
        accessToken: String,
        taskPayload: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }

            val requestBody = JSONObject().apply {
                put("p_task", taskPayload)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/create_task_atomic")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isMissingRpc = response.code == 404 ||
                    responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("Could not find the function", ignoreCase = true)
                if (isMissingRpc) {
                    Log.w(TAG, "RPC create_task_atomic unavailable ($responseBody); falling back to PostgREST tables")
                    return@withContext createTaskViaRestFallback(bearer, taskPayload)
                }
                val parsed = parseErrorMessage(responseBody, "Failed to create task on server (${response.code})")
                val msg = if (response.code == 401 && !parsed.contains("401")) "$parsed (401)" else parsed
                return@withContext Result.failure(IOException(msg))
            }

            val json = JSONObject(responseBody)
            Result.success(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createTaskViaRestFallback(
        bearer: String,
        taskPayload: JSONObject
    ): Result<JSONObject> {
        val creatorId = extractUserIdFromJwt(bearer)
            ?: return Result.failure(IOException("401 Authentication required: invalid user session"))

        // 1. Ensure a valid UUID group exists on server
        var groupId = taskPayload.optString("group_id").takeIf { SupabaseConfig.isValidUuid(it) }
        if (groupId == null) {
            val lookupReq = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_groups?select=id&limit=1")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .get()
                .build()
            val lookupResp = okHttpClient.newCall(lookupReq).execute()
            val lookupBody = lookupResp.body?.string() ?: "[]"
            if (lookupResp.isSuccessful) {
                val arr = JSONArray(lookupBody)
                if (arr.length() > 0) {
                    groupId = arr.getJSONObject(0).optString("id").takeIf { SupabaseConfig.isValidUuid(it) }
                }
            }
            if (groupId == null) {
                val newGroupId = java.util.UUID.randomUUID().toString()
                val grpPayload = JSONObject().apply {
                    put("id", newGroupId)
                    put("title", "General Operations")
                    put("code", "GEN-OPS")
                    put("description", "Default operations group for standalone tasks")
                    put("status", "active")
                    put("visibility", "everyone")
                    put("allow_self_join", true)
                    put("created_by", creatorId)
                }
                val createGrpReq = Request.Builder()
                    .url("${baseUrl()}/rest/v1/task_groups")
                    .addHeader("apikey", anonKey())
                    .addHeader("Authorization", "Bearer $bearer")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(grpPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val createGrpResp = okHttpClient.newCall(createGrpReq).execute()
                val createGrpBody = createGrpResp.body?.string() ?: ""
                if (!createGrpResp.isSuccessful) {
                    val msg = parseErrorMessage(createGrpBody, "Failed to ensure task group (${createGrpResp.code})")
                    return Result.failure(IOException(msg))
                }
                val createdArr = runCatching { JSONArray(createGrpBody) }.getOrNull()
                groupId = createdArr?.optJSONObject(0)?.optString("id")?.takeIf { SupabaseConfig.isValidUuid(it) }
                    ?: newGroupId
            }
        }

        // 2. Insert into public.tasks
        val taskId = taskPayload.optString("id").takeIf { SupabaseConfig.isValidUuid(it) }
            ?: java.util.UUID.randomUUID().toString()

        fun buildInsertBody(includeClientOpId: Boolean): JSONObject = JSONObject().apply {
            put("id", taskId)
            put("group_id", groupId)
            val shiftId = taskPayload.optString("shift_id")
            if (SupabaseConfig.isValidUuid(shiftId)) put("shift_id", shiftId)
            put("title", taskPayload.optString("title", "Untitled Task"))
            if (taskPayload.has("description")) put("description", taskPayload.opt("description"))
            if (taskPayload.has("location")) put("location", taskPayload.opt("location"))
            if (taskPayload.has("starts_at")) put("starts_at", taskPayload.opt("starts_at"))
            if (taskPayload.has("due_at")) put("due_at", taskPayload.opt("due_at"))
            put("priority", taskPayload.optString("priority", "normal"))
            put("status", "open")
            put("visibility", taskPayload.optString("visibility", "everyone"))
            put("allow_self_join", taskPayload.optBoolean("allow_self_join", true))
            put("join_requires_approval", taskPayload.optBoolean("join_requires_approval", true))
            put("notify_creator_on_join", taskPayload.optBoolean("notify_creator_on_join", true))
            put("requires_completion_confirmation", taskPayload.optBoolean("requires_completion_confirmation", false))
            if (taskPayload.has("max_participants")) put("max_participants", taskPayload.opt("max_participants"))
            put("created_by", creatorId)
            put("task_type", taskPayload.optString("task_type", "SIMPLE"))
            put("importance", taskPayload.optString("importance", "NORMAL"))
            put("urgency_type", taskPayload.optString("urgency_type", "BEFORE_DEADLINE"))
            if (taskPayload.has("execution_time")) put("execution_time", taskPayload.opt("execution_time"))
            if (taskPayload.has("target_percentage")) put("target_percentage", taskPayload.opt("target_percentage"))
            if (taskPayload.has("repeat_interval_minutes")) put("repeat_interval_minutes", taskPayload.opt("repeat_interval_minutes"))
            put("alarm_type", taskPayload.optString("alarm_type", "SOUND_ALERT"))
            if (taskPayload.has("advance_reminders_minutes")) put("advance_reminders_minutes", taskPayload.optJSONArray("advance_reminders_minutes"))
            put("remind_until_completed", taskPayload.optBoolean("remind_until_completed", false))
            if (taskPayload.has("overdue_reminder_interval_minutes")) put("overdue_reminder_interval_minutes", taskPayload.opt("overdue_reminder_interval_minutes"))
            if (taskPayload.has("max_overdue_reminders")) put("max_overdue_reminders", taskPayload.opt("max_overdue_reminders"))
            put("response_type", taskPayload.optString("response_type", "COMPLETED_NOT_COMPLETED"))
            if (taskPayload.has("lan_target_list")) put("lan_target_list", taskPayload.optJSONArray("lan_target_list"))
            put("completion_policy", taskPayload.optString("completion_policy", "SINGLE_ASSIGNEE"))
            if (taskPayload.has("response_form_schema")) put("response_form_schema", taskPayload.opt("response_form_schema"))
            if (includeClientOpId && taskPayload.has("client_operation_id")) {
                put("client_operation_id", taskPayload.optString("client_operation_id"))
            }
        }

        fun postTask(bodyObj: JSONObject): okhttp3.Response {
            val req = Request.Builder()
                .url("${baseUrl()}/rest/v1/tasks")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(bodyObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            return okHttpClient.newCall(req).execute()
        }

        var taskResp = postTask(buildInsertBody(includeClientOpId = true))
        var taskRespBody = taskResp.body?.string() ?: ""
        if (!taskResp.isSuccessful && taskRespBody.contains("client_operation_id", ignoreCase = true)) {
            taskResp = postTask(buildInsertBody(includeClientOpId = false))
            taskRespBody = taskResp.body?.string() ?: ""
        }

        if (!taskResp.isSuccessful) {
            val parsed = parseErrorMessage(taskRespBody, "Failed to insert task (${taskResp.code})")
            val msg = if (taskResp.code == 401 && !parsed.contains("401")) "$parsed (401)" else parsed
            return Result.failure(IOException(msg))
        }

        val insertedTaskObj = runCatching { JSONArray(taskRespBody).optJSONObject(0) }.getOrNull()
        val confirmedTaskId = insertedTaskObj?.optString("id")?.takeIf { SupabaseConfig.isValidUuid(it) } ?: taskId

        // 3. Insert participants (creator + assigned employees)
        val participantsArr = JSONArray()
        participantsArr.put(JSONObject().apply {
            put("task_id", confirmedTaskId)
            put("user_id", creatorId)
            put("status", "active")
            put("source", "creator")
        })
        val assignedUsers = taskPayload.optJSONArray("assigned_user_ids")
        if (assignedUsers != null) {
            val seen = mutableSetOf(creatorId)
            for (i in 0 until assignedUsers.length()) {
                val uid = assignedUsers.optString(i)
                if (SupabaseConfig.isValidUuid(uid) && seen.add(uid)) {
                    participantsArr.put(JSONObject().apply {
                        put("task_id", confirmedTaskId)
                        put("user_id", uid)
                        put("status", "active")
                        put("source", "assigned")
                        put("assigned_by", creatorId)
                    })
                }
            }
        }
        val partReq = Request.Builder()
            .url("${baseUrl()}/rest/v1/task_participants")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(participantsArr.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching { okHttpClient.newCall(partReq).execute().close() }

        return Result.success(JSONObject().apply {
            put("success", true)
            put("task_id", confirmedTaskId)
            if (insertedTaskObj != null) put("task", insertedTaskObj)
        })
    }

    /**
     * Fetch all tasks visible to current user (creator, assigned, or group member) from Supabase.
     */
    suspend fun fetchTasks(accessToken: String): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val url = "${baseUrl()}/rest/v1/tasks?select=*,task_participants(*),task_role_requirements(*),task_groups(id,title),shifts(id,name)&order=created_at.desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val parsed = parseErrorMessage(responseBody, "Failed to fetch tasks (${response.code})")
                val msg = if (response.code == 401 && !parsed.contains("401")) "$parsed (401)" else parsed
                return@withContext Result.failure(IOException(msg))
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                    .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
            val bearer = getAuthBearer(accessToken)
            val body = JSONObject().apply {
                put("p_task_id", taskId)
                put("p_user_id", userId)
                if (SupabaseConfig.isValidUuid(roleId)) {
                    put("p_role_id", roleId)
                }
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/assign_user_to_task")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                if (responseBody.contains("PGRST202", ignoreCase = true) ||
                    responseBody.contains("p_task", ignoreCase = true)
                ) {
                    val legacyBody = JSONObject().apply {
                        put("p_task", taskId)
                        put("p_user", userId)
                        if (SupabaseConfig.isValidUuid(roleId)) {
                            put("p_role", roleId)
                        }
                    }
                    val legacyReq = Request.Builder()
                        .url("${baseUrl()}/rest/v1/rpc/assign_user_to_task")
                        .addHeader("apikey", anonKey())
                        .addHeader("Authorization", "Bearer $bearer")
                        .addHeader("Content-Type", "application/json")
                        .post(legacyBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                    val legacyResp = okHttpClient.newCall(legacyReq).execute()
                    if (legacyResp.isSuccessful) {
                        legacyResp.close()
                        return@withContext Result.success(Unit)
                    }
                    val legacyErrBody = legacyResp.body?.string() ?: ""
                    return@withContext Result.failure(IOException(parseErrorMessage(legacyErrBody, "Failed to assign user to task")))
                }
                return@withContext Result.failure(IOException(parseErrorMessage(responseBody, "Failed to assign user to task")))
            }
            response.close()
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                if (SupabaseConfig.isValidUuid(incident.centreId)) put("centre_id", incident.centreId)
                if (SupabaseConfig.isValidUuid(incident.examSessionId)) put("exam_session_id", incident.examSessionId)
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
                if (SupabaseConfig.isValidUuid(incident.createdBy)) put("created_by", incident.createdBy)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/system_incidents")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
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

    /**
     * Delete a system incident on Supabase PostgREST.
     */
    suspend fun deleteSystemIncident(accessToken: String, incidentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val encodedId = java.net.URLEncoder.encode(incidentId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/system_incidents?id=eq.$encodedId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(body, "Failed to delete incident on server (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a task on Supabase PostgREST.
     */
    suspend fun deleteTask(accessToken: String, taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/tasks?id=eq.$encodedId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(body, "Failed to delete task on server (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upsert user profile into Supabase profiles table.
     */
    suspend fun upsertProfile(accessToken: String, profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val body = JSONObject().apply {
                put("id", profile.id)
                put("employee_id", SupabaseConfig.normalizeEmployeeId(profile.employeeId))
                put("full_name", profile.fullName)
                put("mobile", profile.mobile)
                if (!profile.profilePhotoPath.isNullOrBlank()) {
                    put("profile_photo_path", profile.profilePhotoPath)
                }
                put("app_role", profile.appRole)
                put("is_active", profile.isActive)
            }

            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/profiles")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(respBody, "Failed to upsert profile (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch active canonical work roles from Supabase.
     */
    suspend fun fetchWorkRoles(accessToken: String): Result<List<com.example.core.model.WorkRole>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/work_roles?select=*&is_active=eq.true&order=sort_order.asc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer ${getAuthBearer(accessToken)}")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(body, "Failed to fetch work roles (${response.code})")))
            }

            val array = JSONArray(body)
            val list = mutableListOf<com.example.core.model.WorkRole>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.core.model.WorkRole(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        code = obj.optString("code"),
                        description = obj.optString("description").takeIf { it.isNotBlank() && it != "null" },
                        isActive = obj.optBoolean("is_active", true),
                        sortOrder = obj.optInt("sort_order", i)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Patch task fields on Supabase PostgREST.
     */
    suspend fun updateTaskFields(
        accessToken: String,
        taskId: String,
        updates: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/tasks?id=eq.$encodedId")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(updates.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(body, "Failed to update task (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch task join requests for a specific task from Supabase.
     */
    suspend fun fetchTaskJoinRequests(
        accessToken: String,
        taskId: String
    ): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/task_join_requests?task_id=eq.$encodedId&select=*,profiles:user_id(id,employee_id,full_name,profile_photo_path)&order=created_at.desc")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(body, "Failed to fetch task join requests (${response.code})")))
            }
            val array = JSONArray(body)
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
     * Request to join a group via RPC request_group_join.
     */
    suspend fun requestGroupJoin(
        accessToken: String,
        groupId: String,
        message: String? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val body = JSONObject().apply {
                put("p_group", groupId)
                if (!message.isNullOrBlank()) put("p_message", message)
            }
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/request_group_join")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(respBody, "Failed to request group join (${response.code})")))
            }
            Result.success(JSONObject(respBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Review group join request via RPC review_group_join.
     */
    suspend fun reviewGroupJoin(
        accessToken: String,
        requestId: String,
        approve: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val body = JSONObject().apply {
                put("p_request", requestId)
                put("p_approve", approve)
            }
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/review_group_join")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(respBody, "Failed to review group join (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Request task completion confirmation via RPC request_task_completion.
     */
    suspend fun requestTaskCompletion(
        accessToken: String,
        taskId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val body = JSONObject().apply { put("p_task", taskId) }
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/request_task_completion")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(respBody, "Failed to submit completion request (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Review task completion request via RPC review_task_completion.
     */
    suspend fun reviewTaskCompletion(
        accessToken: String,
        taskId: String,
        approve: Boolean,
        comment: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkConfig()
            val bearer = getAuthBearer(accessToken)
            if (bearer.isBlank()) {
                return@withContext Result.failure(IOException("401 Authentication session expired or invalid"))
            }
            val body = JSONObject().apply {
                put("p_task", taskId)
                put("p_approve", approve)
                if (!comment.isNullOrBlank()) put("p_comment", comment)
            }
            val request = Request.Builder()
                .url("${baseUrl()}/rest/v1/rpc/review_task_completion")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer $bearer")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(respBody, "Failed to review task completion (${response.code})")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

