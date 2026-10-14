package com.example.domain.repository

import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.PasswordResetRequest
import com.example.core.model.UserProfile
import com.example.data.remote.SupabaseClient
import java.io.IOException

class AdminRepository(
    private val supabaseClient: SupabaseClient,
    private val userPrefs: UserPreferencesDataStore
) {
    private suspend fun getActiveToken(): String {
        val session = userPrefs.getUserSession()
        return session.accessToken ?: throw IOException("Authentication required")
    }

    suspend fun getAllUsers(): Result<List<UserProfile>> {
        return try {
            val token = getActiveToken()
            supabaseClient.fetchAllProfiles(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleUserActiveStatus(profileId: String, newActiveState: Boolean): Result<Unit> {
        return try {
            val token = getActiveToken()
            supabaseClient.updateUserActiveStatus(token, profileId, newActiveState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPasswordResetRequests(): Result<List<PasswordResetRequest>> {
        return try {
            val token = getActiveToken()
            val requestsRes = supabaseClient.fetchPasswordResetRequests(token)
            if (requestsRes.isFailure) return requestsRes

            val requests = requestsRes.getOrThrow()
            // Optionally match employee names if users can be fetched
            val usersRes = supabaseClient.fetchAllProfiles(token)
            val userMap = usersRes.getOrNull()?.associateBy { it.employeeId.uppercase() } ?: emptyMap()

            val enriched = requests.map { req ->
                val matchedUser = userMap[req.employeeId.uppercase()]
                req.copy(employeeName = matchedUser?.fullName)
            }
            Result.success(enriched)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetEmployeePassword(
        requestId: String?,
        employeeId: String,
        newTemporaryPassword: String
    ): Result<String> {
        return try {
            val token = getActiveToken()
            supabaseClient.callAdminResetPassword(
                accessToken = token,
                requestId = requestId,
                employeeId = employeeId,
                newTemporaryPassword = newTemporaryPassword
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
