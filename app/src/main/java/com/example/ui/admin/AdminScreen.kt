package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.example.core.model.AdminAuditEntry
import com.example.core.model.OrgCentre
import com.example.core.model.OrgHierarchyData
import com.example.core.model.PasswordResetRequest
import com.example.core.model.TaskzRole
import com.example.core.model.UserProfile
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    workRolesViewModel: WorkRolesViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val users by viewModel.users.collectAsStateWithLifecycle()
    val requests by viewModel.resetRequests.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchy.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val isLoadingUsers by viewModel.isLoadingUsers.collectAsStateWithLifecycle()
    val isLoadingRequests by viewModel.isLoadingRequests.collectAsStateWithLifecycle()
    val isLoadingAudits by viewModel.isLoadingAudits.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var centreSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCityFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterNeedsScopeReview by rememberSaveable { mutableStateOf(false) }
    var selectedRequestForReset by remember { mutableStateOf<PasswordResetRequest?>(null) }
    var userToManageAccess by remember { mutableStateOf<UserProfile?>(null) }
    var centreToEdit by remember { mutableStateOf<OrgCentre?>(null) }
    var showAddCentreDialog by remember { mutableStateOf(false) }
    var showAddCityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Admin Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Admin Console",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Users & Roles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            viewModel.loadAllUsers()
                            viewModel.loadOrgHierarchy()
                        }
                        1 -> viewModel.loadResetRequests()
                        3 -> viewModel.loadAuditLogs()
                        4 -> viewModel.loadOrgHierarchy()
                        else -> viewModel.loadAllUsers()
                    }
                },
                modifier = Modifier.testTag("admin_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status / Feedback message banner
        AnimatedVisibility(visible = actionState is AdminActionState.Success) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = TaskzSuccess.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TaskzSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = (actionState as? AdminActionState.Success)?.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TaskzSuccess
                    )
                }
            }
        }

        AnimatedVisibility(visible = actionState is AdminActionState.Error) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = (actionState as? AdminActionState.Error)?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Users (${users.size})", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("admin_tab_users")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    val pendingCount = requests.count { it.isPending }
                    Text(
                        text = if (pendingCount > 0) "Reset Requests ($pendingCount)" else "Reset Requests",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag("admin_tab_requests")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Work Roles", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("admin_tab_work_roles")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = {
                    selectedTab = 3
                    viewModel.loadAuditLogs()
                },
                text = { Text("Audit Logs", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("admin_tab_audit_logs")
            )
            Tab(
                selected = selectedTab == 4,
                onClick = {
                    selectedTab = 4
                    if (orgHierarchy.centres.isEmpty()) viewModel.loadOrgHierarchy()
                },
                text = { Text("Cities & Centres (${orgHierarchy.centres.size})", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("admin_tab_centres")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Search Box
            TaskzTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search Employees",
                placeholder = "Search by ID, Name, Email, or Role...",
                leadingIcon = Icons.Default.Search,
                testTag = "admin_search_input"
            )

            val scopeReviewCount = remember(users) { users.count { it.needsScopeReview } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !filterNeedsScopeReview,
                    onClick = { filterNeedsScopeReview = false },
                    label = { Text("All (${users.size})") },
                    modifier = Modifier.testTag("admin_filter_all_users_chip")
                )
                FilterChip(
                    selected = filterNeedsScopeReview,
                    onClick = { filterNeedsScopeReview = !filterNeedsScopeReview },
                    label = { Text("Needs Scope Review ($scopeReviewCount)") },
                    leadingIcon = if (scopeReviewCount > 0) {
                        {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (filterNeedsScopeReview) MaterialTheme.colorScheme.error else TaskzWarning,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    modifier = Modifier.testTag("admin_filter_scope_review_chip")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredUsers = remember(users, searchQuery, filterNeedsScopeReview) {
                users.filter { u ->
                    val matchesReview = !filterNeedsScopeReview || u.needsScopeReview
                    val matchesQuery = searchQuery.isBlank() ||
                        u.employeeId.contains(searchQuery, ignoreCase = true) ||
                        u.fullName.contains(searchQuery, ignoreCase = true) ||
                        (u.email?.contains(searchQuery, ignoreCase = true) == true) ||
                        u.activeRoleCode.contains(searchQuery, ignoreCase = true)
                    matchesReview && matchesQuery
                }
            }

            if (isLoadingUsers) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) "No employees found." else "No match for '$searchQuery'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredUsers, key = { it.id }) { user ->
                        UserAdminCard(
                            user = user,
                            onManageAccess = { userToManageAccess = user },
                            onToggleActive = { viewModel.toggleUserStatus(user) }
                        )
                    }
                }
            }
        } else if (selectedTab == 1) {
            // Password Reset Requests Tab
            if (isLoadingRequests) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No password reset requests pending.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(requests, key = { it.id }) { request ->
                        ResetRequestCard(
                            request = request,
                            onResetClick = { selectedRequestForReset = request }
                        )
                    }
                }
            }
        } else if (selectedTab == 2) {
            // Work Roles Tab
            WorkRolesScreen(viewModel = workRolesViewModel)
        } else if (selectedTab == 3) {
            // Audit Logs Tab
            if (isLoadingAudits && auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            } else if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No governance audit logs recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(auditLogs, key = { it.id }) { entry ->
                        AdminAuditLogCard(entry = entry)
                    }
                }
            }
        } else {
            // Cities, Centres & LANs Management Tab
            OutlinedTextField(
                value = centreSearchQuery,
                onValueChange = { centreSearchQuery = it },
                placeholder = { Text("Search TC code, centre, city, hub...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_centre_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAddCityDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_admin_add_city")
                ) {
                    Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ City")
                }
                Button(
                    onClick = { showAddCentreDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_admin_add_centre")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Centre")
                }
            }

            if (orgHierarchy.cities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val centreCountsByCity = remember(orgHierarchy.centres) {
                    orgHierarchy.centres.groupingBy { it.cityId }.eachCount()
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item(key = "all_cities") {
                        FilterChip(
                            selected = selectedCityFilterId == null,
                            onClick = { selectedCityFilterId = null },
                            label = { Text("All Cities (${orgHierarchy.cities.size})") },
                            modifier = Modifier.testTag("admin_city_filter_all")
                        )
                    }
                    items(orgHierarchy.cities, key = { it.id }) { city ->
                        val count = centreCountsByCity[city.id] ?: 0
                        FilterChip(
                            selected = selectedCityFilterId == city.id,
                            onClick = {
                                selectedCityFilterId = if (selectedCityFilterId == city.id) null else city.id
                            },
                            label = { Text("${city.name} ($count)") },
                            modifier = Modifier.testTag("admin_city_filter_${city.code}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val filteredCentres = remember(orgHierarchy.centres, centreSearchQuery, selectedCityFilterId) {
                val q = centreSearchQuery.trim()
                orgHierarchy.centres.filter { c ->
                    val matchesCity = selectedCityFilterId == null || c.cityId == selectedCityFilterId
                    val matchesQuery = q.isBlank() ||
                        c.code.contains(q, ignoreCase = true) ||
                        c.name.contains(q, ignoreCase = true) ||
                        (c.districtName?.contains(q, ignoreCase = true) == true) ||
                        (c.subHub?.contains(q, ignoreCase = true) == true)
                    matchesCity && matchesQuery
                }.sortedBy { it.code.toIntOrNull() ?: Int.MAX_VALUE }
            }

            if (filteredCentres.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (centreSearchQuery.isBlank() && selectedCityFilterId == null) {
                            "No centres loaded yet."
                        } else {
                            "No centres in this selection. Tap '+ Centre' to add one."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredCentres, key = { it.id }) { centre ->
                        OrgCentreAdminCard(
                            centre = centre,
                            onEditClick = { centreToEdit = centre }
                        )
                    }
                }
            }
        }
    }

    if (selectedRequestForReset != null) {
        ResetPasswordModal(
            request = selectedRequestForReset!!,
            onDismiss = { selectedRequestForReset = null },
            onConfirm = { tempPwd ->
                viewModel.executeResetPassword(
                    requestId = selectedRequestForReset!!.id,
                    employeeId = selectedRequestForReset!!.employeeId,
                    temporaryPassword = tempPwd
                )
                selectedRequestForReset = null
            }
        )
    }

    if (userToManageAccess != null) {
        UserAccessEditorDialog(
            user = userToManageAccess!!,
            orgHierarchy = orgHierarchy,
            onDismiss = { userToManageAccess = null },
            onSave = { roles, activeRole, zoneId, stateId, cityId, centreId, isActive ->
                viewModel.updateUserAccess(
                    targetUser = userToManageAccess!!,
                    roleCodes = roles,
                    activeRoleCode = activeRole,
                    zoneId = zoneId,
                    stateId = stateId,
                    cityId = cityId,
                    centreId = centreId,
                    isActive = isActive
                )
                userToManageAccess = null
            }
        )
    }

    if (showAddCityDialog) {
        AddOrgCityDialog(
            orgHierarchy = orgHierarchy,
            onDismiss = { showAddCityDialog = false },
            onSave = { cityName, cityCode, stateId, stateName, zoneId ->
                viewModel.upsertOrgCity(
                    code = cityCode,
                    name = cityName,
                    stateId = stateId,
                    stateName = stateName,
                    zoneId = zoneId,
                    onSuccess = {
                        showAddCityDialog = false
                    }
                )
            }
        )
    }

    if (showAddCentreDialog || centreToEdit != null) {
        AddEditOrgCentreDialog(
            existing = centreToEdit,
            defaultCityId = selectedCityFilterId,
            orgHierarchy = orgHierarchy,
            onDismiss = {
                showAddCentreDialog = false
                centreToEdit = null
            },
            onSave = { code, name, cityId, districtName, stateName, subHub, address, lans ->
                viewModel.upsertOrgCentre(
                    id = centreToEdit?.id,
                    code = code,
                    name = name,
                    cityId = cityId,
                    districtName = districtName,
                    address = address,
                    subHub = subHub,
                    availableLans = lans,
                    stateName = stateName,
                    onSuccess = {
                        showAddCentreDialog = false
                        centreToEdit = null
                    }
                )
            }
        )
    }
}

@Composable
private fun UserAdminCard(
    user: UserProfile,
    onManageAccess: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val photoModel = user.avatarSignedUrl
                if (!photoModel.isNullOrBlank()) {
                    AsyncImage(
                        model = photoModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.fullName.take(2).uppercase().ifBlank { "EM" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (user.isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = user.resolvedActiveRole.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (user.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = listOfNotNull(
                            "ID: ${user.employeeId}",
                            user.mobile.takeIf { it.isNotBlank() },
                            user.centreCode?.let { "Centre $it" } ?: user.cityName ?: user.stateName
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (user.assignedRoleCodes.isNotEmpty()) {
                        Text(
                            text = "Assigned: ${user.assignedRoleCodes.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (user.isOnLeave) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TaskzWarning.copy(alpha = 0.18f),
                            modifier = Modifier.testTag("badge_admin_on_leave_${user.employeeId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = "On Leave",
                                    tint = TaskzWarning,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "On Leave",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TaskzWarning
                                )
                            }
                        }
                    }

                    if (user.needsScopeReview) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.testTag("badge_scope_review_${user.employeeId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Scope Review Needed",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Needs Scope Review",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onManageAccess,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_manage_access_${user.employeeId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Roles & Scope")
                }

                Button(
                    onClick = onToggleActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isActive) TaskzError.copy(alpha = 0.15f) else TaskzSuccess.copy(alpha = 0.15f),
                        contentColor = if (user.isActive) TaskzError else TaskzSuccess
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("toggle_status_${user.employeeId}")
                ) {
                    Text(
                        text = if (user.isActive) "Deactivate" else "Activate",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAuditLogCard(entry: AdminAuditEntry) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = entry.action,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = entry.createdAt.take(19).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "By ${entry.adminName ?: "Admin"} -> ${entry.targetName ?: entry.targetEmployeeId ?: entry.targetUserId ?: "System"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (entry.metadataJson.isNotBlank() && entry.metadataJson != "{}") {
                Text(
                    text = entry.metadataJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAccessEditorDialog(
    user: UserProfile,
    orgHierarchy: OrgHierarchyData,
    onDismiss: () -> Unit,
    onSave: (
        roles: List<String>,
        activeRole: String?,
        zoneId: String?,
        stateId: String?,
        cityId: String?,
        centreId: String?,
        isActive: Boolean
    ) -> Unit
) {
    val selectedRoles = remember(user.id) {
        mutableStateOf(
            user.assignedRoleCodes
                .map { TaskzRole.fromCode(it).code }
                .ifEmpty { listOf(user.resolvedActiveRole.code) }
                .toSet()
        )
    }
    var activeRoleCode by remember(user.id) { mutableStateOf(user.resolvedActiveRole.code) }
    var selectedZoneId by remember(user.id) { mutableStateOf(user.zoneId) }
    var selectedStateId by remember(user.id) { mutableStateOf(user.stateId) }
    var selectedCityId by remember(user.id) { mutableStateOf(user.cityId) }
    var selectedCentreId by remember(user.id) { mutableStateOf(user.centreId) }
    var isActive by remember(user.id) { mutableStateOf(user.isActive) }

    var activeRoleExpanded by remember { mutableStateOf(false) }
    var zoneExpanded by remember { mutableStateOf(false) }
    var stateExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var centreExpanded by remember { mutableStateOf(false) }

    val resolvedActiveRole = TaskzRole.fromCode(activeRoleCode)
    val needsZone = resolvedActiveRole !in setOf(TaskzRole.COMMAND_CENTER, TaskzRole.SUPER_ADMIN)
    val needsState = resolvedActiveRole in setOf(TaskzRole.ROM, TaskzRole.CITY_HEAD, TaskzRole.VCO, TaskzRole.IT_MANAGER)
    val needsCity = resolvedActiveRole in setOf(TaskzRole.CITY_HEAD, TaskzRole.VCO, TaskzRole.IT_MANAGER)
    val needsCentre = resolvedActiveRole in setOf(TaskzRole.VCO, TaskzRole.IT_MANAGER)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Manage Roles & Scope", fontWeight = FontWeight.Bold)
                Text(
                    text = "${user.fullName} (${user.employeeId})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Assigned Roles", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TaskzRole.entries.forEach { role ->
                    val checked = selectedRoles.value.contains(role.code)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                val next = if (isChecked) {
                                    selectedRoles.value + role.code
                                } else {
                                    selectedRoles.value - role.code
                                }
                                selectedRoles.value = next
                                if (!next.contains(activeRoleCode) && next.isNotEmpty()) {
                                    activeRoleCode = next.first()
                                }
                            }
                        )
                        Text(role.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = activeRoleExpanded,
                    onExpandedChange = { activeRoleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = resolvedActiveRole.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Active Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = activeRoleExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = activeRoleExpanded,
                        onDismissRequest = { activeRoleExpanded = false }
                    ) {
                        selectedRoles.value.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(TaskzRole.fromCode(code).displayName) },
                                onClick = {
                                    activeRoleCode = code
                                    activeRoleExpanded = false
                                }
                            )
                        }
                    }
                }

                if (!needsZone) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Global Access (All Zones, States, Cities & Centres)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Zone Dropdown
                if (needsZone) {
                    ExposedDropdownMenuBox(
                        expanded = zoneExpanded,
                        onExpandedChange = { zoneExpanded = it }
                    ) {
                        val zoneLabel = orgHierarchy.zones.firstOrNull { it.id == selectedZoneId }?.name ?: "None"
                        OutlinedTextField(
                            value = zoneLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Zone") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = zoneExpanded, onDismissRequest = { zoneExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedZoneId = null
                                    selectedStateId = null
                                    selectedCityId = null
                                    selectedCentreId = null
                                    zoneExpanded = false
                                }
                            )
                            orgHierarchy.zones.forEach { z ->
                                DropdownMenuItem(
                                    text = { Text("${z.name} (${z.code})") },
                                    onClick = {
                                        selectedZoneId = z.id
                                        zoneExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // State Dropdown
                if (needsState) {
                    val filteredStates = orgHierarchy.states.filter { selectedZoneId == null || it.zoneId == selectedZoneId }
                    ExposedDropdownMenuBox(
                        expanded = stateExpanded,
                        onExpandedChange = { stateExpanded = it }
                    ) {
                        val stateLabel = orgHierarchy.states.firstOrNull { it.id == selectedStateId }?.name ?: "None"
                        OutlinedTextField(
                            value = stateLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("State") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedStateId = null
                                    selectedCityId = null
                                    selectedCentreId = null
                                    stateExpanded = false
                                }
                            )
                            filteredStates.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.name} (${st.code})") },
                                    onClick = {
                                        selectedStateId = st.id
                                        selectedZoneId = st.zoneId
                                        stateExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // City Dropdown
                if (needsCity) {
                    val filteredCities = orgHierarchy.cities.filter {
                        (selectedStateId == null || it.stateId == selectedStateId) &&
                            (selectedZoneId == null || it.zoneId == selectedZoneId)
                    }
                    ExposedDropdownMenuBox(
                        expanded = cityExpanded,
                        onExpandedChange = { cityExpanded = it }
                    ) {
                        val cityLabel = orgHierarchy.cities.firstOrNull { it.id == selectedCityId }?.name ?: "None"
                        OutlinedTextField(
                            value = cityLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("City") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedCityId = null
                                    selectedCentreId = null
                                    cityExpanded = false
                                }
                            )
                            filteredCities.forEach { ct ->
                                DropdownMenuItem(
                                    text = { Text("${ct.name} (${ct.code})") },
                                    onClick = {
                                        selectedCityId = ct.id
                                        selectedStateId = ct.stateId
                                        selectedZoneId = ct.zoneId
                                        cityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Centre Dropdown
                if (needsCentre) {
                    val filteredCentres = orgHierarchy.centres.filter {
                        (selectedCityId == null || it.cityId == selectedCityId) &&
                            (selectedStateId == null || it.stateId == selectedStateId) &&
                            (selectedZoneId == null || it.zoneId == selectedZoneId)
                    }
                    ExposedDropdownMenuBox(
                        expanded = centreExpanded,
                        onExpandedChange = { centreExpanded = it }
                    ) {
                        val centreLabel = orgHierarchy.centres.firstOrNull { it.id == selectedCentreId }?.let { "${it.code} · ${it.name}" } ?: "None"
                        OutlinedTextField(
                            value = centreLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Test Centre") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = centreExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = centreExpanded, onDismissRequest = { centreExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedCentreId = null
                                    centreExpanded = false
                                }
                            )
                            filteredCentres.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.code} · ${c.name}") },
                                    onClick = {
                                        selectedCentreId = c.id
                                        selectedCityId = c.cityId
                                        selectedStateId = c.stateId
                                        selectedZoneId = c.zoneId
                                        centreExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Active", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedRoles.value.toList(),
                        activeRoleCode,
                        if (needsZone) selectedZoneId else null,
                        if (needsState) selectedStateId else null,
                        if (needsCity) selectedCityId else null,
                        if (needsCentre) selectedCentreId else null,
                        isActive
                    )
                },
                enabled = selectedRoles.value.isNotEmpty(),
                modifier = Modifier.testTag("btn_save_user_access")
            ) {
                Text("Save Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ResetRequestCard(
    request: PasswordResetRequest,
    onResetClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Employee ID: ${request.employeeId}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!request.employeeName.isNullOrBlank()) {
                        Text(
                            text = request.employeeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (request.status) {
                        "pending" -> TaskzWarning.copy(alpha = 0.15f)
                        "resolved" -> TaskzSuccess.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = request.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (request.status) {
                            "pending" -> TaskzWarning
                            "resolved" -> TaskzSuccess
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mobile: ${request.mobile}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!request.message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${request.message}\"",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (request.isPending) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onResetClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_reset_button_${request.employeeId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Issue Temporary Password")
                }
            }
        }
    }
}

@Composable
private fun ResetPasswordModal(
    request: PasswordResetRequest,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var temporaryPassword by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Assign a new temporary password for Employee ID: ${request.employeeId}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                TaskzTextField(
                    value = temporaryPassword,
                    onValueChange = {
                        temporaryPassword = it
                        errorText = null
                    },
                    label = "Temporary Password",
                    placeholder = "Minimum 6 characters",
                    isPassword = true,
                    errorMessage = errorText,
                    testTag = "admin_temp_password_input"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (temporaryPassword.length < 6) {
                        errorText = "Password must be at least 6 characters"
                        return@Button
                    }
                    onConfirm(temporaryPassword)
                },
                modifier = Modifier.testTag("admin_confirm_temp_password_button")
            ) {
                Text("Execute Server Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrgCentreAdminCard(
    centre: OrgCentre,
    onEditClick: () -> Unit
) {
    val lans = remember(centre) { centre.resolvedLans() }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_centre_card_${centre.code}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${centre.code} · ${centre.name}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    val meta = listOfNotNull(
                        centre.districtName?.takeIf { it.isNotBlank() },
                        centre.subHub?.takeIf { it.isNotBlank() }?.let { "Hub: $it" }
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("btn_edit_centre_${centre.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Centre",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!centre.address.isNullOrBlank()) {
                Text(
                    text = centre.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lans.forEach { lan ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = lan,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrgCityDialog(
    orgHierarchy: OrgHierarchyData,
    onDismiss: () -> Unit,
    onSave: (
        cityName: String,
        cityCode: String?,
        stateId: String?,
        stateName: String?,
        zoneId: String?
    ) -> Unit
) {
    var cityName by remember { mutableStateOf("") }
    var cityCode by remember { mutableStateOf("") }
    var useNewState by remember { mutableStateOf(orgHierarchy.states.isEmpty()) }
    var selectedStateId by remember { mutableStateOf(orgHierarchy.states.firstOrNull()?.id) }
    var newStateName by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf(orgHierarchy.zones.firstOrNull()?.id) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }
    var zoneDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add City", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("City Name *") },
                    placeholder = { Text("e.g., Alwar, Sikar, Gurugram") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_city_name_input")
                )
                OutlinedTextField(
                    value = cityCode,
                    onValueChange = { cityCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                    label = { Text("City Code (Optional)") },
                    placeholder = { Text("e.g., ALW, SKR (auto-generated if blank)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_city_code_input")
                )

                if (orgHierarchy.states.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = stateDropdownExpanded,
                        onExpandedChange = { stateDropdownExpanded = it }
                    ) {
                        val stateLabel = if (useNewState) {
                            "+ New State"
                        } else {
                            orgHierarchy.states.firstOrNull { it.id == selectedStateId }?.name ?: "Select State"
                        }
                        OutlinedTextField(
                            value = stateLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("State") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = stateDropdownExpanded,
                            onDismissRequest = { stateDropdownExpanded = false }
                        ) {
                            orgHierarchy.states.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.name} (${st.code})") },
                                    onClick = {
                                        useNewState = false
                                        selectedStateId = st.id
                                        selectedZoneId = st.zoneId
                                        stateDropdownExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ New State", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    useNewState = true
                                    selectedStateId = null
                                    stateDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (useNewState || orgHierarchy.states.isEmpty()) {
                    OutlinedTextField(
                        value = newStateName,
                        onValueChange = { newStateName = it },
                        label = { Text("New State Name *") },
                        placeholder = { Text("e.g., Haryana, Delhi, Uttar Pradesh") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_new_state_name_input")
                    )
                    if (orgHierarchy.zones.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = zoneDropdownExpanded,
                            onExpandedChange = { zoneDropdownExpanded = it }
                        ) {
                            val zoneLabel = orgHierarchy.zones.firstOrNull { it.id == selectedZoneId }?.name ?: "Select Zone"
                            OutlinedTextField(
                                value = zoneLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Zone") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = zoneDropdownExpanded,
                                onDismissRequest = { zoneDropdownExpanded = false }
                            ) {
                                orgHierarchy.zones.forEach { zone ->
                                    DropdownMenuItem(
                                        text = { Text(zone.name) },
                                        onClick = {
                                            selectedZoneId = zone.id
                                            zoneDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val stateValid = !useNewState || newStateName.isNotBlank()
            Button(
                enabled = cityName.isNotBlank() && stateValid,
                onClick = {
                    val resolvedStateName = if (useNewState || orgHierarchy.states.isEmpty()) {
                        newStateName.trim()
                    } else {
                        orgHierarchy.states.firstOrNull { it.id == selectedStateId }?.name
                    }
                    onSave(
                        cityName.trim(),
                        cityCode.trim().takeIf { it.isNotBlank() },
                        if (useNewState) null else selectedStateId,
                        resolvedStateName,
                        selectedZoneId
                    )
                },
                modifier = Modifier.testTag("confirm_save_admin_city_btn")
            ) {
                Text("Save City")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditOrgCentreDialog(
    existing: OrgCentre?,
    defaultCityId: String?,
    orgHierarchy: OrgHierarchyData,
    onDismiss: () -> Unit,
    onSave: (
        code: String,
        name: String,
        cityId: String?,
        districtName: String?,
        stateName: String?,
        subHub: String?,
        address: String?,
        lans: List<String>
    ) -> Unit
) {
    val initialCityId = existing?.cityId ?: defaultCityId ?: orgHierarchy.cities.firstOrNull()?.id
    var code by remember(existing?.id) { mutableStateOf(existing?.code.orEmpty()) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var useNewCity by remember(existing?.id) { mutableStateOf(orgHierarchy.cities.isEmpty()) }
    var selectedCityId by remember(existing?.id) { mutableStateOf(initialCityId) }
    var districtName by remember(existing?.id) {
        mutableStateOf(
            existing?.districtName
                ?: orgHierarchy.cities.firstOrNull { it.id == initialCityId }?.name
                ?: ""
        )
    }
    var useNewState by remember(existing?.id) { mutableStateOf(orgHierarchy.states.isEmpty()) }
    var selectedStateId by remember(existing?.id) {
        mutableStateOf(
            existing?.stateId
                ?: orgHierarchy.cities.firstOrNull { it.id == initialCityId }?.stateId
                ?: orgHierarchy.states.firstOrNull()?.id
        )
    }
    var newStateName by remember(existing?.id) { mutableStateOf("") }
    var subHub by remember(existing?.id) { mutableStateOf(existing?.subHub.orEmpty()) }
    var address by remember(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var lansInput by remember(existing?.id) {
        mutableStateOf(
            existing?.let { OrgCentre.formatLansCsv(it.resolvedLans()) } ?: "LAN 1, LAN 2"
        )
    }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }

    val parsedPreview = remember(lansInput) { OrgCentre.parseLansInput(lansInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Add Exam Centre" else "Edit Exam Centre",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (existing == null) code = it.trim() },
                    readOnly = existing != null,
                    label = { Text("TC Code *") },
                    placeholder = { Text("e.g., 41683") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_centre_code_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("TC Name *") },
                    placeholder = { Text("e.g., iON Digital Zone iDZ Sitapura 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_centre_name_input")
                )

                if (orgHierarchy.cities.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = cityDropdownExpanded,
                        onExpandedChange = { cityDropdownExpanded = it }
                    ) {
                        val cityLabel = if (useNewCity) {
                            "+ New City"
                        } else {
                            orgHierarchy.cities.firstOrNull { it.id == selectedCityId }?.name ?: "Select City"
                        }
                        OutlinedTextField(
                            value = cityLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("City") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = cityDropdownExpanded,
                            onDismissRequest = { cityDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "+ New City",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    useNewCity = true
                                    selectedCityId = null
                                    districtName = ""
                                    cityDropdownExpanded = false
                                }
                            )
                            orgHierarchy.cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text("${city.name} (${city.code})") },
                                    onClick = {
                                        useNewCity = false
                                        selectedCityId = city.id
                                        selectedStateId = city.stateId
                                        useNewState = false
                                        districtName = city.name
                                        cityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (useNewCity || orgHierarchy.cities.isEmpty()) {
                    if (orgHierarchy.states.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = stateDropdownExpanded,
                            onExpandedChange = { stateDropdownExpanded = it }
                        ) {
                            val stLabel = if (useNewState) {
                                "+ New State"
                            } else {
                                orgHierarchy.states.firstOrNull { it.id == selectedStateId }?.name ?: "Select State"
                            }
                            OutlinedTextField(
                                value = stLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("State") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = stateDropdownExpanded,
                                onDismissRequest = { stateDropdownExpanded = false }
                            ) {
                                orgHierarchy.states.forEach { st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.name} (${st.code})") },
                                        onClick = {
                                            useNewState = false
                                            selectedStateId = st.id
                                            stateDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("+ New State", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        useNewState = true
                                        selectedStateId = null
                                        stateDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (useNewState || orgHierarchy.states.isEmpty()) {
                        OutlinedTextField(
                            value = newStateName,
                            onValueChange = { newStateName = it },
                            label = { Text("New State Name *") },
                            placeholder = { Text("e.g., Haryana, Delhi") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = districtName,
                    onValueChange = {
                        districtName = it
                        val matched = orgHierarchy.cities.firstOrNull { c -> c.name.equals(it.trim(), ignoreCase = true) }
                        if (matched == null && it.isNotBlank()) {
                            useNewCity = true
                            selectedCityId = null
                        }
                    },
                    label = { Text(if (useNewCity) "New City Name *" else "City / District") },
                    placeholder = { Text("e.g., Alwar, Sikar, Gurugram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subHub,
                    onValueChange = { subHub = it },
                    label = { Text("Sub Hub") },
                    placeholder = { Text("e.g., Jaipur 2 (Sitapura)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Available LANs (e.g., LAN 4-11 or LAN 1, LAN 2)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "LAN 1" to "LAN 1",
                        "LAN 1-2" to "LAN 1, LAN 2",
                        "LAN 1-3" to "LAN 1, LAN 2, LAN 3",
                        "LAN 4-11" to "LAN 4-11",
                        "LAN 1-11" to "LAN 1-11"
                    ).forEach { (chipLabel, presetValue) ->
                        FilterChip(
                            selected = lansInput.equals(presetValue, ignoreCase = true),
                            onClick = { lansInput = presetValue },
                            label = { Text(chipLabel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = lansInput,
                    onValueChange = { lansInput = it },
                    label = { Text("LAN List / Range") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_centre_lans_input")
                )
                if (parsedPreview.isNotEmpty()) {
                    Text(
                        text = "Resolved (${parsedPreview.size}): ${parsedPreview.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            val cityReady = !useNewCity || districtName.isNotBlank()
            val stateReady = !useNewCity || !useNewState || newStateName.isNotBlank()
            Button(
                enabled = code.isNotBlank() && name.isNotBlank() && cityReady && stateReady,
                onClick = {
                    val finalLans = OrgCentre.parseLansInput(lansInput).ifEmpty { listOf("LAN 1") }
                    val resolvedStateName = when {
                        useNewCity && (useNewState || orgHierarchy.states.isEmpty()) ->
                            newStateName.trim().takeIf { it.isNotBlank() }
                        else ->
                            orgHierarchy.states.firstOrNull { it.id == selectedStateId }?.name
                    }
                    onSave(
                        code.trim(),
                        name.trim(),
                        if (useNewCity) null else selectedCityId,
                        districtName.trim().takeIf { it.isNotBlank() },
                        resolvedStateName,
                        subHub.trim().takeIf { it.isNotBlank() },
                        address.trim().takeIf { it.isNotBlank() },
                        finalLans
                    )
                },
                modifier = Modifier.testTag("confirm_save_admin_centre_btn")
            ) {
                Text("Save Centre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
