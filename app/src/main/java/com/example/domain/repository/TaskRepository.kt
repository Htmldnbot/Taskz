package com.example.domain.repository

import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.CompletionPolicy
import com.example.core.model.DashboardStats
import com.example.core.model.GroupJoinRequest
import com.example.core.model.GroupMember
import com.example.core.model.Shift
import com.example.core.model.TaskActivityLog
import com.example.core.model.TaskCompletionRequest
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.TaskJoinRequest
import com.example.core.model.TaskParticipant
import com.example.core.model.TaskRoleRequirement
import com.example.core.model.UserProfile
import com.example.core.model.WorkRole
import com.example.data.local.dao.ProfileDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.TaskGroupDao
import com.example.data.local.dao.WorkRoleDao
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.TaskGroupEntity
import com.example.data.local.entity.WorkRoleEntity
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import android.content.Context
import com.example.TaskzApplication
import com.example.alarm.TaskAlarmScheduler
import com.example.R
import kotlinx.coroutines.tasks.await
import com.example.core.model.AlarmType
import com.example.core.model.LanProgressItem
import com.example.core.model.ResponseType
import com.example.core.model.TaskAcknowledgementRecord
import com.example.core.model.TaskImportance
import com.example.core.model.TaskResponseItem
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.data.local.TaskzDatabase
import com.example.data.local.dao.ScheduledAlarmDao
import com.example.data.local.dao.TaskResponseDao
import com.example.data.local.entity.ScheduledAlarmRecordEntity
import com.example.data.local.entity.TaskAcknowledgementEntity
import com.example.data.local.entity.TaskResponseEntity
import java.util.Locale
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao,
    private val taskGroupDao: TaskGroupDao,
    private val shiftDao: ShiftDao,
    private val workRoleDao: WorkRoleDao,
    private val profileDao: ProfileDao,
    private val taskResponseDao: TaskResponseDao = TaskzDatabase.getInstance(TaskzApplication.instance).taskResponseDao(),
    private val scheduledAlarmDao: ScheduledAlarmDao = TaskzDatabase.getInstance(TaskzApplication.instance).scheduledAlarmDao(),
    private val supabaseClient: SupabaseClient,
    private val userPrefs: UserPreferencesDataStore,
    private val context: Context = TaskzApplication.instance
) {
    private val tag = "TaskRepository"
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val taskAlarmScheduler = TaskAlarmScheduler(context)

    // In-memory data store for rich relations (requirements, participants, join requests, activity)
    // Synchronized across the app lifecycle and persisted in-memory / backed by Supabase
    private val requirementsStore = mutableMapOf<String, MutableList<TaskRoleRequirement>>()
    private val participantsStore = mutableMapOf<String, MutableList<TaskParticipant>>()
    private val taskJoinRequestsStore = mutableMapOf<String, MutableList<TaskJoinRequest>>()
    private val groupMembersStore = mutableMapOf<String, MutableList<GroupMember>>()
    private val groupJoinRequestsStore = mutableMapOf<String, MutableList<GroupJoinRequest>>()
    private val completionRequestsStore = mutableMapOf<String, MutableList<TaskCompletionRequest>>()
    private val activityLogStore = mutableMapOf<String, MutableList<TaskActivityLog>>()
    private val taskVisibleUsersStore = mutableMapOf<String, MutableSet<String>>()

    init {
        // Production initialization without fake demo data
    }

    private fun nowIso(): String = isoDateFormat.format(Date())

    private fun JSONObject?.optNullableString(key: String): String? {
        if (this == null || !has(key) || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    suspend fun getActiveUserId(): String? {
        return userPrefs.getUserSession().userId ?: profileDao.getCachedProfile()?.id
    }

    suspend fun getActiveUserName(): String {
        return profileDao.getCachedProfile()?.fullName ?: "Current Employee"
    }

    suspend fun getActiveUserEmployeeId(): String {
        return profileDao.getCachedProfile()?.employeeId ?: "EMP"
    }

    suspend fun isCurrentUserAdmin(): Boolean {
        return profileDao.getCachedProfile()?.appRole == "admin"
    }

    suspend fun getActiveAccessToken(): String? {
        val validToken = com.example.TaskzApplication.instance.authRepository.getValidAccessToken().getOrNull()
        if (!validToken.isNullOrBlank()) return validToken
        return userPrefs.getUserSession().accessToken?.takeIf {
            it.isNotBlank() &&
                !it.startsWith("firebase_") &&
                !it.startsWith("offline_") &&
                !com.example.core.network.FirebaseSessionHelper.isFirebaseJwt(it)
        }
    }

    // ============================================================
    // WORK ROLES
    // ============================================================

    fun getWorkRolesFlow(): Flow<List<WorkRole>> =
        workRoleDao.getAllWorkRolesFlow().map { list -> list.map { it.toDomain() } }

    fun getActiveWorkRolesFlow(): Flow<List<WorkRole>> =
        workRoleDao.getActiveWorkRolesFlow().map { list -> list.map { it.toDomain() } }

    suspend fun fetchWorkRoles(): Result<List<WorkRole>> = withContext(Dispatchers.IO) {
        try {
            val cached = workRoleDao.getAllWorkRolesFlow().firstOrNull()?.map { it.toDomain() }
            if (cached.isNullOrEmpty()) {
                val seed = listOf(
                    WorkRole("role_invigilator", "Invigilator", "INV", "Examination hall monitor & invigilation", true, 1),
                    WorkRole("role_it_manager", "IT Manager", "ITM", "Technical support and workstation maintenance", true, 2),
                    WorkRole("role_security", "Security Guard", "SEC", "Entry/exit security & perimeter monitoring", true, 3),
                    WorkRole("role_supervisor", "Floor Supervisor", "SUP", "Operational floor oversight and coordination", true, 4),
                    WorkRole("role_coordinator", "Event Coordinator", "CRD", "Event management and participant assistance", true, 5)
                )
                workRoleDao.insertWorkRoles(seed.map { WorkRoleEntity.fromDomain(it) })
                return@withContext Result.success(seed)
            }
            Result.success(cached)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createWorkRole(
        name: String,
        code: String?,
        description: String?,
        sortOrder: Int = 0
    ): Result<WorkRole> = withContext(Dispatchers.IO) {
        try {
            val role = WorkRole(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                code = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                isActive = true,
                sortOrder = sortOrder
            )
            workRoleDao.insertWorkRole(WorkRoleEntity.fromDomain(role))
            Result.success(role)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWorkRole(
        id: String,
        name: String,
        code: String?,
        description: String?,
        isActive: Boolean,
        sortOrder: Int
    ): Result<WorkRole> = withContext(Dispatchers.IO) {
        try {
            val existing = workRoleDao.getWorkRoleById(id)?.toDomain()
                ?: return@withContext Result.failure(IOException("Work role not found"))
            val updated = existing.copy(
                name = name.trim(),
                code = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                isActive = isActive,
                sortOrder = sortOrder
            )
            workRoleDao.updateWorkRole(WorkRoleEntity.fromDomain(updated))
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // GROUPS / EVENTS
    // ============================================================

    fun getGroupsFlow(): Flow<List<TaskGroup>> =
        taskGroupDao.getAllGroupsFlow().map { entities ->
            val userId = getActiveUserId()
            entities.map { entity ->
                val domain = entity.toDomain()
                val isMember = groupMembersStore[domain.id]?.any { it.userId == userId } == true || domain.createdBy == userId
                val isPending = groupJoinRequestsStore[domain.id]?.any { it.userId == userId && it.status == "pending" } == true
                val shiftsCount = shiftDao.getShiftsForGroupFlow(domain.id).firstOrNull()?.size ?: 0
                val tasksCount = taskDao.getTasksForGroupFlow(domain.id).firstOrNull()?.size ?: 0
                val membersCount = groupMembersStore[domain.id]?.size ?: (if (domain.createdBy == userId) 1 else 0)

                domain.copy(
                    isCurrentUserMember = isMember,
                    isCurrentUserPending = isPending,
                    shiftCount = shiftsCount,
                    taskCount = tasksCount,
                    memberCount = maxOf(1, membersCount)
                )
            }
        }

    suspend fun getGroupById(groupId: String): TaskGroup? = withContext(Dispatchers.IO) {
        val entity = taskGroupDao.getGroupById(groupId) ?: return@withContext null
        val domain = entity.toDomain()
        val userId = getActiveUserId()
        val isMember = groupMembersStore[domain.id]?.any { it.userId == userId } == true || domain.createdBy == userId
        val isPending = groupJoinRequestsStore[domain.id]?.any { it.userId == userId && it.status == "pending" } == true
        val shiftsCount = shiftDao.getShiftsForGroupFlow(domain.id).firstOrNull()?.size ?: 0
        val tasksCount = taskDao.getTasksForGroupFlow(domain.id).firstOrNull()?.size ?: 0
        val membersCount = groupMembersStore[domain.id]?.size ?: (if (domain.createdBy == userId) 1 else 0)

        domain.copy(
            isCurrentUserMember = isMember,
            isCurrentUserPending = isPending,
            shiftCount = shiftsCount,
            taskCount = tasksCount,
            memberCount = maxOf(1, membersCount)
        )
    }

    suspend fun createGroup(
        title: String,
        code: String?,
        description: String?,
        startDate: String?,
        endDate: String?,
        visibility: String = "everyone",
        allowSelfJoin: Boolean = true,
        joinRequiresApproval: Boolean = false,
        notifyCreatorOnJoin: Boolean = true,
        maxMembers: Int? = null
    ): Result<TaskGroup> = withContext(Dispatchers.IO) {
        try {
            val userId = getActiveUserId()
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required: No active user session found."))
            }
            val userName = getActiveUserName()
            val userEmpId = getActiveUserEmployeeId()
            val groupId = UUID.randomUUID().toString()

            val groupPayload = JSONObject().apply {
                put("id", groupId)
                put("title", title.trim())
                if (!code.isNullOrBlank()) put("code", code.trim().uppercase())
                if (!description.isNullOrBlank()) put("description", description.trim())
                if (!startDate.isNullOrBlank()) put("start_date", startDate)
                if (!endDate.isNullOrBlank()) put("end_date", endDate)
                put("status", "active")
                put("visibility", visibility)
                put("allow_self_join", allowSelfJoin)
                put("join_requires_approval", joinRequiresApproval)
                put("notify_creator_on_join", notifyCreatorOnJoin)
                if (maxMembers != null) put("max_members", maxMembers)
                put("created_by", userId)
            }

            val token = getActiveAccessToken()
            var finalStatus = "active"

            if (!token.isNullOrBlank()) {
                val remoteRes = supabaseClient.createGroup(token, groupPayload)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    val errMsg = err?.message ?: "Failed to create group on server"
                    val isNetworkError = err is java.net.UnknownHostException ||
                            err is java.net.ConnectException ||
                            err is java.net.SocketTimeoutException ||
                            errMsg.contains("offline", ignoreCase = true) ||
                            errMsg.contains("Unable to resolve host", ignoreCase = true)

                    if (isNetworkError) {
                        try {
                            TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(
                                com.example.data.local.entity.OutboxEventEntity(
                                    eventId = groupId,
                                    userId = userId,
                                    taskId = groupId,
                                    operationType = "GROUP_CREATION",
                                    payloadJson = groupPayload.toString(),
                                    syncStatus = "PENDING",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            com.example.sync.SyncWorker.enqueueSync(context)
                            finalStatus = "pending_sync"
                        } catch (oe: Exception) {
                            return@withContext Result.failure(IOException("Failed to commit durable offline group operation: ${oe.message}", oe))
                        }
                    } else {
                        // Server rejected (unauthorized, duplicate code, validation error) -> FAIL ATOMICALLY
                        return@withContext Result.failure(IOException(errMsg, err))
                    }
                }
            } else {
                return@withContext Result.failure(IllegalStateException("Authentication required: Missing session token."))
            }

            val group = TaskGroup(
                id = groupId,
                title = title.trim(),
                code = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                startDate = startDate,
                endDate = endDate,
                status = finalStatus,
                visibility = visibility,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                maxMembers = maxMembers,
                createdBy = userId,
                creatorName = userName,
                memberCount = 1,
                shiftCount = 0,
                taskCount = 0,
                isCurrentUserMember = true,
                createdAt = nowIso(),
                updatedAt = nowIso()
            )

            taskGroupDao.insertGroup(TaskGroupEntity.fromDomain(group))

            // Add creator as initial member
            val creatorMember = GroupMember(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                userId = userId,
                employeeId = userEmpId,
                fullName = userName,
                groupRole = "owner",
                joinedAt = nowIso()
            )
            groupMembersStore.getOrPut(groupId) { mutableListOf() }.add(creatorMember)

            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGroup(
        groupId: String,
        title: String,
        code: String?,
        description: String?,
        startDate: String?,
        endDate: String?,
        status: String,
        visibility: String,
        allowSelfJoin: Boolean,
        joinRequiresApproval: Boolean,
        notifyCreatorOnJoin: Boolean,
        maxMembers: Int?
    ): Result<TaskGroup> = withContext(Dispatchers.IO) {
        try {
            val existing = taskGroupDao.getGroupById(groupId)?.toDomain()
                ?: return@withContext Result.failure(IOException("Group not found"))
            val updated = existing.copy(
                title = title.trim(),
                code = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                startDate = startDate,
                endDate = endDate,
                status = status,
                visibility = visibility,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                maxMembers = maxMembers,
                updatedAt = nowIso()
            )
            taskGroupDao.updateGroup(TaskGroupEntity.fromDomain(updated))

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val groupPayload = JSONObject().apply {
                    put("title", title.trim())
                    if (!code.isNullOrBlank()) put("code", code.trim().uppercase())
                    if (!description.isNullOrBlank()) put("description", description.trim())
                    if (!startDate.isNullOrBlank()) put("start_date", startDate)
                    if (!endDate.isNullOrBlank()) put("end_date", endDate)
                    put("status", status)
                    put("visibility", visibility)
                    put("allow_self_join", allowSelfJoin)
                    put("join_requires_approval", joinRequiresApproval)
                    put("notify_creator_on_join", notifyCreatorOnJoin)
                    if (maxMembers != null) put("max_members", maxMembers)
                }
                supabaseClient.updateGroup(token, groupId, groupPayload)
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(groupId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val group = getGroupById(groupId) ?: return@withContext Result.failure(IOException("Group not found"))
            val userId = getActiveUserId() ?: return@withContext Result.failure(IOException("User not signed in"))
            val userName = getActiveUserName()
            val userEmpId = getActiveUserEmployeeId()

            if (!group.allowSelfJoin) {
                return@withContext Result.failure(IOException("Self-join is disabled for this group"))
            }

            if (group.joinRequiresApproval) {
                // Create pending join request
                val request = GroupJoinRequest(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    userId = userId,
                    employeeId = userEmpId,
                    fullName = userName,
                    status = "pending",
                    createdAt = nowIso()
                )
                groupJoinRequestsStore.getOrPut(groupId) { mutableListOf() }.add(request)
                Result.success("Request Submitted: Approval Pending")
            } else {
                // Immediate membership
                val member = GroupMember(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    userId = userId,
                    employeeId = userEmpId,
                    fullName = userName,
                    groupRole = "member",
                    joinedAt = nowIso()
                )
                groupMembersStore.getOrPut(groupId) { mutableListOf() }.add(member)
                Result.success("Joined Group Successfully")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGroupMembers(groupId: String): List<GroupMember> = withContext(Dispatchers.IO) {
        groupMembersStore[groupId]?.toList() ?: emptyList()
    }

    suspend fun getGroupJoinRequests(groupId: String): List<GroupJoinRequest> = withContext(Dispatchers.IO) {
        groupJoinRequestsStore[groupId]?.filter { it.status == "pending" } ?: emptyList()
    }

    suspend fun reviewGroupJoinRequest(requestId: String, approved: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var foundReq: GroupJoinRequest? = null
            var targetGroupId: String? = null

            for ((gid, reqs) in groupJoinRequestsStore) {
                val match = reqs.find { it.id == requestId }
                if (match != null) {
                    foundReq = match
                    targetGroupId = gid
                    break
                }
            }

            if (foundReq == null || targetGroupId == null) {
                return@withContext Result.failure(IOException("Join request not found"))
            }

            val updatedReq = foundReq.copy(
                status = if (approved) "approved" else "rejected",
                reviewedBy = getActiveUserId(),
                reviewedAt = nowIso()
            )

            val list = groupJoinRequestsStore[targetGroupId]!!
            val index = list.indexOfFirst { it.id == requestId }
            if (index != -1) list[index] = updatedReq

            if (approved) {
                val member = GroupMember(
                    id = UUID.randomUUID().toString(),
                    groupId = targetGroupId,
                    userId = foundReq.userId,
                    employeeId = foundReq.employeeId,
                    fullName = foundReq.fullName,
                    groupRole = "member",
                    joinedAt = nowIso()
                )
                groupMembersStore.getOrPut(targetGroupId) { mutableListOf() }.add(member)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // SHIFTS
    // ============================================================

    fun getShiftsForGroupFlow(groupId: String): Flow<List<Shift>> =
        shiftDao.getShiftsForGroupFlow(groupId).map { list ->
            list.map { entity ->
                val domain = entity.toDomain()
                enrichShiftWithManpower(domain)
            }
        }

    suspend fun getShiftById(shiftId: String): Shift? = withContext(Dispatchers.IO) {
        val entity = shiftDao.getShiftById(shiftId) ?: return@withContext null
        enrichShiftWithManpower(entity.toDomain())
    }

    private suspend fun enrichShiftWithManpower(shift: Shift): Shift {
        val tasks = taskDao.getTasksForShiftFlow(shift.id).firstOrNull() ?: emptyList()
        var totalReq = 0
        var totalAssigned = 0

        for (task in tasks) {
            val reqs = requirementsStore[task.id] ?: emptyList()
            for (r in reqs) totalReq += r.requiredCount
            val parts = participantsStore[task.id]?.filter { it.status == "active" } ?: emptyList()
            totalAssigned += parts.size
        }

        return shift.copy(
            taskCount = tasks.size,
            requiredManpower = totalReq,
            assignedManpower = totalAssigned,
            vacantManpower = maxOf(0, totalReq - totalAssigned)
        )
    }

    suspend fun createShift(
        groupId: String,
        name: String,
        shiftNumber: Int?,
        startsAt: String,
        endsAt: String,
        notes: String?,
        status: String = "scheduled"
    ): Result<Shift> = withContext(Dispatchers.IO) {
        try {
            // Validate: end > start
            if (endsAt <= startsAt) {
                return@withContext Result.failure(IOException("Shift end time must be after start time"))
            }

            val userId = getActiveUserId()
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required: No active user session found."))
            }
            val shiftId = UUID.randomUUID().toString()

            var validGroupId = groupId
            if (!com.example.core.network.SupabaseConfig.isValidUuid(validGroupId)) {
                val uuidGroup = taskGroupDao.getAllGroupsFlow().firstOrNull()
                    ?.firstOrNull { com.example.core.network.SupabaseConfig.isValidUuid(it.id) }
                validGroupId = uuidGroup?.id ?: createGroup(
                    title = getGroupById(groupId)?.title ?: "General Operations",
                    code = null,
                    description = "Operational group for centre shifts",
                    startDate = null,
                    endDate = null
                ).getOrNull()?.id ?: groupId
            }
            if (!com.example.core.network.SupabaseConfig.isValidUuid(validGroupId)) {
                return@withContext Result.failure(IllegalArgumentException("Please select or create a synced operational group before creating a shift."))
            }

            val shiftPayload = JSONObject().apply {
                put("id", shiftId)
                put("group_id", validGroupId)
                put("name", name.trim())
                if (shiftNumber != null) put("shift_number", shiftNumber)
                put("starts_at", startsAt)
                put("ends_at", endsAt)
                if (!notes.isNullOrBlank()) put("notes", notes.trim())
                put("status", status)
                put("created_by", userId)
            }

            val token = getActiveAccessToken()
            var finalStatus = status

            if (!token.isNullOrBlank()) {
                val remoteRes = supabaseClient.createShift(token, shiftPayload)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    val errMsg = err?.message ?: "Failed to create shift on server"
                    val isNetworkError = err is java.net.UnknownHostException ||
                            err is java.net.ConnectException ||
                            err is java.net.SocketTimeoutException ||
                            errMsg.contains("offline", ignoreCase = true) ||
                            errMsg.contains("Unable to resolve host", ignoreCase = true)

                    if (isNetworkError) {
                        try {
                            TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(
                                com.example.data.local.entity.OutboxEventEntity(
                                    eventId = shiftId,
                                    userId = userId,
                                    taskId = shiftId,
                                    operationType = "SHIFT_CREATION",
                                    payloadJson = shiftPayload.toString(),
                                    syncStatus = "PENDING",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            com.example.sync.SyncWorker.enqueueSync(context)
                            finalStatus = "pending_sync"
                        } catch (oe: Exception) {
                            return@withContext Result.failure(IOException("Failed to commit durable offline shift operation: ${oe.message}", oe))
                        }
                    } else {
                        // Server rejected -> FAIL ATOMICALLY
                        return@withContext Result.failure(IOException(errMsg, err))
                    }
                }
            } else {
                return@withContext Result.failure(IllegalStateException("Authentication required: Missing session token."))
            }

            val shift = Shift(
                id = shiftId,
                groupId = groupId,
                name = name.trim(),
                shiftNumber = shiftNumber,
                startsAt = startsAt,
                endsAt = endsAt,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                status = finalStatus,
                createdBy = userId
            )

            shiftDao.insertShift(ShiftEntity.fromDomain(shift))

            Result.success(shift)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateShift(
        shiftId: String,
        name: String,
        shiftNumber: Int?,
        startsAt: String,
        endsAt: String,
        notes: String?,
        status: String
    ): Result<Shift> = withContext(Dispatchers.IO) {
        try {
            if (endsAt <= startsAt) {
                return@withContext Result.failure(IOException("Shift end time must be after start time"))
            }
            val existing = shiftDao.getShiftById(shiftId)?.toDomain()
                ?: return@withContext Result.failure(IOException("Shift not found"))
            val updated = existing.copy(
                name = name.trim(),
                shiftNumber = shiftNumber,
                startsAt = startsAt,
                endsAt = endsAt,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                status = status
            )
            shiftDao.updateShift(ShiftEntity.fromDomain(updated))

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val shiftPayload = JSONObject().apply {
                    put("name", name.trim())
                    if (shiftNumber != null) put("shift_number", shiftNumber)
                    put("starts_at", startsAt)
                    put("ends_at", endsAt)
                    if (!notes.isNullOrBlank()) put("notes", notes.trim())
                    put("status", status)
                }
                supabaseClient.updateShift(token, shiftId, shiftPayload)
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copy Shift functionality:
     * - Preserves relative time offsets for tasks
     * - Optional copy manpower requirements (default ON)
     * - Optional copy assigned employees (default OFF)
     */
    suspend fun copyShift(
        sourceShiftId: String,
        newShiftName: String,
        newStartsAt: String,
        copyManpowerRequirements: Boolean = true,
        copyAssignedEmployees: Boolean = false
    ): Result<Shift> = withContext(Dispatchers.IO) {
        try {
            val sourceShift = getShiftById(sourceShiftId)
                ?: return@withContext Result.failure(IOException("Source shift not found"))

            // Parse source start and new start to calculate millisecond delta
            val sourceStartMillis = parseDateTimeToMillis(sourceShift.startsAt)
            val newStartMillis = parseDateTimeToMillis(newStartsAt)
            val timeOffsetMillis = newStartMillis - sourceStartMillis

            val sourceEndMillis = parseDateTimeToMillis(sourceShift.endsAt)
            val newEndMillis = sourceEndMillis + timeOffsetMillis
            val newEndsAt = formatMillisToDateTime(newEndMillis)

            val newShiftId = UUID.randomUUID().toString()
            val userId = getActiveUserId()
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required: No active user session found."))
            }

            val newShift = Shift(
                id = newShiftId,
                groupId = sourceShift.groupId,
                name = newShiftName.trim(),
                shiftNumber = (sourceShift.shiftNumber ?: 0) + 1,
                startsAt = newStartsAt,
                endsAt = newEndsAt,
                notes = "Copied from ${sourceShift.name}",
                status = "scheduled",
                createdBy = userId
            )

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val offsetMinutes = (timeOffsetMillis / 60000L).toInt()
                val remoteRes = supabaseClient.copyShift(
                    accessToken = token,
                    sourceShiftId = sourceShiftId,
                    newShiftName = newShiftName.trim(),
                    offsetMinutes = offsetMinutes,
                    copyManpower = copyManpowerRequirements,
                    copyAssignments = copyAssignedEmployees
                )
                if (remoteRes.isSuccess) {
                    syncShiftsFromServer(token)
                    syncTasksFromServer(token)
                    val syncedShift = getShiftById(newShiftId) ?: newShift
                    return@withContext Result.success(syncedShift)
                } else {
                    val err = remoteRes.exceptionOrNull()
                    val errMsg = err?.message ?: "Shift copy failed on server"
                    val isNetworkError = err is java.net.UnknownHostException ||
                            err is java.net.ConnectException ||
                            err is java.net.SocketTimeoutException ||
                            errMsg.contains("offline", ignoreCase = true) ||
                            errMsg.contains("Unable to resolve host", ignoreCase = true)

                    if (!isNetworkError) {
                        return@withContext Result.failure(IOException(errMsg, err))
                    }
                }
            } else {
                return@withContext Result.failure(IllegalStateException("Authentication required: Missing session token."))
            }

            shiftDao.insertShift(ShiftEntity.fromDomain(newShift))

            // Fetch and copy tasks belonging to source shift
            val sourceTasks = taskDao.getTasksForShiftFlow(sourceShiftId).firstOrNull() ?: emptyList()
            for (sourceTaskEntity in sourceTasks) {
                val st = sourceTaskEntity.toDomain()
                val newTaskStart = st.startsAt?.let { formatMillisToDateTime(parseDateTimeToMillis(it) + timeOffsetMillis) }
                val newTaskDue = st.dueAt?.let { formatMillisToDateTime(parseDateTimeToMillis(it) + timeOffsetMillis) }
                val newExecutionTime = st.executionTime?.let { formatMillisToDateTime(parseDateTimeToMillis(it) + timeOffsetMillis) }
                val newTaskId = UUID.randomUUID().toString()

                val newTask = st.copy(
                    id = newTaskId,
                    shiftId = newShiftId,
                    shiftName = newShiftName,
                    startsAt = newTaskStart,
                    dueAt = newTaskDue,
                    executionTime = newExecutionTime,
                    status = "open",
                    currentProgress = 0,
                    lastProgressUpdate = null,
                    isAcknowledged = false,
                    acknowledgedAt = null,
                    isPendingCompletionConfirmation = false,
                    createdAt = nowIso(),
                    updatedAt = nowIso()
                )
                taskDao.insertTask(TaskEntity.fromDomain(newTask))
                taskAlarmScheduler.scheduleAlarmsForTask(newTask)

                // Copy Manpower requirements
                if (copyManpowerRequirements) {
                    val srcReqs = requirementsStore[st.id] ?: emptyList()
                    val newReqs = srcReqs.map { r ->
                        r.copy(
                            id = UUID.randomUUID().toString(),
                            taskId = newTaskId,
                            filledCount = 0
                        )
                    }.toMutableList()
                    requirementsStore[newTaskId] = newReqs
                }

                // Copy Assigned Employees (Default OFF)
                if (copyAssignedEmployees) {
                    val srcParts = participantsStore[st.id]?.filter { it.status == "active" } ?: emptyList()
                    val newParts = srcParts.map { p ->
                        p.copy(
                            id = UUID.randomUUID().toString(),
                            taskId = newTaskId,
                            source = "copied_assignment",
                            joinedAt = nowIso(),
                            status = "active"
                        )
                    }.toMutableList()
                    participantsStore[newTaskId] = newParts
                }

                // Log copy activity
                activityLogStore.getOrPut(newTaskId) { mutableListOf() }.add(
                    TaskActivityLog(
                        id = System.currentTimeMillis(),
                        taskId = newTaskId,
                        actorName = getActiveUserName(),
                        eventType = "TASK_COPIED",
                        message = "Task created from shift copy of '${sourceShift.name}'",
                        createdAt = nowIso()
                    )
                )
            }

            Result.success(newShift)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // TASKS
    // ============================================================

    fun getAllTasksFlow(): Flow<List<TaskItem>> =
        taskDao.getAllTasksFlow().map { list ->
            val userId = getActiveUserId()
            list.map { entity ->
                val domain = entity.toDomain()
                enrichTaskWithParticipantsAndCounts(domain, userId)
            }
        }

    fun getTasksForGroupFlow(groupId: String): Flow<List<TaskItem>> =
        taskDao.getTasksForGroupFlow(groupId).map { list ->
            val userId = getActiveUserId()
            list.map { entity ->
                val domain = entity.toDomain()
                enrichTaskWithParticipantsAndCounts(domain, userId)
            }
        }

    fun getTasksForShiftFlow(shiftId: String): Flow<List<TaskItem>> =
        taskDao.getTasksForShiftFlow(shiftId).map { list ->
            val userId = getActiveUserId()
            list.map { entity ->
                val domain = entity.toDomain()
                enrichTaskWithParticipantsAndCounts(domain, userId)
            }
        }

    suspend fun getTaskById(taskId: String): TaskItem? = withContext(Dispatchers.IO) {
        val entity = taskDao.getTaskById(taskId) ?: return@withContext null
        enrichTaskWithParticipantsAndCounts(entity.toDomain(), getActiveUserId())
    }

    private suspend fun enrichTaskWithParticipantsAndCounts(task: TaskItem, userId: String?): TaskItem {
        val reqs = requirementsStore[task.id] ?: emptyList()
        val parts = participantsStore[task.id] ?: emptyList()
        val activeParts = parts.filter { it.status == "active" }

        val isCreator = (userId != null && task.createdBy == userId)
        val myParticipant = activeParts.find { it.userId == userId }
        val myPendingJoin = taskJoinRequestsStore[task.id]?.find { it.userId == userId && it.status == "pending" }
        val isPendingCompletion = completionRequestsStore[task.id]?.any { it.status == "pending" } == true

        var totalReq = 0
        for (r in reqs) totalReq += r.requiredCount
        val totalAssigned = if (activeParts.isEmpty() && isCreator) 1 else activeParts.size
        val effectiveParticipantStatus = myParticipant?.status ?: if (isCreator) "active" else null

        return task.copy(
            totalRequired = totalReq,
            totalAssigned = totalAssigned,
            totalVacant = maxOf(0, totalReq - totalAssigned),
            currentUserRoleName = myParticipant?.roleName ?: if (isCreator) "Creator" else null,
            currentUserParticipantStatus = effectiveParticipantStatus,
            currentUserJoinStatus = myPendingJoin?.status,
            isPendingCompletionConfirmation = isPendingCompletion
        )
    }

    /**
     * Resolves an employee identifier (which could be an employeeId or UUID) to their profiles.id UUID.
     */
    suspend fun resolveToProfileUuid(idOrEmpId: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = idOrEmpId.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Employee ID cannot be blank"))
        }

        // 1. If already valid UUID format, return immediately
        if (com.example.core.network.SupabaseConfig.isValidUuid(trimmed)) {
            return@withContext Result.success(trimmed)
        }

        // 2. Check local ProfileDao cache
        val localProfile = profileDao.findProfileByIdOrEmployeeId(trimmed)
        if (localProfile != null && com.example.core.network.SupabaseConfig.isValidUuid(localProfile.id)) {
            return@withContext Result.success(localProfile.id)
        }

        // 3. Query remote Supabase profiles if token is a valid Supabase JWT
        val token = getActiveAccessToken()
        if (!token.isNullOrBlank() && token.split(".").size == 3) {
            val remoteRes = supabaseClient.resolveEmployeeToUuid(token, trimmed)
            val resolved = remoteRes.getOrNull()
            if (!resolved.isNullOrBlank() && com.example.core.network.SupabaseConfig.isValidUuid(resolved)) {
                return@withContext Result.success(resolved)
            }
        }

        Result.failure(NoSuchElementException("Employee '$trimmed' could not be resolved to a registered Taskz account."))
    }

    suspend fun createTask(
        groupId: String,
        shiftId: String?,
        title: String,
        description: String?,
        location: String?,
        startsAt: String?,
        dueAt: String?,
        priority: String = "normal",
        visibility: String = "everyone",
        selectedEmployeeIds: List<String> = emptyList(),
        allowSelfJoin: Boolean = true,
        joinRequiresApproval: Boolean = true,
        notifyCreatorOnJoin: Boolean = true,
        requiresCompletionConfirmation: Boolean = false,
        maxParticipants: Int? = null,
        requirements: List<Pair<String, Int>> = emptyList(), // (roleId, count)
        taskType: TaskType = TaskType.SIMPLE,
        importance: TaskImportance = TaskImportance.NORMAL,
        urgencyType: TimeUrgency = TimeUrgency.BEFORE_DEADLINE,
        executionTime: String? = null,
        targetPercentage: Int? = 100,
        repeatIntervalMinutes: Int? = null,
        alarmType: AlarmType = AlarmType.SOUND_ALERT,
        advanceRemindersMinutes: List<Int> = listOf(0, 5),
        remindUntilCompleted: Boolean = false,
        overdueReminderIntervalMinutes: Int? = 5,
        maxOverdueReminders: Int? = 3,
        responseType: ResponseType = ResponseType.COMPLETED_NOT_COMPLETED,
        lanTargetList: List<String> = emptyList(),
        completionPolicy: CompletionPolicy = CompletionPolicy.SINGLE_ASSIGNEE,
        responseFormSchemaJson: String? = null
    ): Result<TaskItem> = withContext(Dispatchers.IO) {
        try {
            if (title.trim().isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Task title cannot be blank"))
            }

            var targetGroupId = groupId
            var group = if (targetGroupId.isNotBlank()) getGroupById(targetGroupId) else null
            if (group == null) {
                val allGroups = taskGroupDao.getAllGroupsFlow().firstOrNull() ?: emptyList()
                if (allGroups.isNotEmpty()) {
                    group = allGroups.first().toDomain()
                    targetGroupId = group.id
                } else {
                    val defaultGroupRes = createGroup(
                        title = "General Operations",
                        code = "GEN-OPS",
                        description = "Default operations group for standalone tasks",
                        startDate = null,
                        endDate = null
                    )
                    group = defaultGroupRes.getOrNull()
                    targetGroupId = group?.id ?: UUID.randomUUID().toString()
                }
            }

            val shift = shiftId?.let { getShiftById(it) }
            val userId = getActiveUserId()
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required: No active user session found."))
            }
            val userName = getActiveUserName()
            val userEmpId = getActiveUserEmployeeId()
            val taskId = UUID.randomUUID().toString()
            val clientOperationId = UUID.randomUUID().toString()

            // Resolve selected employee identifiers to registered Supabase profiles.id UUIDs
            val resolvedEmployeeUuids = mutableListOf<String>()
            for (empId in selectedEmployeeIds) {
                val resolveRes = resolveToProfileUuid(empId)
                val resolvedUuid = resolveRes.getOrNull()
                if (!resolvedUuid.isNullOrBlank() &&
                    resolvedUuid != userId &&
                    com.example.core.network.SupabaseConfig.isValidUuid(resolvedUuid)
                ) {
                    resolvedEmployeeUuids.add(resolvedUuid)
                } else if (resolvedUuid != userId) {
                    Log.w(tag, "Skipping unassigned non-UUID employee identifier '$empId': ${resolveRes.exceptionOrNull()?.message}")
                }
            }

            val normalizedVisibility = if (visibility.equals("restricted", ignoreCase = true)) "restricted" else "everyone"
            val effectiveLocation = location?.trim()?.takeIf { it.isNotBlank() }
                ?: group?.title?.takeIf { !com.example.core.network.SupabaseConfig.isValidUuid(targetGroupId) }

            val task = TaskItem(
                id = taskId,
                groupId = targetGroupId,
                groupTitle = group?.title ?: "General Operations",
                shiftId = shiftId,
                shiftName = shift?.name,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                location = effectiveLocation,
                startsAt = startsAt,
                dueAt = dueAt,
                priority = priority.lowercase(),
                status = "open",
                visibility = normalizedVisibility,
                allowSelfJoin = allowSelfJoin,
                joinRequiresApproval = joinRequiresApproval,
                notifyCreatorOnJoin = notifyCreatorOnJoin,
                requiresCompletionConfirmation = requiresCompletionConfirmation,
                maxParticipants = maxParticipants,
                createdBy = userId,
                creatorName = userName,
                createdAt = nowIso(),
                updatedAt = nowIso(),
                taskType = taskType,
                importance = importance,
                urgencyType = urgencyType,
                executionTime = executionTime,
                targetPercentage = targetPercentage,
                repeatIntervalMinutes = repeatIntervalMinutes,
                alarmType = alarmType,
                advanceRemindersMinutes = advanceRemindersMinutes,
                remindUntilCompleted = remindUntilCompleted,
                overdueReminderIntervalMinutes = overdueReminderIntervalMinutes,
                maxOverdueReminders = maxOverdueReminders,
                responseType = responseType,
                lanTargetList = lanTargetList,
                completionPolicy = completionPolicy,
                responseFormSchemaJson = responseFormSchemaJson
            )

            // Build Supabase JSON Payload for atomic persistence (strictly UUID-safe for RPC casts)
            val taskPayload = JSONObject().apply {
                put("id", taskId)
                put("client_operation_id", clientOperationId)
                if (com.example.core.network.SupabaseConfig.isValidUuid(targetGroupId)) {
                    put("group_id", targetGroupId)
                }
                if (com.example.core.network.SupabaseConfig.isValidUuid(shiftId)) {
                    put("shift_id", shiftId)
                }
                put("title", title.trim())
                if (!description.isNullOrBlank()) put("description", description.trim())
                if (!effectiveLocation.isNullOrBlank()) put("location", effectiveLocation)
                if (!startsAt.isNullOrBlank()) put("starts_at", startsAt)
                if (!dueAt.isNullOrBlank()) put("due_at", dueAt)
                put("priority", priority.lowercase())
                put("visibility", normalizedVisibility)
                put("allow_self_join", allowSelfJoin)
                put("join_requires_approval", joinRequiresApproval)
                put("notify_creator_on_join", notifyCreatorOnJoin)
                put("requires_completion_confirmation", requiresCompletionConfirmation)
                if (maxParticipants != null) put("max_participants", maxParticipants)
                put("task_type", taskType.name)
                put("importance", importance.name)
                put("urgency_type", urgencyType.name)
                if (!executionTime.isNullOrBlank()) put("execution_time", executionTime)
                if (targetPercentage != null) put("target_percentage", targetPercentage)
                if (repeatIntervalMinutes != null) put("repeat_interval_minutes", repeatIntervalMinutes)
                put("alarm_type", alarmType.name)
                put("advance_reminders_minutes", JSONArray(advanceRemindersMinutes))
                put("remind_until_completed", remindUntilCompleted)
                if (overdueReminderIntervalMinutes != null) put("overdue_reminder_interval_minutes", overdueReminderIntervalMinutes)
                if (maxOverdueReminders != null) put("max_overdue_reminders", maxOverdueReminders)
                put("response_type", responseType.name)
                put("lan_target_list", JSONArray(lanTargetList))
                put("completion_policy", completionPolicy.name)
                if (!responseFormSchemaJson.isNullOrBlank()) {
                    try {
                        put("response_form_schema", JSONArray(responseFormSchemaJson))
                    } catch (_: Exception) {}
                }
                put("assigned_user_ids", JSONArray(resolvedEmployeeUuids.distinct()))
                val reqsArr = JSONArray()
                for ((rId, cnt) in requirements) {
                    if (com.example.core.network.SupabaseConfig.isValidUuid(rId)) {
                        reqsArr.put(JSONObject().apply {
                            put("role_id", rId)
                            put("required_count", cnt)
                        })
                    }
                }
                put("requirements", reqsArr)
            }

            var token = getActiveAccessToken() ?: ""
            var serverSavedTask = task

            if (!com.example.core.network.SupabaseConfig.isConfigured) {
                return@withContext Result.failure(IOException("Supabase is not configured. Please verify SUPABASE_URL and SUPABASE_ANON_KEY."))
            }

            var remoteRes = if (token.isNotBlank()) {
                supabaseClient.createTaskAtomic(token, taskPayload)
            } else {
                Result.failure(IOException("Authentication session missing. Please sign out and sign in again to sync tasks."))
            }

            if (remoteRes.isFailure) {
                val firstMsg = remoteRes.exceptionOrNull()?.message.orEmpty()
                if (firstMsg.contains("JWT", ignoreCase = true) ||
                    firstMsg.contains("401") ||
                    firstMsg.contains("Unauthorized", ignoreCase = true)
                ) {
                    val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                    if (!refreshed.isNullOrBlank()) {
                        token = refreshed
                        remoteRes = supabaseClient.createTaskAtomic(token, taskPayload)
                    }
                }
            }

            if (remoteRes.isSuccess) {
                val serverObj = remoteRes.getOrThrow()
                val confirmedId = serverObj.optString("task_id", taskId)
                val serverTaskObj = serverObj.optJSONObject("task")
                val confirmedGroupId = serverTaskObj.optNullableString("group_id")
                    ?.takeIf { com.example.core.network.SupabaseConfig.isValidUuid(it) }
                    ?: targetGroupId
                serverSavedTask = task.copy(id = confirmedId, groupId = confirmedGroupId, status = "open")
            } else {
                val err = remoteRes.exceptionOrNull()
                val isTrulyOffline = !TaskzApplication.instance.networkMonitor.isCurrentlyConnected() ||
                    (err != null && TaskzApplication.instance.authRepository.isNetworkFailure(err))

                if (isTrulyOffline && token.isNotBlank()) {
                    Log.i(tag, "Device offline — queuing task creation into outbox: ${err?.message}")
                    try {
                        TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(
                            com.example.data.local.entity.OutboxEventEntity(
                                eventId = clientOperationId,
                                userId = userId,
                                taskId = taskId,
                                operationType = "TASK_CREATION",
                                payloadJson = taskPayload.toString(),
                                syncStatus = "PENDING",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        com.example.sync.SyncWorker.enqueueSync(context)
                        serverSavedTask = task.copy(status = "pending_sync")
                    } catch (oe: Exception) {
                        return@withContext Result.failure(IOException("Failed to commit durable offline task creation: ${oe.message}", oe))
                    }
                } else {
                    val reason = err?.message ?: "Server rejected task creation"
                    Log.e(tag, "Task creation server sync failed: $reason")
                    return@withContext Result.failure(IOException("Failed to sync task to server: $reason", err))
                }
            }

            // Save to Room cache
            taskDao.insertTask(TaskEntity.fromDomain(serverSavedTask))

            // Schedule device alarms via AlarmManager
            try {
                taskAlarmScheduler.scheduleAlarmsForTask(serverSavedTask)
            } catch (e: Exception) {
                Log.e(tag, "Failed to schedule alarm: ${e.message}")
            }

            // Store visible users if visibility is restricted
            if (visibility == "restricted") {
                taskVisibleUsersStore[serverSavedTask.id] = selectedEmployeeIds.toMutableSet()
            }

            // Store manpower requirements locally
            val allRoles = workRoleDao.getAllWorkRolesFlow().firstOrNull() ?: emptyList()
            val roleMap = allRoles.associateBy { it.id }

            val reqList = requirements.map { (roleId, count) ->
                val roleName = roleMap[roleId]?.name ?: "Role"
                TaskRoleRequirement(
                    id = UUID.randomUUID().toString(),
                    taskId = serverSavedTask.id,
                    roleId = roleId,
                    requiredCount = count,
                    roleName = roleName,
                    filledCount = 0
                )
            }.toMutableList()
            requirementsStore[serverSavedTask.id] = reqList

            // Add activity log
            activityLogStore.getOrPut(serverSavedTask.id) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = serverSavedTask.id,
                    actorName = userName,
                    eventType = "TASK_CREATED",
                    message = "Task created by $userName ($taskType, $importance priority)",
                    createdAt = nowIso()
                )
            )

            // Add creator as active participant
            participantsStore.getOrPut(serverSavedTask.id) { mutableListOf() }.add(
                TaskParticipant(
                    id = UUID.randomUUID().toString(),
                    taskId = serverSavedTask.id,
                    userId = userId,
                    userName = userName,
                    userEmployeeId = userEmpId,
                    status = "active",
                    joinedAt = nowIso(),
                    assignedBy = userId
                )
            )

            // Add assigned employees locally
            for (assignedId in selectedEmployeeIds) {
                participantsStore.getOrPut(serverSavedTask.id) { mutableListOf() }.add(
                    TaskParticipant(
                        id = UUID.randomUUID().toString(),
                        taskId = serverSavedTask.id,
                        userId = assignedId,
                        userName = "Assigned Employee",
                        userEmployeeId = "",
                        status = "active",
                        joinedAt = nowIso(),
                        assignedBy = userId
                    )
                )
            }

            Result.success(serverSavedTask)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cancel any scheduled native device alarms immediately
            taskAlarmScheduler.cancelAlarmsForTask(taskId)
            // Delete from local Room database
            taskDao.deleteTask(taskId)
            // Delete associated responses and acknowledgements
            taskResponseDao.deleteResponsesForTask(taskId)
            taskResponseDao.deleteAcknowledgementsForTask(taskId)
            // Clean up in-memory caches
            participantsStore.remove(taskId)
            requirementsStore.remove(taskId)
            activityLogStore.remove(taskId)
            taskVisibleUsersStore.remove(taskId)

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank() && com.example.core.network.SupabaseConfig.isConfigured) {
                val deleteRes = supabaseClient.deleteTask(token, taskId)
                if (deleteRes.isFailure) {
                    try {
                        val session = userPrefs.getUserSession()
                        TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(
                            com.example.data.local.entity.OutboxEventEntity(
                                eventId = UUID.randomUUID().toString(),
                                userId = session.userId ?: "",
                                taskId = taskId,
                                operationType = "TASK_DELETION",
                                payloadJson = JSONObject().put("task_id", taskId).toString(),
                                syncStatus = "PENDING",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        com.example.sync.SyncWorker.enqueueSync(context)
                    } catch (_: Exception) {}
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all authorized tasks from Supabase and synchronize into local Room cache.
     * Reconciles participants, requirements, and schedules alarms on this device.
     */
    suspend fun syncTasksFromServer(token: String): Result<List<TaskItem>> = withContext(Dispatchers.IO) {
        try {
            val remoteRes = supabaseClient.fetchTasks(token)
            if (remoteRes.isFailure) {
                return@withContext Result.failure(remoteRes.exceptionOrNull()!!)
            }

            val remoteTasksJson = remoteRes.getOrDefault(emptyList())
            val currentUserId = getActiveUserId()
            val entities = mutableListOf<TaskEntity>()
            val domainTasks = mutableListOf<TaskItem>()

            for (taskJson in remoteTasksJson) {
                val taskId = taskJson.optString("id")
                val groupObj = taskJson.optJSONObject("task_groups")
                val shiftObj = taskJson.optJSONObject("shifts")

                // Parse participants
                val participantsJson = taskJson.optJSONArray("task_participants")
                val taskParts = mutableListOf<TaskParticipant>()
                var myParticipantStatus: String? = null
                var myRoleName: String? = null

                if (participantsJson != null) {
                    for (i in 0 until participantsJson.length()) {
                        val pObj = participantsJson.getJSONObject(i)
                        val pUserId = pObj.optString("user_id")
                        val pStatus = pObj.optString("status", "active")
                        val part = TaskParticipant(
                            id = pObj.optString("id", UUID.randomUUID().toString()),
                            taskId = taskId,
                            userId = pUserId,
                            userName = pObj.optNullableString("full_name") ?: "Employee",
                            userEmployeeId = pObj.optNullableString("employee_id") ?: "",
                            roleName = pObj.optNullableString("role_name"),
                            status = pStatus,
                            joinedAt = pObj.optNullableString("joined_at") ?: nowIso(),
                            assignedBy = pObj.optNullableString("assigned_by")
                        )
                        taskParts.add(part)
                        if (pUserId == currentUserId && pStatus == "active") {
                            myParticipantStatus = "active"
                            myRoleName = part.roleName
                        }
                    }
                }
                participantsStore[taskId] = taskParts

                // Parse role requirements
                val reqsJson = taskJson.optJSONArray("task_role_requirements")
                val reqsList = mutableListOf<TaskRoleRequirement>()
                if (reqsJson != null) {
                    for (i in 0 until reqsJson.length()) {
                        val rObj = reqsJson.getJSONObject(i)
                        reqsList.add(
                            TaskRoleRequirement(
                                id = rObj.optString("id", UUID.randomUUID().toString()),
                                taskId = taskId,
                                roleId = rObj.optString("role_id"),
                                requiredCount = rObj.optInt("required_count", 1),
                                roleName = rObj.optString("role_name", "Role"),
                                filledCount = rObj.optInt("filled_count", 0)
                            )
                        )
                    }
                }
                requirementsStore[taskId] = reqsList

                // Parse advance reminders
                val advCsv = try {
                    val advArr = taskJson.optJSONArray("advance_reminders_minutes")
                    if (advArr != null) {
                        val nums = mutableListOf<Int>()
                        for (i in 0 until advArr.length()) nums.add(advArr.getInt(i))
                        nums.joinToString(",")
                    } else "0,5"
                } catch (_: Exception) { "0,5" }

                // Parse LAN targets
                val lanCsv = try {
                    val lanArr = taskJson.optJSONArray("lan_target_list")
                    if (lanArr != null) {
                        val lans = mutableListOf<String>()
                        for (i in 0 until lanArr.length()) lans.add(lanArr.getString(i))
                        lans.joinToString(",")
                    } else ""
                } catch (_: Exception) { "" }

                val entity = TaskEntity(
                    id = taskId,
                    groupId = taskJson.optString("group_id"),
                    groupTitle = groupObj.optNullableString("title"),
                    shiftId = taskJson.optNullableString("shift_id"),
                    shiftName = shiftObj.optNullableString("name"),
                    title = taskJson.optNullableString("title") ?: "Untitled Task",
                    description = taskJson.optNullableString("description"),
                    location = taskJson.optNullableString("location"),
                    startsAt = taskJson.optNullableString("starts_at"),
                    dueAt = taskJson.optNullableString("due_at"),
                    priority = taskJson.optString("priority", "normal"),
                    status = taskJson.optString("status", "open"),
                    visibility = taskJson.optString("visibility", "everyone"),
                    allowSelfJoin = taskJson.optBoolean("allow_self_join", true),
                    joinRequiresApproval = taskJson.optBoolean("join_requires_approval", true),
                    notifyCreatorOnJoin = taskJson.optBoolean("notify_creator_on_join", true),
                    requiresCompletionConfirmation = taskJson.optBoolean("requires_completion_confirmation", false),
                    maxParticipants = if (taskJson.has("max_participants") && !taskJson.isNull("max_participants")) taskJson.getInt("max_participants") else null,
                    createdBy = taskJson.optString("created_by"),
                    creatorName = taskJson.optNullableString("creator_name"),
                    createdAt = taskJson.optNullableString("created_at") ?: nowIso(),
                    updatedAt = taskJson.optNullableString("updated_at") ?: nowIso(),
                    taskType = taskJson.optString("task_type", "SIMPLE"),
                    importance = taskJson.optString("importance", "NORMAL"),
                    urgencyType = taskJson.optString("urgency_type", "BEFORE_DEADLINE"),
                    executionTime = taskJson.optNullableString("execution_time"),
                    targetPercentage = if (taskJson.has("target_percentage") && !taskJson.isNull("target_percentage")) taskJson.getInt("target_percentage") else 100,
                    repeatIntervalMinutes = if (taskJson.has("repeat_interval_minutes") && !taskJson.isNull("repeat_interval_minutes")) taskJson.getInt("repeat_interval_minutes") else null,
                    alarmType = taskJson.optString("alarm_type", "SOUND_ALERT"),
                    advanceRemindersMinutesCsv = advCsv,
                    remindUntilCompleted = taskJson.optBoolean("remind_until_completed", false),
                    overdueReminderIntervalMinutes = if (taskJson.has("overdue_reminder_interval_minutes") && !taskJson.isNull("overdue_reminder_interval_minutes")) taskJson.getInt("overdue_reminder_interval_minutes") else 5,
                    maxOverdueReminders = if (taskJson.has("max_overdue_reminders") && !taskJson.isNull("max_overdue_reminders")) taskJson.getInt("max_overdue_reminders") else 3,
                    responseType = taskJson.optString("response_type", "COMPLETED_NOT_COMPLETED"),
                    lanTargetListCsv = lanCsv,
                    currentProgress = taskJson.optInt("current_progress", 0),
                    lastProgressUpdate = taskJson.optNullableString("last_progress_update"),
                    isAcknowledged = taskJson.optBoolean("is_acknowledged", false),
                    acknowledgedAt = taskJson.optNullableString("acknowledged_at"),
                    completionPolicy = taskJson.optString("completion_policy", "SINGLE_ASSIGNEE"),
                    responseFormSchemaJson = taskJson.optNullableString("response_form_schema")?.takeIf { it != "[]" }
                )
                entities.add(entity)

                val domain = entity.toDomain().copy(
                    currentUserParticipantStatus = myParticipantStatus,
                    currentUserRoleName = myRoleName
                )
                domainTasks.add(domain)

                // Schedule alarms if task is active and assigned to current user or created by current user
                if (domain.isAssignedToMe || domain.createdBy == currentUserId) {
                    try {
                        taskAlarmScheduler.scheduleAlarmsForTask(domain)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to schedule alarm for synced task: ${e.message}")
                    }
                }
            }

            taskDao.insertTasks(entities)
            // Also pull latest task responses from server so teammates' updates are visible
            runCatching { syncResponsesFromServer(token) }
            Result.success(domainTasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTaskResponses(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        var token = getActiveAccessToken() ?: return@withContext Result.failure(IOException("No active session"))
        var res = syncResponsesFromServer(token, taskId)
        if (res.isFailure) {
            val errMsg = res.exceptionOrNull()?.message.orEmpty()
            if (isAuthExpiredError(errMsg)) {
                val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    res = syncResponsesFromServer(token, taskId)
                }
            }
        }
        res
    }

    suspend fun syncResponsesFromServer(token: String, taskId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = supabaseClient.fetchTaskResponses(token, taskId)
            if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)
            val remoteResponses = res.getOrDefault(emptyList())
            if (remoteResponses.isNotEmpty()) {
                val pendingIds = taskResponseDao.getPendingSyncResponses().map { it.id }.toSet()
                val toInsert = remoteResponses
                    .filter { it.id.isNotBlank() && it.id !in pendingIds }
                    .map { TaskResponseEntity.fromDomain(it, syncStatus = "SYNCED") }
                if (toInsert.isNotEmpty()) {
                    taskResponseDao.insertResponses(toInsert)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isAuthExpiredError(msg: String?): Boolean {
        if (msg.isNullOrBlank()) return false
        return msg.contains("JWT", ignoreCase = true) ||
            msg.contains("401", ignoreCase = true) ||
            msg.contains("Unauthorized", ignoreCase = true)
    }

    suspend fun syncGroupsFromServer(token: String): Result<List<TaskGroup>> = withContext(Dispatchers.IO) {
        try {
            val remoteRes = supabaseClient.fetchGroups(token)
            if (remoteRes.isFailure) return@withContext Result.failure(remoteRes.exceptionOrNull()!!)
            val groupsJson = remoteRes.getOrDefault(emptyList())
            val entities = mutableListOf<TaskGroupEntity>()

            for (gJson in groupsJson) {
                val gId = gJson.optString("id")
                val entity = TaskGroupEntity(
                    id = gId,
                    title = gJson.optNullableString("title") ?: "Untitled Group",
                    code = gJson.optNullableString("code"),
                    description = gJson.optNullableString("description"),
                    startDate = gJson.optNullableString("start_date"),
                    endDate = gJson.optNullableString("end_date"),
                    status = gJson.optString("status", "active"),
                    visibility = gJson.optString("visibility", "everyone"),
                    allowSelfJoin = gJson.optBoolean("allow_self_join", true),
                    joinRequiresApproval = gJson.optBoolean("join_requires_approval", false),
                    notifyCreatorOnJoin = gJson.optBoolean("notify_creator_on_join", true),
                    maxMembers = if (gJson.has("max_members") && !gJson.isNull("max_members")) gJson.getInt("max_members") else null,
                    createdBy = gJson.optString("created_by"),
                    createdAt = gJson.optString("created_at", nowIso()),
                    updatedAt = gJson.optString("updated_at", nowIso())
                )
                entities.add(entity)

                // Cache members
                val membersArr = gJson.optJSONArray("group_members")
                val mList = mutableListOf<GroupMember>()
                if (membersArr != null) {
                    for (i in 0 until membersArr.length()) {
                        val mObj = membersArr.getJSONObject(i)
                        mList.add(
                            GroupMember(
                                id = mObj.optString("id", UUID.randomUUID().toString()),
                                groupId = gId,
                                userId = mObj.optString("user_id"),
                                employeeId = mObj.optString("employee_id", ""),
                                fullName = mObj.optString("full_name", "Member"),
                                groupRole = mObj.optString("source", "member"),
                                joinedAt = mObj.optString("joined_at", nowIso())
                            )
                        )
                    }
                }
                groupMembersStore[gId] = mList
            }

            taskGroupDao.insertGroups(entities)
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncShiftsFromServer(token: String): Result<List<Shift>> = withContext(Dispatchers.IO) {
        try {
            val remoteRes = supabaseClient.fetchShifts(token)
            if (remoteRes.isFailure) return@withContext Result.failure(remoteRes.exceptionOrNull()!!)
            val shiftsJson = remoteRes.getOrDefault(emptyList())
            val entities = mutableListOf<ShiftEntity>()

            for (sJson in shiftsJson) {
                val entity = ShiftEntity(
                    id = sJson.optString("id"),
                    groupId = sJson.optString("group_id"),
                    name = sJson.optNullableString("name") ?: "Shift",
                    shiftNumber = if (sJson.has("shift_number") && !sJson.isNull("shift_number")) sJson.getInt("shift_number") else null,
                    startsAt = sJson.optString("starts_at"),
                    endsAt = sJson.optString("ends_at"),
                    notes = sJson.optNullableString("notes"),
                    status = sJson.optString("status", "scheduled"),
                    createdBy = sJson.optString("created_by")
                )
                entities.add(entity)
            }

            shiftDao.insertShifts(entities)
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTasks(): Result<List<TaskItem>> {
        var token = getActiveAccessToken() ?: return Result.failure(IOException("No active session. Please sign in again."))
        try {
            syncPendingTasks()
        } catch (_: Exception) {}
        try {
            syncGroupsFromServer(token)
        } catch (_: Exception) {}

        var res = syncTasksFromServer(token)
        if (res.isFailure) {
            val errMsg = res.exceptionOrNull()?.message.orEmpty()
            if (errMsg.contains("JWT", ignoreCase = true) ||
                errMsg.contains("401") ||
                errMsg.contains("Unauthorized", ignoreCase = true)
            ) {
                val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                if (!refreshed.isNullOrBlank()) {
                    token = refreshed
                    res = syncTasksFromServer(token)
                }
            }
        }
        return res
    }

    suspend fun syncGroups(): Result<List<TaskGroup>> {
        val token = getActiveAccessToken() ?: return Result.failure(IOException("No active session"))
        return syncGroupsFromServer(token)
    }

    suspend fun syncShifts(): Result<List<Shift>> {
        val token = getActiveAccessToken() ?: return Result.failure(IOException("No active session"))
        return syncShiftsFromServer(token)
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        description: String?,
        location: String?,
        startsAt: String?,
        dueAt: String?,
        priority: String,
        status: String
    ): Result<TaskItem> = withContext(Dispatchers.IO) {
        try {
            val existing = taskDao.getTaskById(taskId)?.toDomain()
                ?: return@withContext Result.failure(IOException("Task not found"))
            val updated = existing.copy(
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                location = location?.trim()?.takeIf { it.isNotBlank() },
                startsAt = startsAt,
                dueAt = dueAt,
                priority = priority,
                status = status,
                updatedAt = nowIso()
            )
            taskDao.updateTask(TaskEntity.fromDomain(updated))
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // PHASE 3: TIME-CRITICAL RESPONSES & ACKNOWLEDGEMENTS
    // ============================================================

    suspend fun submitTaskResponse(
        taskId: String,
        responseType: ResponseType,
        responseValue: String? = null,
        percentage: Int? = null,
        numeric1: Double? = null,
        numeric2: Double? = null,
        textValue: String? = null,
        lanNumber: String? = null,
        dynamicValues: Map<String, String> = emptyMap()
    ): Result<TaskResponseItem> = withContext(Dispatchers.IO) {
        try {
            val session = userPrefs.getUserSession()
            val user = profileDao.getCachedProfile()
            val userId = session.userId ?: user?.id
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required"))
            }

            val empId = user?.employeeId ?: "EMP"
            val userName = user?.fullName ?: empId
            val nowIso = nowIso()
            val responseId = UUID.randomUUID().toString()

            val task = taskDao.getTaskById(taskId)?.toDomain()
            val isMultiLan = !task?.lanTargetList.isNullOrEmpty() || task?.taskType == TaskType.MULTI_CONFIRMATION

            // Prevent duplicate YES confirmations
            if (responseValue?.equals("YES", ignoreCase = true) == true) {
                val existing = taskResponseDao.getResponsesForTask(taskId)
                if (existing.any { it.userId == userId && it.responseValue.equals("YES", ignoreCase = true) }) {
                    return@withContext Result.failure(IllegalStateException("Confirmation already submitted. Duplicate YES confirmations are not allowed."))
                }
            }

            val item = TaskResponseItem(
                id = responseId,
                taskId = taskId,
                userId = userId,
                employeeId = empId,
                employeeName = userName,
                lanNumber = lanNumber,
                responseType = responseType,
                responseValue = responseValue,
                percentageValue = percentage,
                numericValue1 = numeric1,
                numericValue2 = numeric2,
                textValue = textValue,
                dynamicValues = dynamicValues,
                submittedAt = nowIso,
                isPendingSync = true
            )

            // Cache response locally
            taskResponseDao.insertResponse(TaskResponseEntity.fromDomain(item, syncStatus = "PENDING_SYNC"))

            // Durable Outbox Entry (PART 4)
            val payload = JSONObject().apply {
                put("responseType", responseType.name)
                if (responseValue != null) put("responseValue", responseValue)
                if (percentage != null) put("percentage", percentage)
                if (numeric1 != null) put("numeric1", numeric1)
                if (numeric2 != null) put("numeric2", numeric2)
                if (textValue != null) put("textValue", textValue)
                if (lanNumber != null) put("lanNumber", lanNumber)
                put("deviceTimestamp", nowIso)
                if (dynamicValues.isNotEmpty()) {
                    put("dynamic_values", JSONObject(dynamicValues))
                }
            }

            val outboxEvent = com.example.data.local.entity.OutboxEventEntity(
                eventId = responseId,
                userId = userId,
                taskId = taskId,
                operationType = "TASK_RESPONSE",
                payloadJson = payload.toString(),
                syncStatus = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            val outboxId = TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(outboxEvent)

            // For Progress or Single Assignee (non-multi-LAN), update progress locally
            if (percentage != null && !isMultiLan) {
                taskDao.updateTaskProgress(taskId, percentage, nowIso)
                if (percentage >= (task?.targetPercentage ?: 100)) {
                    if (task?.completionPolicy == CompletionPolicy.CREATOR_CONFIRMATION) {
                        taskDao.updateTaskStatus(taskId, "pending_confirmation")
                    } else {
                        taskDao.updateTaskStatus(taskId, "completed")
                        taskAlarmScheduler.cancelAlarmsForTask(taskId)
                    }
                }
            } else if (responseValue?.equals("YES", ignoreCase = true) == true && !isMultiLan) {
                // Single YES response confirms task complete and cancels repeating/snoozing alarms!
                taskDao.updateTaskStatus(taskId, "completed")
                taskDao.updateTaskProgress(taskId, 100, nowIso)
                taskAlarmScheduler.cancelAlarmsForTask(taskId)
            } else if (responseType == ResponseType.YES_NO && responseValue?.equals("NO", ignoreCase = true) == true) {
                // NO response keeps task active and schedules follow-up reminder using task default (or 10 mins)
                taskDao.updateTaskStatus(taskId, "in_progress")
                val reminderMins = (task?.overdueReminderIntervalMinutes ?: task?.repeatIntervalMinutes ?: 10).coerceAtLeast(1)
                taskAlarmScheduler.scheduleSnoozeAlarm(
                    taskId = taskId,
                    taskTitle = task?.title ?: "Task Reminder",
                    importance = task?.importance?.name ?: "NORMAL",
                    alarmType = task?.alarmType?.name ?: "SOUND_ALERT",
                    snoozeMinutes = reminderMins,
                    userId = userId
                )
            } else if (isMultiLan) {
                // Multi-LAN task: NEVER mark task completed from a single LAN submission!
                taskDao.updateTaskStatus(taskId, "in_progress")
            }

            // Trigger WorkManager sync as backup
            com.example.sync.SyncWorker.enqueueSync(context)

            var finalItem = item
            // Attempt instant sync if online
            var activeToken = getActiveAccessToken()
            if (!activeToken.isNullOrBlank()) {
                var res = supabaseClient.submitTaskResponse(
                    accessToken = activeToken,
                    taskId = taskId,
                    responseType = responseType.name,
                    responseValue = responseValue,
                    percentage = percentage,
                    numeric1 = numeric1,
                    numeric2 = numeric2,
                    text = textValue,
                    lanNumber = lanNumber,
                    idempotencyKey = responseId,
                    deviceTimestamp = nowIso,
                    dynamicValues = dynamicValues
                )
                if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                    val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                    if (!refreshed.isNullOrBlank()) {
                        activeToken = refreshed
                        res = supabaseClient.submitTaskResponse(
                            accessToken = activeToken,
                            taskId = taskId,
                            responseType = responseType.name,
                            responseValue = responseValue,
                            percentage = percentage,
                            numeric1 = numeric1,
                            numeric2 = numeric2,
                            text = textValue,
                            lanNumber = lanNumber,
                            idempotencyKey = responseId,
                            deviceTimestamp = nowIso,
                            dynamicValues = dynamicValues
                        )
                    }
                }
                if (res.isSuccess) {
                    val serverObj = res.getOrThrow()
                    val db = TaskzDatabase.getInstance(context)
                    taskResponseDao.markResponseSynced(responseId)
                    db.outboxDao().markEventSynced(outboxId, responseId, System.currentTimeMillis())

                    val serverStatus = serverObj.optString("task_status")
                    if (serverStatus.isNotBlank()) {
                        taskDao.updateTaskStatus(taskId, serverStatus)
                        if (serverStatus.equals("completed", ignoreCase = true)) {
                            taskAlarmScheduler.cancelAlarmsForTask(taskId)
                        }
                    }
                    val remaining = db.outboxDao().getPendingEventsForUser(userId).size
                    if (remaining == 0) {
                        db.syncMetadataDao().updateLastSuccessfulSync(System.currentTimeMillis())
                        db.syncMetadataDao().updateSyncStatus("SYNCED", "Verified with Server")
                    }
                    finalItem = item.copy(isPendingSync = false)
                } else {
                    val errMsg = res.exceptionOrNull()?.message.orEmpty()
                    Log.w(tag, "Instant response sync deferred/failed: $errMsg")
                    val isPermanent = errMsg.contains("not found", ignoreCase = true) ||
                        errMsg.contains("cancelled", ignoreCase = true) ||
                        errMsg.contains("violates foreign key", ignoreCase = true)
                    if (isPermanent) {
                        TaskzDatabase.getInstance(context).outboxDao().markEventFailed(
                            outboxId, errMsg, "FAILED_PERMANENT", System.currentTimeMillis()
                        )
                    }
                }
            }

            // Activity log
            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = userName,
                    eventType = "RESPONSE_SUBMITTED",
                    message = "$userName submitted response: ${responseValue ?: percentage?.let { "$it%" } ?: textValue ?: "Confirmed"}",
                    createdAt = nowIso
                )
            )

            Result.success(finalItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledgeTaskAlarm(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = userPrefs.getUserSession()
            val user = profileDao.getCachedProfile()
            val userId = session.userId ?: user?.id
            if (userId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required"))
            }

            val empId = user?.employeeId ?: "EMP"
            val userName = user?.fullName ?: empId
            val eventId = UUID.randomUUID().toString()
            val nowIso = nowIso()

            val ack = TaskAcknowledgementEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                userId = userId,
                employeeId = empId,
                eventId = eventId,
                deviceTimestamp = System.currentTimeMillis(),
                acknowledgedAt = nowIso,
                syncStatus = "PENDING_SYNC"
            )
            taskResponseDao.insertAcknowledgement(ack)
            taskDao.markTaskAcknowledged(taskId, true, nowIso)

            val payload = JSONObject().apply {
                put("deviceTimestamp", nowIso)
            }
            val outboxEvent = com.example.data.local.entity.OutboxEventEntity(
                eventId = eventId,
                userId = userId,
                taskId = taskId,
                operationType = "TASK_ACKNOWLEDGEMENT",
                payloadJson = payload.toString(),
                syncStatus = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            val outboxId = TaskzDatabase.getInstance(context).outboxDao().enqueueEvent(outboxEvent)

            // Trigger WorkManager
            com.example.sync.SyncWorker.enqueueSync(context)

            // Attempt remote acknowledgement
            var activeToken = getActiveAccessToken()
            if (!activeToken.isNullOrBlank()) {
                var res = supabaseClient.acknowledgeTaskAlarm(activeToken, taskId, eventId, nowIso)
                if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                    val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                    if (!refreshed.isNullOrBlank()) {
                        activeToken = refreshed
                        res = supabaseClient.acknowledgeTaskAlarm(activeToken, taskId, eventId, nowIso)
                    }
                }
                if (res.isSuccess) {
                    val db = TaskzDatabase.getInstance(context)
                    taskResponseDao.markAcknowledgementSynced(ack.id)
                    db.outboxDao().markEventSynced(outboxId, "ACK_CONFIRMED", System.currentTimeMillis())
                    val remaining = db.outboxDao().getPendingEventsForUser(userId).size
                    if (remaining == 0) {
                        db.syncMetadataDao().updateLastSuccessfulSync(System.currentTimeMillis())
                        db.syncMetadataDao().updateSyncStatus("SYNCED", "Verified with Server")
                    }
                }
            }

            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = userName,
                    eventType = "ALARM_ACKNOWLEDGED",
                    message = "$userName acknowledged task alarm",
                    createdAt = nowIso
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTaskOperationsDashboard(taskId: String): Result<com.example.core.model.TaskOperationsDashboardData> = withContext(Dispatchers.IO) {
        try {
            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val res = supabaseClient.getTaskOperationsDashboard(token, taskId)
                if (res.isSuccess) {
                    return@withContext res
                }
            }

            // Offline / fallback calculation from local cache
            val task = taskDao.getTaskById(taskId)?.toDomain()
                ?: return@withContext Result.failure(IOException("Task not found"))
            val responses = taskResponseDao.getResponsesForTask(taskId).map { it.toDomain() }
            val acks = taskResponseDao.getAcknowledgementsForTaskFlow(taskId).firstOrNull() ?: emptyList()

            val lans = task.lanTargetList.map { lanNum ->
                val lanResp = responses.firstOrNull { it.lanNumber.equals(lanNum, ignoreCase = true) }
                val isCompleted = lanResp?.responseValue in listOf("COMPLETED", "YES") || (lanResp?.percentageValue ?: 0) >= 100
                com.example.core.model.LanStatusItem(
                    lanNumber = lanNum,
                    status = if (isCompleted) "completed" else if (lanResp != null) "in_progress" else "pending",
                    progress = lanResp?.percentageValue ?: (if (isCompleted) 100 else 0),
                    employeeName = lanResp?.employeeName,
                    employeeId = lanResp?.employeeId,
                    lastResponseTime = lanResp?.submittedAt,
                    completedAt = if (isCompleted) lanResp?.submittedAt else null,
                    isOverdue = lanResp?.isLate ?: false
                )
            }

            val completedCount = lans.count { it.status == "completed" }
            val overdueCount = lans.count { it.isOverdue }
            val pendingCount = lans.count { it.status == "pending" }

            val data = com.example.core.model.TaskOperationsDashboardData(
                taskId = task.id,
                title = task.title,
                taskType = task.taskType.name,
                importance = task.importance.name,
                status = task.status,
                completionPolicy = task.completionPolicy.name,
                executionTime = task.executionTime,
                deadlineTime = task.dueAt,
                currentProgress = task.currentProgress,
                totalLans = lans.size,
                completedLans = completedCount,
                pendingLans = pendingCount,
                overdueLans = overdueCount,
                totalParticipants = participantsStore[taskId]?.size ?: 0,
                acknowledgedCount = acks.size,
                lanStatuses = lans,
                recentResponses = responses.take(10)
            )
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getResponsesForTaskFlow(taskId: String): Flow<List<TaskResponseItem>> {
        return taskResponseDao.getResponsesForTaskFlow(taskId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getLanProgressFlow(taskId: String, lanList: List<String>): Flow<List<LanProgressItem>> {
        return taskResponseDao.getResponsesForTaskFlow(taskId).map { responses ->
            val latestByLan = responses.filter { !it.lanNumber.isNullOrBlank() }
                .groupBy { it.lanNumber!! }
                .mapValues { it.value.maxByOrNull { resp -> resp.submittedAt } }

            lanList.map { lan ->
                val latest = latestByLan[lan]
                LanProgressItem(
                    lanNumber = lan,
                    isConfirmed = latest != null,
                    percentage = latest?.percentageValue,
                    numeric1 = latest?.numericValue1,
                    numeric2 = latest?.numericValue2,
                    employeeName = latest?.employeeName,
                    employeeId = latest?.employeeId,
                    submittedAt = latest?.submittedAt
                )
            }
        }
    }

    suspend fun snoozeAlarm(taskId: String, minutes: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val task = taskDao.getTaskById(taskId)?.toDomain()
                ?: return@withContext Result.failure(IOException("Task not found"))
            taskAlarmScheduler.scheduleSnoozeAlarm(
                taskId = task.id,
                taskTitle = task.title,
                importance = task.importance.name,
                alarmType = task.alarmType.name,
                snoozeMinutes = minutes
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // MANPOWER REQUIREMENTS & PARTICIPANTS
    // ============================================================

    suspend fun getTaskRoleRequirements(taskId: String): List<TaskRoleRequirement> = withContext(Dispatchers.IO) {
        val reqs = requirementsStore[taskId]?.toList() ?: emptyList()
        val parts = participantsStore[taskId]?.filter { it.status == "active" } ?: emptyList()

        reqs.map { r ->
            val filled = parts.count { it.roleId == r.roleId }
            r.copy(filledCount = filled)
        }
    }

    suspend fun getTaskParticipants(taskId: String): List<TaskParticipant> = withContext(Dispatchers.IO) {
        participantsStore[taskId]?.toList() ?: emptyList()
    }

    suspend fun getTaskJoinRequests(taskId: String): List<TaskJoinRequest> = withContext(Dispatchers.IO) {
        taskJoinRequestsStore[taskId]?.filter { it.status == "pending" } ?: emptyList()
    }

    suspend fun getTaskActivityLog(taskId: String): List<TaskActivityLog> = withContext(Dispatchers.IO) {
        activityLogStore[taskId]?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    // Task Self-Join
    suspend fun joinTask(taskId: String, roleId: String, message: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val task = getTaskById(taskId) ?: return@withContext Result.failure(IOException("Task not found"))
            val userId = getActiveUserId() ?: return@withContext Result.failure(IOException("Not signed in"))
            val userName = getActiveUserName()
            val userEmpId = getActiveUserEmployeeId()

            if (!task.allowSelfJoin) {
                return@withContext Result.failure(IOException("Self-join is not permitted for this task"))
            }

            // Verify role capacity
            val reqs = getTaskRoleRequirements(taskId)
            val req = reqs.find { it.roleId == roleId }
            if (req != null && req.isFull) {
                return@withContext Result.failure(IOException("This role (${req.roleName}) is already full"))
            }

            val roleName = req?.roleName ?: workRoleDao.getWorkRoleById(roleId)?.name ?: "Role"

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val remoteRes = supabaseClient.requestTaskJoin(token, taskId, roleId, message)
                if (remoteRes.isSuccess) {
                    val serverObj = remoteRes.getOrNull()
                    val status = serverObj?.optString("status", if (task.joinRequiresApproval) "pending" else "active")
                    if (status == "active") {
                        syncTasksFromServer(token)
                        return@withContext Result.success("Joined Task Successfully")
                    } else {
                        return@withContext Result.success("Approval Pending")
                    }
                }
            }

            if (task.joinRequiresApproval) {
                val request = TaskJoinRequest(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    taskTitle = task.title,
                    userId = userId,
                    roleId = roleId,
                    roleName = roleName,
                    message = message?.trim()?.takeIf { it.isNotBlank() },
                    status = "pending",
                    userName = userName,
                    userEmployeeId = userEmpId,
                    createdAt = nowIso()
                )
                taskJoinRequestsStore.getOrPut(taskId) { mutableListOf() }.add(request)

                activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                    TaskActivityLog(
                        id = System.currentTimeMillis(),
                        taskId = taskId,
                        actorName = userName,
                        eventType = "JOIN_REQUESTED",
                        message = "$userName requested to join as $roleName",
                        createdAt = nowIso()
                    )
                )

                Result.success("Approval Pending")
            } else {
                val participant = TaskParticipant(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    userId = userId,
                    roleId = roleId,
                    roleName = roleName,
                    source = "self_join",
                    status = "active",
                    userName = userName,
                    userEmployeeId = userEmpId,
                    joinedAt = nowIso()
                )
                participantsStore.getOrPut(taskId) { mutableListOf() }.add(participant)

                activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                    TaskActivityLog(
                        id = System.currentTimeMillis(),
                        taskId = taskId,
                        actorName = userName,
                        eventType = "USER_JOINED",
                        message = "$userName joined the task as $roleName",
                        createdAt = nowIso()
                    )
                )

                Result.success("Joined Task Successfully")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Review Task Join Request
    suspend fun reviewTaskJoinRequest(requestId: String, approved: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var foundReq: TaskJoinRequest? = null
            var targetTaskId: String? = null

            for ((tid, reqs) in taskJoinRequestsStore) {
                val match = reqs.find { it.id == requestId }
                if (match != null) {
                    foundReq = match
                    targetTaskId = tid
                    break
                }
            }

            if (foundReq == null || targetTaskId == null) {
                return@withContext Result.failure(IOException("Join request not found"))
            }

            val adminName = getActiveUserName()
            val list = taskJoinRequestsStore[targetTaskId]!!
            val index = list.indexOfFirst { it.id == requestId }
            if (index != -1) {
                list[index] = foundReq.copy(
                    status = if (approved) "approved" else "rejected",
                    reviewedBy = getActiveUserId(),
                    reviewedAt = nowIso()
                )
            }

            if (approved) {
                val participant = TaskParticipant(
                    id = UUID.randomUUID().toString(),
                    taskId = targetTaskId,
                    userId = foundReq.userId,
                    roleId = foundReq.roleId,
                    roleName = foundReq.roleName,
                    source = "approved_join",
                    status = "active",
                    userName = foundReq.userName,
                    userEmployeeId = foundReq.userEmployeeId,
                    joinedAt = nowIso()
                )
                participantsStore.getOrPut(targetTaskId) { mutableListOf() }.add(participant)
            }

            activityLogStore.getOrPut(targetTaskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = targetTaskId,
                    actorName = adminName,
                    eventType = if (approved) "REQUEST_APPROVED" else "REQUEST_REJECTED",
                    message = if (approved) "Join request for ${foundReq.userName} approved" else "Join request for ${foundReq.userName} rejected",
                    createdAt = nowIso()
                )
            )

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                supabaseClient.reviewTaskJoin(token, requestId, approved)
                syncTasksFromServer(token)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Manual Employee Assignment by Creator/Admin
    suspend fun assignEmployee(taskId: String, userId: String, roleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val assignerId = getActiveUserId()
            if (assignerId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Authentication required: No active user session found."))
            }

            // Resolve employee identifier (empId or UUID) to profiles.id UUID
            val resolvedRes = resolveToProfileUuid(userId)
            if (resolvedRes.isFailure) {
                return@withContext Result.failure(resolvedRes.exceptionOrNull() ?: IllegalArgumentException("Employee could not be resolved to a profile UUID"))
            }
            val resolvedUserUuid = resolvedRes.getOrThrow()

            val task = getTaskById(taskId) ?: return@withContext Result.failure(IOException("Task not found"))
            val targetProfile = profileDao.findProfileByIdOrEmployeeId(resolvedUserUuid)
                ?: profileDao.searchProfiles("").find { it.id == resolvedUserUuid }

            if (targetProfile != null && !targetProfile.isActive) {
                return@withContext Result.failure(IOException("Employee account is inactive"))
            }

            val parts = participantsStore[taskId] ?: emptyList()
            if (parts.any { it.userId == resolvedUserUuid && it.status == "active" }) {
                return@withContext Result.failure(IOException("Employee is already an active participant in this task"))
            }

            val role = workRoleDao.getWorkRoleById(roleId)?.toDomain()
                ?: WorkRole(id = roleId, name = "Assignee", code = "STAFF")

            val reqs = getTaskRoleRequirements(taskId)
            val req = reqs.find { it.roleId == roleId }
            if (req != null && req.isFull) {
                return@withContext Result.failure(IOException("Role ${role.name} has no available vacancies"))
            }

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank() && com.example.core.network.SupabaseConfig.isConfigured) {
                val remoteRes = supabaseClient.assignUserToTask(token, taskId, resolvedUserUuid, roleId)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    return@withContext Result.failure(IOException(err?.message ?: "Failed to assign user on server", err))
                }
                syncTasksFromServer(token)
            }

            val participant = TaskParticipant(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                userId = resolvedUserUuid,
                roleId = roleId,
                roleName = role.name,
                source = "assigned",
                status = "active",
                assignedBy = assignerId,
                userName = targetProfile?.fullName ?: "Assigned Employee",
                userEmployeeId = targetProfile?.employeeId ?: "",
                joinedAt = nowIso()
            )

            participantsStore.getOrPut(taskId) { mutableListOf() }.add(participant)

            val assignerName = getActiveUserName()
            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = assignerName,
                    eventType = "USER_ASSIGNED",
                    message = "$assignerName assigned ${targetProfile?.fullName ?: "employee"} as ${role.name}",
                    createdAt = nowIso()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignUserToTask(taskId: String, userId: String, roleId: String = "staff"): Result<Unit> =
        assignEmployee(taskId, userId, roleId)

    // Leave Task
    suspend fun leaveTask(taskId: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getActiveUserId() ?: return@withContext Result.failure(IllegalStateException("Authentication required: Not signed in"))
            val userName = getActiveUserName()

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank()) {
                val remoteRes = supabaseClient.leaveTask(token, taskId, reason)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    return@withContext Result.failure(IOException(err?.message ?: "Failed to leave task on server", err))
                }
                syncTasksFromServer(token)
            } else {
                return@withContext Result.failure(IllegalStateException("Authentication required: Missing session token."))
            }

            val parts = participantsStore[taskId]
            val myPart = parts?.find { it.userId == userId && it.status == "active" }
            if (myPart != null) {
                val index = parts.indexOf(myPart)
                parts[index] = myPart.copy(
                    status = "left",
                    leftAt = nowIso(),
                    leftReason = reason?.trim()?.takeIf { it.isNotBlank() }
                )
            }

            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = userName,
                    eventType = "USER_LEFT",
                    message = "$userName left the task${reason?.let { ": $it" } ?: ""}",
                    createdAt = nowIso()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Remove Task Participant (Creator/Admin)
    suspend fun removeParticipant(participantId: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var targetTaskId: String? = null
            var targetPart: TaskParticipant? = null

            for ((tid, parts) in participantsStore) {
                val match = parts.find { it.id == participantId && it.status == "active" }
                if (match != null) {
                    targetPart = match
                    targetTaskId = tid
                    break
                }
            }

            if (targetPart == null || targetTaskId == null) {
                return@withContext Result.failure(IOException("Active participant record not found"))
            }

            val tokenRem = getActiveAccessToken()
            if (!tokenRem.isNullOrBlank()) {
                val remoteRes = supabaseClient.removeParticipant(tokenRem, participantId, reason)
                if (remoteRes.isFailure) {
                    val err = remoteRes.exceptionOrNull()
                    return@withContext Result.failure(IOException(err?.message ?: "Failed to remove participant on server", err))
                }
                syncTasksFromServer(tokenRem)
            } else {
                return@withContext Result.failure(IllegalStateException("Authentication required: Missing session token."))
            }

            val adminName = getActiveUserName()
            val list = participantsStore[targetTaskId]!!
            val index = list.indexOfFirst { it.id == participantId }
            list[index] = targetPart.copy(
                status = "removed",
                leftAt = nowIso(),
                leftReason = reason?.trim()?.takeIf { it.isNotBlank() }
            )

            activityLogStore.getOrPut(targetTaskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = targetTaskId,
                    actorName = adminName,
                    eventType = "USER_REMOVED",
                    message = "$adminName removed ${targetPart.userName}${reason?.let { ": $it" } ?: ""}",
                    createdAt = nowIso()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Completion Workflow
    suspend fun completeTask(taskId: String, note: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val task = getTaskById(taskId) ?: return@withContext Result.failure(IOException("Task not found"))
            val userId = getActiveUserId() ?: return@withContext Result.failure(IllegalStateException("Authentication required: Not signed in"))
            val userName = getActiveUserName()

            if (task.requiresCompletionConfirmation) {
                val req = TaskCompletionRequest(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    requestedBy = userId,
                    requesterName = userName,
                    note = note?.trim()?.takeIf { it.isNotBlank() },
                    status = "pending",
                    createdAt = nowIso()
                )
                completionRequestsStore.getOrPut(taskId) { mutableListOf() }.add(req)

                activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                    TaskActivityLog(
                        id = System.currentTimeMillis(),
                        taskId = taskId,
                        actorName = userName,
                        eventType = "COMPLETION_REQUESTED",
                        message = "$userName marked task completed (Pending Confirmation)",
                        createdAt = nowIso()
                    )
                )

                Result.success("Completion Request Submitted: Pending Manager Confirmation")
            } else {
                val existing = taskDao.getTaskById(taskId)?.toDomain()
                    ?: return@withContext Result.failure(IOException("Task not found"))
                val updated = existing.copy(status = "completed", updatedAt = nowIso())
                taskDao.updateTask(TaskEntity.fromDomain(updated))

                activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                    TaskActivityLog(
                        id = System.currentTimeMillis(),
                        taskId = taskId,
                        actorName = userName,
                        eventType = "TASK_COMPLETED",
                        message = "Task completed directly by $userName",
                        createdAt = nowIso()
                    )
                )

                Result.success("Task Completed")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reviewTaskCompletion(taskId: String, confirmed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val adminName = getActiveUserName()
            val reqs = completionRequestsStore[taskId] ?: emptyList()
            val pending = reqs.find { it.status == "pending" }
                ?: return@withContext Result.failure(IOException("No pending completion request"))

            val list = completionRequestsStore[taskId]!!
            val index = list.indexOf(pending)
            list[index] = pending.copy(
                status = if (confirmed) "confirmed" else "rejected",
                reviewedBy = getActiveUserId(),
                reviewedAt = nowIso()
            )

            if (confirmed) {
                val existing = taskDao.getTaskById(taskId)?.toDomain()
                if (existing != null) {
                    val updated = existing.copy(status = "completed", updatedAt = nowIso())
                    taskDao.updateTask(TaskEntity.fromDomain(updated))
                }
            }

            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = adminName,
                    eventType = if (confirmed) "COMPLETION_CONFIRMED" else "COMPLETION_REJECTED",
                    message = if (confirmed) "Completion confirmed by $adminName" else "Needs More Work: Completion returned by $adminName",
                    createdAt = nowIso()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(taskId: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            taskDao.updateTaskStatus(taskId, status)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitCompletionRequest(taskId: String, note: String?): Result<String> = completeTask(taskId, note)

    suspend fun reviewCompletionRequest(taskId: String, confirmed: Boolean): Result<Unit> = reviewTaskCompletion(taskId, confirmed)

    suspend fun leaveTask(taskId: String): Result<Unit> = leaveTask(taskId, null)

    // Dashboard Stats
    suspend fun fetchDashboardStats(): DashboardStats = withContext(Dispatchers.IO) {
        val userId = getActiveUserId()
        val allTasks = taskDao.getAllTasksFlow().firstOrNull() ?: emptyList()
        var myTasksToday = 0
        var upcomingTasks = 0
        var pendingRequests = 0
        var totalVacancies = 0

        for (taskEntity in allTasks) {
            val domain = taskEntity.toDomain()
            val parts = participantsStore[domain.id]?.filter { it.status == "active" } ?: emptyList()
            val isMyTask = parts.any { it.userId == userId } || (userId != null && domain.createdBy == userId)
            if (isMyTask && domain.status != "completed" && domain.status != "cancelled") {
                myTasksToday++
                upcomingTasks++
            }

            val reqs = taskJoinRequestsStore[domain.id]?.filter { it.status == "pending" } ?: emptyList()
            if (domain.createdBy == userId || isCurrentUserAdmin()) {
                pendingRequests += reqs.size
            }

            val roleReqs = requirementsStore[domain.id] ?: emptyList()
            for (r in roleReqs) {
                val filled = parts.count { it.roleId == r.roleId }
                totalVacancies += maxOf(0, r.requiredCount - filled)
            }
        }

        DashboardStats(
            myTasksToday = myTasksToday,
            upcomingTasks = upcomingTasks,
            pendingRequests = pendingRequests,
            vacancies = totalVacancies
        )
    }

    // Employee Directory Search
    suspend fun searchEmployees(query: String): List<UserProfile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val resultList = mutableListOf<UserProfile>()
        try {
            val localProfiles = profileDao.searchProfiles(trimmed).map { it.toDomain() }
            resultList.addAll(localProfiles)

            val token = getActiveAccessToken()
            if (!token.isNullOrBlank() && com.example.core.network.SupabaseConfig.isConfigured) {
                val remoteRes = supabaseClient.searchEmployeeDirectory(token, trimmed)
                if (remoteRes.isSuccess) {
                    val remoteProfiles = remoteRes.getOrDefault(emptyList())
                    for (p in remoteProfiles) {
                        if (resultList.none { it.id == p.id || it.employeeId.equals(p.employeeId, ignoreCase = true) }) {
                            resultList.add(p)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Search employees error: ${e.message}")
        }
        resultList
    }

    suspend fun syncPendingTasks() = withContext(Dispatchers.IO) {
        val db = TaskzDatabase.getInstance(context)
        val activeUserId = getActiveUserId()
        val pendingEvents = if (!activeUserId.isNullOrBlank()) {
            db.outboxDao().getPendingEventsForUser(activeUserId)
        } else {
            db.outboxDao().getPendingEvents()
        }
        var token = getActiveAccessToken() ?: return@withContext
        if (!com.example.core.network.SupabaseConfig.isConfigured) return@withContext

        val now = System.currentTimeMillis()
        for (ev in pendingEvents) {
            when (ev.operationType) {
                "TASK_CREATION" -> {
                    try {
                        val obj = sanitizeTaskPayloadForSupabase(JSONObject(ev.payloadJson))
                        var res = supabaseClient.createTaskAtomic(token, obj)
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                            if (!refreshed.isNullOrBlank()) {
                                token = refreshed
                                res = supabaseClient.createTaskAtomic(token, obj)
                            }
                        }
                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val serverTaskId = serverObj.optString("task_id", ev.taskId)
                            db.outboxDao().markEventSynced(ev.id, serverTaskId, now)
                            if (!ev.taskId.isNullOrBlank()) {
                                db.taskDao().updateTaskStatus(ev.taskId, "open")
                            }
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Task creation sync error"
                            val isPermanent = err.contains("not authorized", true) || err.contains("inactive", true)
                            db.outboxDao().markEventFailed(ev.id, err, if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE", now)
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to sync pending task ${ev.taskId}: ${e.message}")
                    }
                }
                "TASK_DELETION" -> {
                    try {
                        val targetTaskId = ev.taskId ?: JSONObject(ev.payloadJson).optString("task_id")
                        if (targetTaskId.isNotBlank()) {
                            var res = supabaseClient.deleteTask(token, targetTaskId)
                            if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                                val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                                if (!refreshed.isNullOrBlank()) {
                                    token = refreshed
                                    res = supabaseClient.deleteTask(token, targetTaskId)
                                }
                            }
                            if (res.isSuccess) {
                                db.outboxDao().markEventSynced(ev.id, "DELETED", now)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to sync pending task deletion ${ev.taskId}: ${e.message}")
                    }
                }
                "TASK_RESPONSE", "PROGRESS_UPDATE", "LAN_CONFIRMATION", "COMPLETION_REQUEST" -> {
                    try {
                        val payload = JSONObject(ev.payloadJson)
                        val rType = payload.optString("responseType", "COMPLETED_NOT_COMPLETED")
                        val rVal = payload.optString("responseValue").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val pct = if (payload.has("percentage") && !payload.isNull("percentage")) payload.getInt("percentage") else null
                        val n1 = if (payload.has("numeric1") && !payload.isNull("numeric1")) payload.getDouble("numeric1") else null
                        val n2 = if (payload.has("numeric2") && !payload.isNull("numeric2")) payload.getDouble("numeric2") else null
                        val txt = payload.optString("textValue").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val lan = payload.optString("lanNumber").takeIf { it.isNotBlank() && !it.equals("null", true) }
                        val devTs = payload.optString("deviceTimestamp").takeIf { it.isNotBlank() && !it.equals("null", true) }

                        var res = supabaseClient.submitTaskResponse(
                            accessToken = token,
                            taskId = ev.taskId,
                            responseType = rType,
                            responseValue = rVal,
                            percentage = pct,
                            numeric1 = n1,
                            numeric2 = n2,
                            text = txt,
                            lanNumber = lan,
                            idempotencyKey = ev.eventId,
                            deviceTimestamp = devTs
                        )
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                            if (!refreshed.isNullOrBlank()) {
                                token = refreshed
                                res = supabaseClient.submitTaskResponse(
                                    accessToken = token,
                                    taskId = ev.taskId,
                                    responseType = rType,
                                    responseValue = rVal,
                                    percentage = pct,
                                    numeric1 = n1,
                                    numeric2 = n2,
                                    text = txt,
                                    lanNumber = lan,
                                    idempotencyKey = ev.eventId,
                                    deviceTimestamp = devTs
                                )
                            }
                        }

                        if (res.isSuccess) {
                            val serverObj = res.getOrThrow()
                            val respId = serverObj.optString("response_id", ev.eventId)
                            db.outboxDao().markEventSynced(ev.id, respId, now)
                            db.taskResponseDao().markResponseSynced(ev.eventId)
                            val newStatus = serverObj.optString("task_status")
                            if (newStatus.isNotBlank()) {
                                db.taskDao().updateTaskStatus(ev.taskId, newStatus)
                            }
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Response sync error"
                            val isPermanent = err.contains("not found", true) ||
                                err.contains("cancelled", true) ||
                                err.contains("violates foreign key", true)
                            db.outboxDao().markEventFailed(ev.id, err, if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE", now)
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to sync pending task response ${ev.eventId}: ${e.message}")
                    }
                }
                "TASK_ACKNOWLEDGEMENT" -> {
                    try {
                        val payload = JSONObject(ev.payloadJson)
                        val devTs = payload.optString("deviceTimestamp", nowIso())
                        var res = supabaseClient.acknowledgeTaskAlarm(
                            accessToken = token,
                            taskId = ev.taskId,
                            eventId = ev.eventId,
                            deviceTimestamp = devTs
                        )
                        if (res.isFailure && isAuthExpiredError(res.exceptionOrNull()?.message)) {
                            val refreshed = TaskzApplication.instance.authRepository.forceRefreshAccessToken().getOrNull()
                            if (!refreshed.isNullOrBlank()) {
                                token = refreshed
                                res = supabaseClient.acknowledgeTaskAlarm(
                                    accessToken = token,
                                    taskId = ev.taskId,
                                    eventId = ev.eventId,
                                    deviceTimestamp = devTs
                                )
                            }
                        }
                        if (res.isSuccess) {
                            db.outboxDao().markEventSynced(ev.id, "ACK_CONFIRMED", now)
                            db.taskResponseDao().markAcknowledgementSynced(ev.eventId)
                        } else {
                            val err = res.exceptionOrNull()?.message ?: "Acknowledgement sync error"
                            val isPermanent = err.contains("not found", true) ||
                                err.contains("violates foreign key", true)
                            db.outboxDao().markEventFailed(ev.id, err, if (isPermanent) "FAILED_PERMANENT" else "FAILED_RETRYABLE", now)
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to sync pending task acknowledgement ${ev.eventId}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun sanitizeTaskPayloadForSupabase(obj: JSONObject): JSONObject {
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
            val cleanUsers = JSONArray()
            for (i in 0 until rawUsers.length()) {
                val u = rawUsers.optString(i)
                if (com.example.core.network.SupabaseConfig.isValidUuid(u)) cleanUsers.put(u)
            }
            obj.put("assigned_user_ids", cleanUsers)
        }

        val rawReqs = obj.optJSONArray("requirements")
        if (rawReqs != null) {
            val cleanReqs = JSONArray()
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

    private fun parseDateTimeToMillis(isoString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            format.parse(isoString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(isoString)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun formatMillisToDateTime(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).format(Date(millis))
    }
}
