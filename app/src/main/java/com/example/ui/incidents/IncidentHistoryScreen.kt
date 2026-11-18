package com.example.ui.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.TaskzApplication
import com.example.core.model.SystemIncident
import com.example.core.model.SystemIncidentAudit
import com.example.core.model.TaskzRole
import com.example.ui.common.OrderedCascadingFilterBar
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
    val filterShift by viewModel.filterShift.collectAsStateWithLifecycle()
    val filterDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()
    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentreNames.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()

    val selectedIncident by viewModel.selectedIncident.collectAsStateWithLifecycle()
    val selectedAudits by viewModel.incidentAudits.collectAsStateWithLifecycle()
    var incidentToDelete by remember { mutableStateOf<SystemIncident?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val activeContext by TaskzApplication.instance.permissionManager.contextFlow.collectAsStateWithLifecycle()
    val isSuperAdmin = activeContext.activeRole == TaskzRole.SUPER_ADMIN

    val hasActiveFilter = myReportsOnly || filterShiftedOnly || filterLan != null ||
        filterCentre != null || filterCity != null || filterState != null || filterZone != null ||
        filterShift != null || (filterDateRange != null && filterDateRange != "all") || searchQuery.isNotBlank()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // Search & Refresh Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by system, centre, reason...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_search_input")
            )

            IconButton(
                onClick = { viewModel.refreshIncidents() },
                modifier = Modifier.size(44.dp).testTag("history_refresh_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Sync from Cloud",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

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
            initiallyExpanded = true
        )

        // Quick Toggles: My Reports, Shifted Only, Clear
        val primaryChipScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(primaryChipScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = myReportsOnly,
                onClick = { viewModel.setFilterMyReportsOnly(!myReportsOnly) },
                label = { Text(if (myReportsOnly) "My Reports Only ✓" else "My Reports Only") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("filter_my_reports_chip")
            )

            FilterChip(
                selected = !filterShiftedOnly && !hasActiveFilter,
                onClick = { viewModel.clearAllFilters() },
                label = { Text("All (${allIncidents.size})") },
                modifier = Modifier.testTag("filter_all_chip")
            )

            FilterChip(
                selected = filterShiftedOnly,
                onClick = { viewModel.setFilterShiftedOnly(!filterShiftedOnly) },
                label = { Text("Shifted Only") },
                modifier = Modifier.testTag("filter_shifted_chip")
            )

            if (hasActiveFilter) {
                AssistChip(
                    onClick = { viewModel.clearAllFilters() },
                    label = { Text("Clear All Filters", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }

        // Incident Cards List
        if (incidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "No incidents match your search" else "No incidents recorded yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Use Quick Entry to record technical system incidents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Delete Incident Dialog
    if (incidentToDelete != null) {
        AlertDialog(
            onDismissRequest = { incidentToDelete = null },
            title = {
                Text("Delete Incident Record")
            },
            text = {
                Text("Are you sure you want to delete incident for system '${incidentToDelete?.lockedSystemNumber}'? This will remove it from the register and cloud database.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = incidentToDelete?.id
                        if (id != null) {
                            viewModel.deleteIncident(id)
                        }
                        incidentToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { incidentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Incident Details Dialog with Escalation
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IncidentItemCard(
    incident: SystemIncident,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("incident_card_${incident.lockedSystemNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: System numbers & Sync Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Locked System badge
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = incident.lockedSystemNumber,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (incident.candidateShifted && !incident.newlyAllocatedSystemNumber.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Shifted to",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = incident.newlyAllocatedSystemNumber,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Sync Status Badge
                val isSynced = incident.syncStatus.equals("SYNCED", ignoreCase = true)
                Surface(
                    color = if (isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSynced) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = if (isSynced) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isSynced) "Synced" else "Pending",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSynced) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            // Reason and LAN row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = incident.reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${incident.lanName} \u00B7 ${incident.shiftName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Centre and Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${incident.centreCode} \u00B7 ${incident.centreName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = incident.incidentTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Status, Escalation Level & Scope Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isEscalated = incident.status.equals("ESCALATED", ignoreCase = true)
                val isResolved = incident.status.equals("RESOLVED", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isEscalated -> MaterialTheme.colorScheme.errorContainer
                        isResolved -> Color(0xFFE8F5E9)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = incident.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isEscalated -> MaterialTheme.colorScheme.onErrorContainer
                            isResolved -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "Level: ${TaskzRole.fromCode(incident.escalationLevel).displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = incident.scopeLevel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
        title = {
            Text(
                text = "Incident: ${incident.lockedSystemNumber}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Status", incident.status.uppercase())
                DetailRow("Escalation Level", currentLevelRole.displayName)
                DetailRow("Scope", incident.scopeLevel)
                DetailRow("Centre", "${incident.centreCode} · ${incident.centreName}")
                DetailRow("Location", "${incident.districtName}, ${incident.stateName}")
                DetailRow("Examination", incident.examName)
                DetailRow("Date & Shift", "${incident.examDate} · ${incident.shiftName}")
                DetailRow("LAN", incident.lanName)
                DetailRow("Locked System", incident.lockedSystemNumber)
                DetailRow("Candidate Shifted", if (incident.candidateShifted) "Yes" else "No")
                if (incident.candidateShifted) {
                    DetailRow("New System", incident.newlyAllocatedSystemNumber ?: "NA")
                }
                DetailRow("Failure Time", incident.incidentTime)
                DetailRow("Reason", incident.reason)
                DetailRow("MAC Address", incident.macAddress)
                DetailRow("IP Address", incident.ipAddress)
                DetailRow("IT Manager", incident.itManagerName)
                DetailRow("Sync Status", incident.syncStatus)

                if (audits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Audit History",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    audits.forEach { audit ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(audit.changedAt))
                        val actor = (audit.changedByName ?: "").ifBlank { "User" }
                        Text(
                            text = "\u2022 [${audit.action}] by $actor on $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (canEscalate) {
                Button(
                    onClick = { showEscalatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_escalate_incident")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
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
            title = { Text("Escalate Incident", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select target authority in the operational escalation chain:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    higherChain.forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetRole = role }
                                .padding(vertical = 4.dp),
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selectedValue != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (selectedValue != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
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
                            tint = if (selectedValue != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (selectedValue != null) "$label: $selectedValue" else "$label: All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selectedValue != null) FontWeight.Bold else FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
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
                        fontWeight = if (selectedValue == null) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedValue == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                            fontWeight = if (selectedValue == opt) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedValue == opt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
