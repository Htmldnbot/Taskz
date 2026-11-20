package com.example.domain.repository

import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AdminAuditEntry
import com.example.core.model.OrgHierarchyData
import com.example.core.model.PasswordResetRequest
import com.example.core.model.ScopedMemberSearchResult
import com.example.core.model.UserProfile
import com.example.data.remote.SupabaseClient
import java.io.IOException

class AdminRepository(
    private val supabaseClient: SupabaseClient,
    private val userPrefs: UserPreferencesDataStore
) {
    private suspend fun getActiveToken(): String {
        val validToken = com.example.TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()
        return validToken
            ?: userPrefs.getUserSession().accessToken
            ?: throw IOException("Authentication required")
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

    suspend fun getOrgHierarchy(): Result<OrgHierarchyData> {
        return try {
            val token = getActiveToken()
            supabaseClient.fetchOrgHierarchy(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchScopedMembers(
        query: String = "",
        roleCode: String? = null,
        centreId: String? = null,
        stateId: String? = null,
        cityId: String? = null
    ): Result<List<ScopedMemberSearchResult>> {
        return try {
            val token = getActiveToken()
            supabaseClient.searchScopedMembers(
                accessToken = token,
                query = query,
                roleCode = roleCode,
                centreId = centreId,
                stateId = stateId,
                cityId = cityId
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserAccess(
        targetUserId: String,
        roleCodes: List<String>,
        activeRoleCode: String?,
        zoneId: String?,
        stateId: String?,
        cityId: String?,
        centreId: String?,
        isActive: Boolean?
    ): Result<Unit> {
        return try {
            val token = getActiveToken()
            supabaseClient.adminUpdateUserAccess(
                accessToken = token,
                targetUserId = targetUserId,
                roleCodes = roleCodes,
                activeRoleCode = activeRoleCode,
                zoneId = zoneId,
                stateId = stateId,
                cityId = cityId,
                centreId = centreId,
                isActive = isActive
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAdminAuditLogs(limit: Int = 60): Result<List<AdminAuditEntry>> {
        return try {
            val token = getActiveToken()
            supabaseClient.fetchAdminAuditLogs(token, limit)
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

    suspend fun upsertOrgCentre(
        id: String? = null,
        code: String,
        name: String,
        cityId: String? = null,
        districtName: String? = null,
        address: String? = null,
        subHub: String? = null,
        availableLans: List<String> = emptyList(),
        stateName: String? = null
    ): Result<com.example.core.model.OrgCentre> {
        return com.example.TaskzApplication.instance.taskzSyncManager.upsertOrgCentre(
            id = id,
            code = code,
            name = name,
            cityId = cityId,
            districtName = districtName,
            address = address,
            subHub = subHub,
            availableLans = availableLans,
            stateName = stateName
        )
    }
}
