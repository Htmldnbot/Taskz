package com.example.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.core.model.AdminAuditEntry
import com.example.core.model.OrgCentre
import com.example.core.model.OrgHierarchyData
import com.example.core.model.PasswordResetRequest
import com.example.core.model.TaskzRole
import com.example.core.model.UserProfile
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

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
            .background(MaterialTheme.colorScheme.background)
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NothingTheme.colors.surfaceElevated)
                        .border(1.dp, NothingTheme.colors.border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    NothingDotMatrixText(
                        text = "ADMIN CONSOLE",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "USERS · ROLES · CENTRES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            letterSpacing = 1.sp
                        ),
                        color = NothingTheme.colors.textSecondary
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
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NothingTheme.colors.surfaceElevated)
                    .border(1.dp, NothingTheme.colors.border, CircleShape)
                    .testTag("admin_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status / Feedback message banner
        AnimatedVisibility(visible = actionState is AdminActionState.Success) {
            NothingHairlineCard(
                shape = RoundedCornerShape(12.dp),
                containerColor = NothingGreen.copy(alpha = 0.08f),
                borderColor = NothingGreen.copy(alpha = 0.4f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NothingGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = (actionState as? AdminActionState.Success)?.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = NothingGreen
                    )
                }
            }
        }

        AnimatedVisibility(visible = actionState is AdminActionState.Error) {
            NothingHairlineCard(
                shape = RoundedCornerShape(12.dp),
                containerColor = NothingRed.copy(alpha = 0.08f),
                borderColor = NothingRed.copy(alpha = 0.4f),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = (actionState as? AdminActionState.Error)?.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = NothingRed
                )
            }
        }

        // Pill Tabs
        val pendingCount = requests.count { it.isPending }
        val adminTabs = listOf(
            Triple(0, "Users (${users.size})", "admin_tab_users"),
            Triple(1, if (pendingCount > 0) "Resets ($pendingCount)" else "Resets", "admin_tab_requests"),
            Triple(2, "Work Roles", "admin_tab_work_roles"),
            Triple(3, "Audit Logs", "admin_tab_audit_logs"),
            Triple(4, "Centres (${orgHierarchy.centres.size})", "admin_tab_centres")
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(adminTabs, key = { it.first }) { (tabIdx, title, tag) ->
                val isSelected = selectedTab == tabIdx
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) NothingTheme.colors.accentPrimary else NothingTheme.colors.border
                    ),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            selectedTab = tabIdx
                            when (tabIdx) {
                                3 -> viewModel.loadAuditLogs()
                                4 -> if (orgHierarchy.centres.isEmpty()) viewModel.loadOrgHierarchy()
                            }
                        }
                        .testTag(tag)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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
                    label = {
                        Text(
                            "All (${users.size})",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                        )
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NothingTheme.colors.accentPrimary,
                        selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                        containerColor = NothingTheme.colors.surfaceElevated,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = !filterNeedsScopeReview,
                        borderColor = NothingTheme.colors.border,
                        selectedBorderColor = NothingTheme.colors.accentPrimary
                    ),
                    modifier = Modifier.testTag("admin_filter_all_users_chip")
                )
                FilterChip(
                    selected = filterNeedsScopeReview,
                    onClick = { filterNeedsScopeReview = !filterNeedsScopeReview },
                    label = {
                        Text(
                            "Scope Review ($scopeReviewCount)",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                        )
                    },
                    leadingIcon = if (scopeReviewCount > 0) {
                        {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (filterNeedsScopeReview) NothingTheme.colors.onAccentPrimary else NothingAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NothingTheme.colors.accentPrimary,
                        selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                        containerColor = NothingTheme.colors.surfaceElevated,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filterNeedsScopeReview,
                        borderColor = NothingTheme.colors.border,
                        selectedBorderColor = NothingTheme.colors.accentPrimary
                    ),
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
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        icon = Icons.Default.Person,
                        title = if (searchQuery.isBlank()) "NO EMPLOYEES" else "NO MATCHES",
                        subtitle = if (searchQuery.isBlank()) "No personnel registered in directory yet." else "No match for '$searchQuery'."
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
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
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        icon = Icons.Default.LockReset,
                        title = "NO RESET REQUESTS",
                        subtitle = "All password reset tickets are clear."
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
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
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NothingEmptyState(
                        icon = Icons.Default.History,
                        title = "NO AUDIT LOGS",
                        subtitle = "Governance audit logs will appear here."
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
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
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_admin_add_city")
                ) {
                    Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ City", color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = { showAddCentreDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingTheme.colors.accentPrimary,
                        contentColor = NothingTheme.colors.onAccentPrimary
                    ),
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
                            label = {
                                Text(
                                    "All (${orgHierarchy.cities.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NothingTheme.colors.accentPrimary,
                                selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                containerColor = NothingTheme.colors.surfaceElevated,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCityFilterId == null,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
                            modifier = Modifier.testTag("admin_city_filter_all")
                        )
                    }
                    items(orgHierarchy.cities, key = { it.id }) { city ->
                        val count = centreCountsByCity[city.id] ?: 0
                        val isCitySelected = selectedCityFilterId == city.id
                        FilterChip(
                            selected = isCitySelected,
                            onClick = {
                                selectedCityFilterId = if (isCitySelected) null else city.id
                            },
                            label = {
                                Text(
                                    "${city.name} ($count)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NothingTheme.colors.accentPrimary,
                                selectedLabelColor = NothingTheme.colors.onAccentPrimary,
                                containerColor = NothingTheme.colors.surfaceElevated,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isCitySelected,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            ),
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
                    NothingEmptyState(
                        icon = Icons.Default.Business,
                        title = "NO CENTRES",
                        subtitle = if (centreSearchQuery.isBlank() && selectedCityFilterId == null) {
                            "No centres loaded yet."
                        } else {
                            "No centres in this selection. Tap '+ Centre' to add one."
                        }
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
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
    NothingHairlineCard(
        shape = RoundedCornerShape(14.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
                            .clip(CircleShape)
                            .border(1.dp, NothingTheme.colors.border, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NothingTheme.colors.surfaceElevated)
                            .border(1.dp, NothingTheme.colors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.fullName.take(2).uppercase().ifBlank { "EM" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NothingTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        NothingTagChip(
                            text = user.resolvedActiveRole.displayName.uppercase(),
                            containerColor = if (user.isAdmin) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                            contentColor = if (user.isAdmin) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textPrimary,
                            borderColor = if (user.isAdmin) NothingTheme.colors.accentPrimary else NothingTheme.colors.border
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = listOfNotNull(
                            "ID: ${user.employeeId}",
                            user.mobile.takeIf { it.isNotBlank() },
                            user.centreCode?.let { "Centre $it" } ?: user.cityName ?: user.stateName
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )

                    if (user.assignedRoleCodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Assigned: ${user.assignedRoleCodes.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textPrimary
                        )
                    }

                    if (user.isOnLeave) {
                        Spacer(modifier = Modifier.height(5.dp))
                        NothingTagChip(
                            text = "ON LEAVE",
                            containerColor = NothingAmber.copy(alpha = 0.14f),
                            contentColor = NothingAmber,
                            borderColor = NothingAmber.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("badge_admin_on_leave_${user.employeeId}")
                        )
                    }

                    if (user.needsScopeReview) {
                        Spacer(modifier = Modifier.height(5.dp))
                        NothingTagChip(
                            text = "NEEDS SCOPE REVIEW",
                            containerColor = NothingRed.copy(alpha = 0.14f),
                            contentColor = NothingRed,
                            borderColor = NothingRed.copy(alpha = 0.45f),
                            modifier = Modifier.testTag("badge_scope_review_${user.employeeId}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onManageAccess,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingTheme.colors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingTheme.colors.textPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
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
                        containerColor = if (user.isActive) NothingRed.copy(alpha = 0.14f) else NothingGreen.copy(alpha = 0.14f),
                        contentColor = if (user.isActive) NothingRed else NothingGreen
                    ),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_status_${user.employeeId}")
                ) {
                    Text(
                        text = if (user.isActive) "Deactivate" else "Activate",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAuditLogCard(entry: AdminAuditEntry) {
    NothingHairlineCard(
        shape = RoundedCornerShape(14.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NothingTagChip(text = entry.action.uppercase())
                Text(
                    text = entry.createdAt.take(19).replace("T", " "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
            Text(
                text = "By ${entry.adminName ?: "Admin"} -> ${entry.targetName ?: entry.targetEmployeeId ?: entry.targetUserId ?: "System"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NothingTheme.colors.textPrimary
            )
            if (entry.metadataJson.isNotBlank() && entry.metadataJson != "{}") {
                Text(
                    text = entry.metadataJson,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
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
        containerColor = NothingTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NothingDotMatrixText(
                    text = "ROLES & SCOPE",
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = NothingTheme.colors.textPrimary
                )
                Text(
                    text = "${user.fullName} (${user.employeeId})",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
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
                Text(
                    text = "ASSIGNED ROLES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                TaskzRole.entries.forEach { role ->
                    val checked = selectedRoles.value.contains(role.code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (!checked) {
                                    selectedRoles.value + role.code
                                } else {
                                    selectedRoles.value - role.code
                                }
                                selectedRoles.value = next
                                if (!next.contains(activeRoleCode) && next.isNotEmpty()) {
                                    activeRoleCode = next.first()
                                }
                            },
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
                            },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = NothingTheme.colors.accentPrimary,
                                checkmarkColor = NothingTheme.colors.onAccentPrimary,
                                uncheckedColor = NothingTheme.colors.textSecondary
                            )
                        )
                        Text(
                            text = role.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTheme.colors.textPrimary
                        )
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
                        shape = RoundedCornerShape(12.dp),
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
                    NothingHairlineCard(
                        shape = RoundedCornerShape(10.dp),
                        containerColor = NothingTheme.colors.surfaceElevated,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Text(
                            text = "Global Access (All Zones, States, Cities & Centres)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = NothingTheme.colors.textPrimary
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
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                    Text(
                        text = "Account Active",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NothingTheme.colors.textPrimary
                    )
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_save_user_access")
            ) {
                Text("Save Access", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = NothingTheme.colors.textSecondary)
            }
        }
    )
}

@Composable
private fun ResetRequestCard(
    request: PasswordResetRequest,
    onResetClick: () -> Unit
) {
    NothingHairlineCard(
        shape = RoundedCornerShape(14.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Employee ID: ${request.employeeId}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textPrimary
                )
                if (!request.employeeName.isNullOrBlank()) {
                    Text(
                        text = request.employeeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textPrimary
                    )
                }
            }

            NothingTagChip(
                text = request.status.uppercase(),
                containerColor = when (request.status) {
                    "pending" -> NothingAmber.copy(alpha = 0.14f)
                    "resolved" -> NothingGreen.copy(alpha = 0.14f)
                    else -> NothingTheme.colors.surfaceElevated
                },
                contentColor = when (request.status) {
                    "pending" -> NothingAmber
                    "resolved" -> NothingGreen
                    else -> NothingTheme.colors.textSecondary
                },
                borderColor = when (request.status) {
                    "pending" -> NothingAmber.copy(alpha = 0.45f)
                    "resolved" -> NothingGreen.copy(alpha = 0.45f)
                    else -> NothingTheme.colors.border
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mobile: ${request.mobile}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
            color = NothingTheme.colors.textSecondary
        )

        if (!request.message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"${request.message}\"",
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = NothingTheme.colors.textPrimary
            )
        }

        if (request.isPending) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onResetClick,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("admin_reset_button_${request.employeeId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Issue Temporary Password", fontWeight = FontWeight.Bold)
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
        containerColor = NothingTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            NothingDotMatrixText(
                text = "RESET PASSWORD",
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                color = NothingTheme.colors.textPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Assign a new temporary password for Employee ID: ${request.employeeId}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("admin_confirm_temp_password_button")
            ) {
                Text("Execute Server Reset", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = NothingTheme.colors.textSecondary)
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
    NothingHairlineCard(
        shape = RoundedCornerShape(14.dp),
        containerColor = NothingTheme.colors.surface,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.testTag("admin_centre_card_${centre.code}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${centre.code} · ${centre.name}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = NothingTheme.colors.textPrimary
                    )
                    val meta = listOfNotNull(
                        centre.districtName?.takeIf { it.isNotBlank() },
                        centre.subHub?.takeIf { it.isNotBlank() }?.let { "Hub: $it" }
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                            color = NothingTheme.colors.textSecondary
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
                        tint = NothingTheme.colors.textPrimary
                    )
                }
            }

            if (!centre.address.isNullOrBlank()) {
                Text(
                    text = centre.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTheme.colors.textSecondary,
                    maxLines = 2
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lans.forEach { lan ->
                    NothingTagChip(text = lan)
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
        containerColor = NothingTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            NothingDotMatrixText(
                text = "ADD CITY",
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                color = NothingTheme.colors.textPrimary
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
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("City Name *") },
                    placeholder = { Text("e.g., Alwar, Sikar, Gurugram") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                                text = {
                                    Text(
                                        "+ New State",
                                        fontWeight = FontWeight.Bold,
                                        color = NothingTheme.colors.textPrimary
                                    )
                                },
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
                        shape = RoundedCornerShape(12.dp),
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
                                shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_save_admin_city_btn")
            ) {
                Text("Save City", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = NothingTheme.colors.textSecondary)
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
        containerColor = NothingTheme.colors.surface,
        shape = RoundedCornerShape(16.dp),
        title = {
            NothingDotMatrixText(
                text = if (existing == null) "ADD EXAM CENTRE" else "EDIT EXAM CENTRE",
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                color = NothingTheme.colors.textPrimary
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_centre_code_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("TC Name *") },
                    placeholder = { Text("e.g., iON Digital Zone iDZ Sitapura 2") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(12.dp),
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
                                        color = NothingTheme.colors.textPrimary
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
                                shape = RoundedCornerShape(12.dp),
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
                                    text = {
                                        Text(
                                            "+ New State",
                                            fontWeight = FontWeight.Bold,
                                            color = NothingTheme.colors.textPrimary
                                        )
                                    },
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
                            shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subHub,
                    onValueChange = { subHub = it },
                    label = { Text("Sub Hub") },
                    placeholder = { Text("e.g., Jaipur 2 (Sitapura)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "AVAILABLE LANS (e.g., LAN 4-11 or LAN 1, LAN 2)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "LAN 1" to "LAN 1",
                        "LAN 1-2" to "LAN 1, LAN 2",
                        "LAN 1-3" to "LAN 1, LAN 2, LAN 3",
                        "LAN 4-11" to "LAN 4-11",
                        "LAN 1-11" to "LAN 1-11"
                    ).forEach { (chipLabel, presetValue) ->
                        val selected = lansInput.equals(presetValue, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { lansInput = presetValue },
                            label = {
                                Text(
                                    text = chipLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily)
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NothingTheme.colors.surfaceElevated,
                                labelColor = NothingTheme.colors.textSecondary,
                                selectedContainerColor = NothingTheme.colors.accentPrimary,
                                selectedLabelColor = NothingTheme.colors.onAccentPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = NothingTheme.colors.border,
                                selectedBorderColor = NothingTheme.colors.accentPrimary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = lansInput,
                    onValueChange = { lansInput = it },
                    label = { Text("LAN List / Range") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_centre_lans_input")
                )
                if (parsedPreview.isNotEmpty()) {
                    Text(
                        text = "Resolved (${parsedPreview.size}): ${parsedPreview.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textPrimary
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
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_save_admin_centre_btn")
            ) {
                Text("Save Centre", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = NothingTheme.colors.textSecondary)
            }
        }
    )
}
