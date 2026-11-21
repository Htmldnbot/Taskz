package com.example.ui.incidents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.TaskzApplication
import com.example.core.model.SystemIncident
import com.example.core.model.SystemIncidentAudit
import com.example.core.model.TaskzRole
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingFilterToggleButton
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSearchInput
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.OrderedCascadingFilterBar
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentHistoryScreen(
    viewModel: SystemIncidentViewModel,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.filteredIncidents.collectAsStateWithLifecycle()
    val allIncidents by viewModel.allIncidents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val myReportsOnly by viewModel.filterMyReportsOnly.collectAsStateWithLifecycle()
    val filterShiftedOnly by viewModel.filterShiftedOnly.collectAsStateWithLifecycle()
    val filterLan by viewModel.filterLan.collectAsStateWithLifecycle()
    val filterCentre by viewModel.filterCentre.collectAsStateWithLifecycle()
    val filterCity by viewModel.filterCity.collectAsStateWithLifecycle()
    val filterState by viewModel.filterSelectedState.collectAsStateWithLifecycle()
    val filterZone by viewModel.filterZone.collectAsStateWithLifecycle()
    val filterExam by viewModel.filterExam.collectAsStateWithLifecycle()
    val filterShift by viewModel.filterShift.collectAsStateWithLifecycle()
    val filterDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()
    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentreNames.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableExams by viewModel.availableExams.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()

    val selectedIncident by viewModel.selectedIncident.collectAsStateWithLifecycle()
    val selectedAudits by viewModel.incidentAudits.collectAsStateWithLifecycle()
    var incidentToDelete by remember { mutableStateOf<SystemIncident?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val activeContext by TaskzApplication.instance.permissionManager.contextFlow.collectAsStateWithLifecycle()
    val isSuperAdmin = activeContext.activeRole == TaskzRole.SUPER_ADMIN

    val extraActiveCount = (if (myReportsOnly) 1 else 0) + (if (filterShiftedOnly) 1 else 0)
    val cascadingActiveCount = listOfNotNull(
        filterState?.takeIf { it.isNotBlank() },
        filterCity?.takeIf { it.isNotBlank() },
        filterCentre?.takeIf { it.isNotBlank() },
        filterExam?.takeIf { it.isNotBlank() },
        filterLan?.takeIf { it.isNotBlank() },
        filterShift?.takeIf { it.isNotBlank() },
        filterZone?.takeIf { it.isNotBlank() },
        filterDateRange?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
    ).size
    val activeFilterCount = extraActiveCount + cascadingActiveCount
    val hasActiveFilter = activeFilterCount > 0 || searchQuery.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NothingTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search, Filter Toggle & Refresh Row
            item(key = "history_search_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingSearchInput(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = "Search system, exam, reason...",
                        testTag = "history_search_input",
                        modifier = Modifier.weight(1f)
                    )

                    NothingFilterToggleButton(
                        activeCount = activeFilterCount,
                        isExpanded = showFilters,
                        onClick = { showFilters = !showFilters },
                        testTag = "filter_toggle_button"
                    )

                    Surface(
                        onClick = { viewModel.refreshIncidents() },
                        shape = RoundedCornerShape(12.dp),
                        color = NothingTheme.colors.surface,
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("history_refresh_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync from Cloud",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            item(key = "history_cascading_filters") {
                val selectedDateLabel = when (filterDateRange?.lowercase(Locale.ROOT)) {
                    "today" -> "Today"
                    "yesterday" -> "Yesterday"
                    "last_7_days", "last 7 days" -> "Last 7 Days"
                    else -> null
                }

                OrderedCascadingFilterBar(
                    selectedState = filterState,
                    stateOptions = availableStates,
                    onStateSelected = { viewModel.setFilterState(it) },
                    selectedCity = filterCity,
                    cityOptions = availableCities,
                    onCitySelected = { viewModel.setFilterCity(it) },
                    selectedCentre = filterCentre,
                    centreOptions = availableCentres,
                    onCentreSelected = { viewModel.setFilterCentre(it) },
                    selectedExam = filterExam,
                    examOptions = availableExams,
                    onExamSelected = { viewModel.setFilterExam(it) },
                    selectedLan = filterLan,
                    lanOptions = availableLans,
                    onLanSelected = { viewModel.setFilterLan(it) },
                    selectedShift = filterShift,
                    shiftOptions = availableShifts,
                    onShiftSelected = { viewModel.setFilterShift(it) },
                    selectedDate = selectedDateLabel,
                    dateOptions = listOf("Today", "Yesterday", "Last 7 Days"),
                    onDateSelected = { selected ->
                        viewModel.setFilterDateRange(
                            when (selected) {
                                "Today" -> "today"
                                "Yesterday" -> "yesterday"
                                "Last 7 Days" -> "last_7_days"
                                else -> "all"
                            }
                        )
                    },
                    onClearAll = { viewModel.clearAllFilters() },
                    tagPrefix = "filter_dropdown",
                    expanded = showFilters,
                    onToggleExpanded = { showFilters = !showFilters },
                    extraActiveCount = extraActiveCount + if (filterZone != null) 1 else 0,
                    extraContent = {
                        val primaryChipScroll = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(primaryChipScroll),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactHistoryFilterChip(
                                label = "My Reports",
                                selected = myReportsOnly,
                                icon = Icons.Filled.Person,
                                onClick = { viewModel.setFilterMyReportsOnly(!myReportsOnly) },
                                testTag = "filter_my_reports_chip"
                            )
                            CompactHistoryFilterChip(
                                label = "All (${allIncidents.size})",
                                selected = !filterShiftedOnly && !hasActiveFilter,
                                onClick = { viewModel.clearAllFilters() },
                                testTag = "filter_all_chip"
                            )
                            CompactHistoryFilterChip(
                                label = "Shifted Only",
                                selected = filterShiftedOnly,
                                onClick = { viewModel.setFilterShiftedOnly(!filterShiftedOnly) },
                                testTag = "filter_shifted_chip"
                            )
                            if (hasActiveFilter) {
                                CompactHistoryFilterChip(
                                    label = "Clear",
                                    selected = false,
                                    icon = Icons.Filled.Clear,
                                    tintColor = NothingRed,
                                    onClick = { viewModel.clearAllFilters() },
                                    testTag = "filter_clear_all_chip"
                                )
                            }
                        }
                    }
                )
            }

            if (incidents.isEmpty()) {
                item(key = "history_empty_state") {
                    NothingEmptyState(
                        title = if (searchQuery.isNotBlank()) "NO MATCHES" else "NO INCIDENTS",
                        subtitle = "Use Quick Entry to log system locks.",
                        icon = Icons.Filled.Info
                    )
                }
            } else {
                items(incidents, key = { it.id }) { incident ->
                    IncidentItemCard(
                        incident = incident,
                        onClick = { viewModel.selectIncidentForDetail(incident) },
                        onLongClick = if (viewModel.canEditIncident(incident)) {
                            { incidentToDelete = incident }
                        } else null
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
        )
    }

    if (incidentToDelete != null) {
        AlertDialog(
            onDismissRequest = { incidentToDelete = null },
            containerColor = NothingTheme.colors.surface,
            title = { Text("Delete Incident Record", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete incident for system '${incidentToDelete?.lockedSystemNumber}'? This removes it from the register and cloud database.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = incidentToDelete?.id
                        if (id != null) viewModel.deleteIncident(id)
                        incidentToDelete = null
                    }
                ) {
                    Text("Delete", color = NothingRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { incidentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedIncident != null) {
        IncidentDetailDialog(
            incident = selectedIncident!!,
            audits = selectedAudits,
            canEscalate = viewModel.canEscalateIncident(selectedIncident!!),
            isSuperAdmin = isSuperAdmin,
            onEscalate = { targetLevel, reason ->
                viewModel.escalateIncident(
                    incidentId = selectedIncident!!.id,
                    targetLevel = targetLevel,
                    reason = reason
                ) { res ->
                    uiScope.launch {
                        res.fold(
                            onSuccess = { updated ->
                                snackbarHostState.showSnackbar("Incident escalated to ${TaskzRole.fromCode(updated.escalationLevel).displayName}")
                            },
                            onFailure = { err ->
                                snackbarHostState.showSnackbar(err.message ?: "Escalation failed")
                            }
                        )
                    }
                }
            },
            onDismiss = { viewModel.dismissIncidentDetail() }
        )
    }
}

@Composable
private fun CompactHistoryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    icon: ImageVector? = null,
    tintColor: Color? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) NothingTheme.colors.accentPrimary else NothingTheme.colors.surface,
        border = BorderStroke(
            1.dp,
            when {
                selected -> NothingTheme.colors.accentPrimary
                tintColor != null -> tintColor.copy(alpha = 0.45f)
                else -> NothingTheme.colors.border
            }
        ),
        modifier = Modifier
            .heightIn(min = 34.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor ?: if (selected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = tintColor ?: if (selected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IncidentItemCard(
    incident: SystemIncident,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val isEscalated = incident.status.equals("ESCALATED", ignoreCase = true)
    val isResolved = incident.status.equals("RESOLVED", ignoreCase = true)
    val isSynced = incident.syncStatus.equals("SYNCED", ignoreCase = true)
    val statusDotColor = when {
        isEscalated -> NothingRed
        isResolved -> NothingGreen
        else -> NothingAmber
    }

    val systemTransition = buildString {
        append(incident.lockedSystemNumber)
        if (incident.candidateShifted && !incident.newlyAllocatedSystemNumber.isNullOrBlank()) {
            append(" → ")
            append(incident.newlyAllocatedSystemNumber)
        }
    }

    // Ultra-compact 2-line telemetry row (~8-10 visible on screen)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("incident_card_${incident.lockedSystemNumber}"),
        shape = RoundedCornerShape(10.dp),
        color = NothingTheme.colors.surface,
        border = BorderStroke(
            1.dp,
            if (isEscalated) NothingRed.copy(alpha = 0.45f) else NothingTheme.colors.border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Line 1: [StatusDot] System->System · Reason [Time + Sync]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NothingStatusDot(color = statusDotColor, size = 7.dp)
                    Text(
                        text = systemTransition,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "· ${incident.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = incident.incidentTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    NothingStatusDot(
                        color = if (isSynced) NothingGreen else NothingAmber,
                        size = 5.dp
                    )
                }
            }

            // Line 2: Centre · LAN · Shift | Status · Level ›
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${incident.centreCode} · ${incident.lanName} · ${incident.shiftName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = NothingTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NothingTagChip(
                        text = incident.status.uppercase(),
                        containerColor = if (isEscalated) NothingRed.copy(alpha = 0.14f) else NothingTheme.colors.surfaceElevated,
                        contentColor = when {
                            isEscalated -> NothingRed
                            isResolved -> NothingGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        borderColor = if (isEscalated) NothingRed.copy(alpha = 0.4f) else NothingTheme.colors.border
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IncidentDetailDialog(
    incident: SystemIncident,
    audits: List<SystemIncidentAudit>,
    canEscalate: Boolean = false,
    isSuperAdmin: Boolean = false,
    onEscalate: (String, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    var showEscalatePicker by remember { mutableStateOf(false) }
    val currentLevelRole = TaskzRole.fromCode(incident.escalationLevel)
    val higherChain = remember(incident.escalationLevel, isSuperAdmin) {
        val higher = TaskzRole.escalationChain.filter { it.hierarchyRank > currentLevelRole.hierarchyRank }
        when {
            higher.isEmpty() -> listOf(TaskzRole.SUPER_ADMIN)
            isSuperAdmin -> higher
            else -> listOf(higher.first())
        }
    }
    var selectedTargetRole by remember(incident.id, incident.escalationLevel, isSuperAdmin) {
        mutableStateOf(higherChain.first())
    }
    var escalationReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingTheme.colors.surface,
        title = {
            NothingDotMatrixText(
                text = "INCIDENT // ${incident.lockedSystemNumber}",
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DetailRow("Status", incident.status.uppercase())
                DetailRow("Escalation", currentLevelRole.displayName)
                DetailRow("Scope", incident.scopeLevel)
                NothingDivider()
                DetailRow("Centre", "${incident.centreCode} · ${incident.centreName}")
                DetailRow("Location", "${incident.districtName}, ${incident.stateName}")
                DetailRow("Exam", incident.examName)
                DetailRow("Date / Shift", "${incident.examDate} · ${incident.shiftName}")
                DetailRow("LAN", incident.lanName)
                DetailRow("Locked Sys", incident.lockedSystemNumber)
                DetailRow("Shifted", if (incident.candidateShifted) "Yes" else "No")
                if (incident.candidateShifted) {
                    DetailRow("New Sys", incident.newlyAllocatedSystemNumber ?: "NA")
                }
                DetailRow("Time", incident.incidentTime)
                DetailRow("Reason", incident.reason)
                DetailRow("MAC", incident.macAddress)
                DetailRow("IP", incident.ipAddress)
                DetailRow("IT Manager", incident.itManagerName)
                DetailRow("Sync", incident.syncStatus)

                if (audits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NothingDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AUDIT HISTORY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NothingTheme.colors.textSecondary
                    )
                    audits.forEach { audit ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(audit.changedAt))
                        val actor = (audit.changedByName ?: "").ifBlank { "User" }
                        Text(
                            text = "• [${audit.action}] by $actor · $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (canEscalate) {
                Button(
                    onClick = { showEscalatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed, contentColor = Color.White),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("btn_escalate_incident")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Escalate")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showEscalatePicker) {
        AlertDialog(
            onDismissRequest = { showEscalatePicker = false },
            containerColor = NothingTheme.colors.surface,
            title = { NothingDotMatrixText(text = "ESCALATE INCIDENT", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select target authority in escalation chain:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                    higherChain.forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetRole = role }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTargetRole == role,
                                onClick = { selectedTargetRole = role }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = role.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTargetRole == role) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    OutlinedTextField(
                        value = escalationReason,
                        onValueChange = { escalationReason = it },
                        label = { Text("Escalation Notes (Optional)") },
                        placeholder = { Text("Describe urgency or required intervention...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("incident_escalation_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEscalate(selectedTargetRole.code, escalationReason.takeIf { it.isNotBlank() })
                        showEscalatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed, contentColor = Color.White),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("btn_confirm_escalate_incident")
                ) {
                    Text("Confirm Escalation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEscalatePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NothingTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = TechMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FilterDropdownButton(
    label: String,
    selectedValue: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = if (selectedValue != null) NothingTheme.colors.accentPrimary else NothingTheme.colors.surface,
            border = BorderStroke(
                1.dp,
                if (selectedValue != null) NothingTheme.colors.accentPrimary else NothingTheme.colors.border
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selectedValue != null) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (selectedValue != null) "$label: $selectedValue" else "$label: All",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selectedValue != null) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selectedValue != null) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (selectedValue != null) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "All $label",
                        fontWeight = if (selectedValue == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt,
                            fontWeight = if (selectedValue == opt) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
