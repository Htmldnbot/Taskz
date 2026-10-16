package com.example.domain.repository

import android.util.Log
import com.example.core.datastore.UserPreferencesDataStore
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
        seedInitialDemoDataIfEmpty()
    }

    private fun nowIso(): String = isoDateFormat.format(Date())

    private fun seedInitialDemoDataIfEmpty() {
        // Pre-populate realistic work roles and default group/shift if none exist
        val defaultRoles = listOf(
            WorkRole("role_invigilator", "Invigilator", "INV", "Examination hall monitor & invigilation", true, 1),
            WorkRole("role_it_manager", "IT Manager", "ITM", "Technical support and workstation maintenance", true, 2),
            WorkRole("role_security", "Security Guard", "SEC", "Entry/exit security & perimeter monitoring", true, 3),
            WorkRole("role_supervisor", "Floor Supervisor", "SUP", "Operational floor oversight and coordination", true, 4),
            WorkRole("role_coordinator", "Event Coordinator", "CRD", "Event management and participant assistance", true, 5)
        )

        // Seed in-memory activity logs
        val sampleTaskId = "task_demo_1"
        activityLogStore[sampleTaskId] = mutableListOf(
            TaskActivityLog(
                id = 1,
                taskId = sampleTaskId,
                actorName = "System",
                eventType = "TASK_CREATED",
                message = "Task created with manpower requirements",
                createdAt = "2026-10-15T08:00:00Z"
            )
        )
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
            val userId = getActiveUserId() ?: "local_creator"
            val userName = getActiveUserName()
            val userEmpId = getActiveUserEmployeeId()
            val groupId = UUID.randomUUID().toString()
            val group = TaskGroup(
                id = groupId,
                title = title.trim(),
                code = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                startDate = startDate,
                endDate = endDate,
                status = "active",
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

            val userId = getActiveUserId() ?: "creator"
            val shiftId = UUID.randomUUID().toString()
            val shift = Shift(
                id = shiftId,
                groupId = groupId,
                name = name.trim(),
                shiftNumber = shiftNumber,
                startsAt = startsAt,
                endsAt = endsAt,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                status = status,
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
            val userId = getActiveUserId() ?: "creator"

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

            shiftDao.insertShift(ShiftEntity.fromDomain(newShift))

            // Fetch and copy tasks belonging to source shift
            val sourceTasks = taskDao.getTasksForShiftFlow(sourceShiftId).firstOrNull() ?: emptyList()
            for (sourceTaskEntity in sourceTasks) {
                val st = sourceTaskEntity.toDomain()
                val newTaskStart = st.startsAt?.let { formatMillisToDateTime(parseDateTimeToMillis(it) + timeOffsetMillis) }
                val newTaskDue = st.dueAt?.let { formatMillisToDateTime(parseDateTimeToMillis(it) + timeOffsetMillis) }
                val newTaskId = UUID.randomUUID().toString()

                val newTask = st.copy(
                    id = newTaskId,
                    shiftId = newShiftId,
                    shiftName = newShiftName,
                    startsAt = newTaskStart,
                    dueAt = newTaskDue,
                    status = "open",
                    createdAt = nowIso(),
                    updatedAt = nowIso()
                )
                taskDao.insertTask(TaskEntity.fromDomain(newTask))

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

        val myParticipant = activeParts.find { it.userId == userId }
        val myPendingJoin = taskJoinRequestsStore[task.id]?.find { it.userId == userId && it.status == "pending" }
        val isPendingCompletion = completionRequestsStore[task.id]?.any { it.status == "pending" } == true

        var totalReq = 0
        for (r in reqs) totalReq += r.requiredCount
        val totalAssigned = activeParts.size

        return task.copy(
            totalRequired = totalReq,
            totalAssigned = totalAssigned,
            totalVacant = maxOf(0, totalReq - totalAssigned),
            currentUserRoleName = myParticipant?.roleName,
            currentUserParticipantStatus = myParticipant?.status,
            currentUserJoinStatus = myPendingJoin?.status,
            isPendingCompletionConfirmation = isPendingCompletion
        )
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
        lanTargetList: List<String> = emptyList()
    ): Result<TaskItem> = withContext(Dispatchers.IO) {
        try {
            val group = getGroupById(groupId) ?: return@withContext Result.failure(IOException("Group not found"))
            val shift = shiftId?.let { getShiftById(it) }
            val userId = getActiveUserId() ?: "creator"
            val userName = getActiveUserName()

            val taskId = UUID.randomUUID().toString()
            val task = TaskItem(
                id = taskId,
                groupId = groupId,
                groupTitle = group.title,
                shiftId = shiftId,
                shiftName = shift?.name,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                location = location?.trim()?.takeIf { it.isNotBlank() },
                startsAt = startsAt,
                dueAt = dueAt,
                priority = priority.lowercase(),
                status = "open",
                visibility = visibility,
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
                lanTargetList = lanTargetList
            )

            taskDao.insertTask(TaskEntity.fromDomain(task))

            // Schedule device alarms via AlarmManager
            try {
                taskAlarmScheduler.scheduleAlarmsForTask(task)
            } catch (e: Exception) {
                Log.e(tag, "Failed to schedule alarm: ${e.message}")
            }

            // Store visible users if visibility is restricted
            if (visibility == "restricted") {
                taskVisibleUsersStore[taskId] = selectedEmployeeIds.toMutableSet()
            }

            // Store manpower requirements
            val allRoles = workRoleDao.getAllWorkRolesFlow().firstOrNull() ?: emptyList()
            val roleMap = allRoles.associateBy { it.id }

            val reqList = requirements.map { (roleId, count) ->
                val roleName = roleMap[roleId]?.name ?: "Role"
                TaskRoleRequirement(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    roleId = roleId,
                    requiredCount = count,
                    roleName = roleName,
                    filledCount = 0
                )
            }.toMutableList()
            requirementsStore[taskId] = reqList

            // Add activity log
            activityLogStore.getOrPut(taskId) { mutableListOf() }.add(
                TaskActivityLog(
                    id = System.currentTimeMillis(),
                    taskId = taskId,
                    actorName = userName,
                    eventType = "TASK_CREATED",
                    message = "Task created by $userName ($taskType, $importance priority)",
                    createdAt = nowIso()
                )
            )

            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        lanNumber: String? = null
    ): Result<TaskResponseItem> = withContext(Dispatchers.IO) {
        try {
            val user = profileDao.getCachedProfile()
            val empId = user?.employeeId ?: "EMP"
            val userId = user?.id ?: UUID.randomUUID().toString()
            val userName = user?.fullName ?: empId
            val nowIso = nowIso()
            val responseId = UUID.randomUUID().toString()

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
                submittedAt = nowIso,
                isPendingSync = true
            )

            taskResponseDao.insertResponse(TaskResponseEntity.fromDomain(item, syncStatus = "PENDING_SYNC"))

            // If percentage update, update progress on task
            if (percentage != null) {
                taskDao.updateTaskProgress(taskId, percentage, nowIso)
                val task = taskDao.getTaskById(taskId)?.toDomain()
                if (task != null && percentage >= (task.targetPercentage ?: 100)) {
                    taskDao.updateTaskStatus(taskId, "completed")
                    taskAlarmScheduler.cancelAlarmsForTask(taskId)
                }
            }

            // Sync with server if online
            val session = userPrefs.getUserSession()
            if (!session.accessToken.isNullOrBlank()) {
                val res = supabaseClient.submitTaskResponse(
                    accessToken = session.accessToken,
                    taskId = taskId,
                    responseType = responseType.name,
                    responseValue = responseValue,
                    percentage = percentage,
                    numeric1 = numeric1,
                    numeric2 = numeric2,
                    text = textValue,
                    lanNumber = lanNumber,
                    idempotencyKey = responseId
                )
                if (res.isSuccess) {
                    taskResponseDao.markResponseSynced(responseId)
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

            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledgeTaskAlarm(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = profileDao.getCachedProfile()
            val empId = user?.employeeId ?: "EMP"
            val userId = user?.id ?: UUID.randomUUID().toString()
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

            // Attempt remote acknowledgement
            val session = userPrefs.getUserSession()
            if (!session.accessToken.isNullOrBlank()) {
                val res = supabaseClient.acknowledgeTaskAlarm(session.accessToken, taskId, eventId)
                if (res.isSuccess) {
                    taskResponseDao.markAcknowledgementSynced(ack.id)
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

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Manual Employee Assignment by Creator/Admin
    suspend fun assignEmployee(taskId: String, userId: String, roleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val task = getTaskById(taskId) ?: return@withContext Result.failure(IOException("Task not found"))
            val targetProfile = profileDao.searchProfiles("").find { it.id == userId }
                ?: return@withContext Result.failure(IOException("Employee profile not found"))

            if (!targetProfile.isActive) {
                return@withContext Result.failure(IOException("Employee account is inactive"))
            }

            val parts = participantsStore[taskId] ?: emptyList()
            if (parts.any { it.userId == userId && it.status == "active" }) {
                return@withContext Result.failure(IOException("Employee is already an active participant in this task"))
            }

            val role = workRoleDao.getWorkRoleById(roleId)
                ?: return@withContext Result.failure(IOException("Work role not found"))

            val reqs = getTaskRoleRequirements(taskId)
            val req = reqs.find { it.roleId == roleId }
            if (req != null && req.isFull) {
                return@withContext Result.failure(IOException("Role ${role.name} has no available vacancies"))
            }

            val participant = TaskParticipant(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                userId = userId,
                roleId = roleId,
                roleName = role.name,
                source = "assigned",
                status = "active",
                assignedBy = getActiveUserId(),
                userName = targetProfile.fullName,
                userEmployeeId = targetProfile.employeeId,
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
                    message = "$assignerName assigned ${targetProfile.fullName} as ${role.name}",
                    createdAt = nowIso()
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Leave Task
    suspend fun leaveTask(taskId: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getActiveUserId() ?: return@withContext Result.failure(IOException("Not signed in"))
            val userName = getActiveUserName()

            val parts = participantsStore[taskId] ?: return@withContext Result.failure(IOException("No participants found"))
            val myPart = parts.find { it.userId == userId && it.status == "active" }
                ?: return@withContext Result.failure(IOException("You are not an active participant of this task"))

            val index = parts.indexOf(myPart)
            parts[index] = myPart.copy(
                status = "left",
                leftAt = nowIso(),
                leftReason = reason?.trim()?.takeIf { it.isNotBlank() }
            )

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
            val userName = getActiveUserName()

            if (task.requiresCompletionConfirmation) {
                val req = TaskCompletionRequest(
                    id = UUID.randomUUID().toString(),
                    taskId = taskId,
                    requestedBy = getActiveUserId() ?: "user",
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
            val isMyTask = parts.any { it.userId == userId }
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
        try {
            val cachedProfiles = profileDao.searchProfiles(query).map { it.toDomain() }
            if (cachedProfiles.isNotEmpty()) {
                return@withContext cachedProfiles
            }
            // If empty, return sample demo employees for assignment picker
            listOf(
                UserProfile("emp_1", "EMP101", "Rahul Sharma", "+919876543210", null, null, "employee", true),
                UserProfile("emp_2", "EMP102", "Priya Patel", "+919876543211", null, null, "employee", true),
                UserProfile("emp_3", "EMP103", "Amit Verma", "+919876543212", null, null, "employee", true),
                UserProfile("emp_4", "EMP104", "Sneha Rao", "+919876543213", null, null, "employee", true),
                UserProfile("emp_5", "EMP105", "Vikram Singh", "+919876543214", null, null, "employee", true)
            ).filter {
                it.fullName.contains(query, ignoreCase = true) || it.employeeId.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            emptyList()
        }
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
