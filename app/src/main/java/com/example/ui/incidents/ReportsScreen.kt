package com.example.ui.incidents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.OrderedCascadingFilterBar
import java.util.Locale

enum class ReportBreakdownTab {
    OVERVIEW,
    LAN_WISE,
    CENTER_WISE,
    CITY_WISE,
    STATE_WISE,
    ZONE_WISE
}

@Composable
fun ReportsScreen(
    viewModel: SystemIncidentViewModel,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.filteredIncidents.collectAsStateWithLifecycle()
    val allIncidents by viewModel.allIncidents.collectAsStateWithLifecycle()

    val myReportsOnly by viewModel.filterMyReportsOnly.collectAsStateWithLifecycle()
    val selectedLan by viewModel.filterLan.collectAsStateWithLifecycle()
    val selectedCentre by viewModel.filterCentre.collectAsStateWithLifecycle()
    val selectedCity by viewModel.filterCity.collectAsStateWithLifecycle()
    val selectedState by viewModel.filterSelectedState.collectAsStateWithLifecycle()
    val selectedZone by viewModel.filterZone.collectAsStateWithLifecycle()
    val selectedShift by viewModel.filterShift.collectAsStateWithLifecycle()
    val selectedDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()

    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentreNames.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()

    val repeatLockedSystems by viewModel.repeatLockedSystems.collectAsStateWithLifecycle()
    val topReasonsByCentre by viewModel.topReasonsByCentre.collectAsStateWithLifecycle()
    val topReasonsByDistrict by viewModel.topReasonsByDistrict.collectAsStateWithLifecycle()
    val topReasonsByState by viewModel.topReasonsByState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(ReportBreakdownTab.OVERVIEW) }
    var topReasonsScopeTab by remember { mutableIntStateOf(0) } // 0=Centre, 1=City/District, 2=State

    val hasActiveFilter = myReportsOnly || selectedLan != null || selectedCentre != null ||
        selectedCity != null || selectedState != null || selectedZone != null ||
        selectedShift != null || (selectedDateRange != null && selectedDateRange != "all")

    val totalCount = incidents.size
    val shiftedCount = incidents.count { it.candidateShifted }
    val pendingSyncCount = incidents.count { !it.syncStatus.equals("SYNCED", ignoreCase = true) }

    // Dynamic Groupings based on current filtered dataset
    val reasonCounts = incidents.groupBy { it.reason }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val lanCounts = incidents.groupBy { it.lanName }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val centreCounts = incidents.groupBy { "${it.centreCode} · ${it.centreName}" }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val cityCounts = incidents.groupBy { it.districtName.ifBlank { "Jaipur" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val stateCounts = incidents.groupBy { it.stateName.ifBlank { "Rajasthan" } }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val zoneCounts = incidents.groupBy { SystemIncidentViewModel.getZoneForState(it.stateName) }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header: Refresh & Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Incident Analytics & Reports",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (hasActiveFilter) "Showing ${incidents.size} filtered of ${allIncidents.size} total" else "${allIncidents.size} total examination records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.refreshIncidents() },
                modifier = Modifier.testTag("refresh_reports_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh from Cloud",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Export Action Header
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Export Incident Register",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (hasActiveFilter) {
                        "Exporting ${incidents.size} filtered records with technical parameters, LAN logs, and replacement records to Excel/CSV."
                    } else {
                        "Generate a formatted UTF-8 Excel/CSV report with all 15 technical fields, audit stamps, and candidate replacement logs."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Button(
                    onClick = { viewModel.exportReport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_csv_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasActiveFilter) "Export Filtered Report (${incidents.size})" else "Export All to Excel / CSV",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Breakdown View Selector Tabs (LAN-wise, Center-wise, City-wise, State-wise, Zone-wise)
        Text(
            text = "Report Breakdown View",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val tabScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeTab == ReportBreakdownTab.OVERVIEW,
                onClick = { activeTab = ReportBreakdownTab.OVERVIEW },
                label = { Text("Overview") }
            )
            FilterChip(
                selected = activeTab == ReportBreakdownTab.LAN_WISE,
                onClick = { activeTab = ReportBreakdownTab.LAN_WISE },
                label = { Text("LAN-wise") },
                leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = activeTab == ReportBreakdownTab.CENTER_WISE,
                onClick = { activeTab = ReportBreakdownTab.CENTER_WISE },
                label = { Text("Center-wise") },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = activeTab == ReportBreakdownTab.CITY_WISE,
                onClick = { activeTab = ReportBreakdownTab.CITY_WISE },
                label = { Text("City-wise") },
                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = activeTab == ReportBreakdownTab.STATE_WISE,
                onClick = { activeTab = ReportBreakdownTab.STATE_WISE },
                label = { Text("State-wise") },
                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = activeTab == ReportBreakdownTab.ZONE_WISE,
                onClick = { activeTab = ReportBreakdownTab.ZONE_WISE },
                label = { Text("Zone-wise") },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        val selectedDateLabel = when (selectedDateRange?.lowercase(Locale.ROOT)) {
            "today" -> "Today"
            "yesterday" -> "Yesterday"
            "last_7_days", "last 7 days" -> "Last 7 Days"
            else -> null
        }

        OrderedCascadingFilterBar(
            selectedState = selectedState,
            stateOptions = availableStates,
            onStateSelected = { viewModel.setFilterState(it) },
            selectedCity = selectedCity,
            cityOptions = availableCities,
            onCitySelected = { viewModel.setFilterCity(it) },
            selectedCentre = selectedCentre,
            centreOptions = availableCentres,
            onCentreSelected = { viewModel.setFilterCentre(it) },
            selectedLan = selectedLan,
            lanOptions = availableLans,
            onLanSelected = { viewModel.setFilterLan(it) },
            selectedShift = selectedShift,
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
            tagPrefix = "reports_dropdown",
            initiallyExpanded = true
        )

        // Quick Chips Row: My Reports & Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = myReportsOnly,
                onClick = { viewModel.setFilterMyReportsOnly(!myReportsOnly) },
                label = { Text(if (myReportsOnly) "My Reports Only ✓" else "My Reports Only") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("reports_my_reports_chip")
            )

            if (hasActiveFilter) {
                OutlinedButton(
                    onClick = { viewModel.clearAllFilters() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All Filters", fontSize = 12.sp)
                }
            }
        }

        // Summary Metric Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Total Incidents",
                value = "$totalCount",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
            MetricCard(
                title = "Repeat Systems",
                value = "${repeatLockedSystems.size}",
                modifier = Modifier.weight(1f),
                containerColor = if (repeatLockedSystems.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
            )
            MetricCard(
                title = "Shifted",
                value = "$shiftedCount",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }

        // SECTION 1: Repeat Locked Systems (2+ Locks per Centre & LAN)
        RepeatLockedSystemsSection(
            repeatSystems = repeatLockedSystems,
            modifier = Modifier.testTag("repeat_locked_systems_section")
        )

        // SECTION 2: Top Incident Reasons by Centre / District / State
        ScopedTopReasonsSection(
            selectedScopeTab = topReasonsScopeTab,
            onScopeTabSelected = { topReasonsScopeTab = it },
            centreSummaries = topReasonsByCentre,
            districtSummaries = topReasonsByDistrict,
            stateSummaries = topReasonsByState,
            modifier = Modifier.testTag("top_incident_reasons_section")
        )

        // SECTION 3: Field Recommendations to Prevent Repeat Locks
        RepeatLockPreventionCard(
            modifier = Modifier.testTag("repeat_lock_prevention_card")
        )

        // Dynamic Content according to Selected Tab
        when (activeTab) {
            ReportBreakdownTab.OVERVIEW -> {
                // Breakdown by Failure Reason
                BreakdownCard(
                    title = "Incidents by Failure Reason",
                    items = reasonCounts,
                    total = totalCount
                )

                // Breakdown by LAN
                BreakdownCard(
                    title = "Incidents by LAN",
                    items = lanCounts,
                    total = totalCount,
                    onItemClick = { lan -> viewModel.setFilterLan(if (selectedLan == lan) null else lan) },
                    selectedItem = selectedLan
                )

                // Breakdown by Centre
                BreakdownCard(
                    title = "Incidents by Examination Centre",
                    items = centreCounts,
                    total = totalCount,
                    onItemClick = { centre -> viewModel.setFilterCentre(if (selectedCentre == centre) null else centre) },
                    selectedItem = selectedCentre
                )
            }

            ReportBreakdownTab.LAN_WISE -> {
                BreakdownCard(
                    title = "LAN-wise Distribution",
                    items = lanCounts,
                    total = totalCount,
                    onItemClick = { lan -> viewModel.setFilterLan(if (selectedLan == lan) null else lan) },
                    selectedItem = selectedLan,
                    interactiveHint = "Tap any LAN to filter report"
                )

                if (selectedLan != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered by $selectedLan ($totalCount incidents)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { viewModel.setFilterLan(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear LAN filter")
                            }
                        }
                    }
                }

                // Cross breakdown of reasons for LANs
                BreakdownCard(
                    title = "Failure Reasons in Current View",
                    items = reasonCounts,
                    total = totalCount
                )
            }

            ReportBreakdownTab.CENTER_WISE -> {
                BreakdownCard(
                    title = "Centre-wise Distribution",
                    items = centreCounts,
                    total = totalCount,
                    onItemClick = { centre -> viewModel.setFilterCentre(if (selectedCentre == centre) null else centre) },
                    selectedItem = selectedCentre,
                    interactiveHint = "Tap any Centre to filter report"
                )

                if (selectedCentre != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered by Centre: $selectedCentre",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setFilterCentre(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Centre filter")
                            }
                        }
                    }
                }

                BreakdownCard(
                    title = "LAN Breakdown for Centre",
                    items = lanCounts,
                    total = totalCount
                )
            }

            ReportBreakdownTab.CITY_WISE -> {
                BreakdownCard(
                    title = "City / District-wise Distribution",
                    items = cityCounts,
                    total = totalCount,
                    onItemClick = { city -> viewModel.setFilterCity(if (selectedCity == city) null else city) },
                    selectedItem = selectedCity,
                    interactiveHint = "Tap any City to filter report"
                )

                BreakdownCard(
                    title = "Failure Reasons for Selected Cities",
                    items = reasonCounts,
                    total = totalCount
                )
            }

            ReportBreakdownTab.STATE_WISE -> {
                BreakdownCard(
                    title = "State-wise Distribution",
                    items = stateCounts,
                    total = totalCount,
                    onItemClick = { state -> viewModel.setFilterState(if (selectedState == state) null else state) },
                    selectedItem = selectedState,
                    interactiveHint = "Tap any State to filter report"
                )

                BreakdownCard(
                    title = "Cities within State View",
                    items = cityCounts,
                    total = totalCount
                )
            }

            ReportBreakdownTab.ZONE_WISE -> {
                BreakdownCard(
                    title = "Zone-wise Distribution",
                    items = zoneCounts,
                    total = totalCount,
                    onItemClick = { zone -> viewModel.setFilterZone(if (selectedZone == zone) null else zone) },
                    selectedItem = selectedZone,
                    interactiveHint = "Tap any Zone (North, West, South, East, Central) to filter"
                )

                BreakdownCard(
                    title = "States within Zone View",
                    items = stateCounts,
                    total = totalCount
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun BreakdownCard(
    title: String,
    items: List<Pair<String, Int>>,
    total: Int,
    onItemClick: ((String) -> Unit)? = null,
    selectedItem: String? = null,
    interactiveHint: String? = null
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (interactiveHint != null && items.isNotEmpty()) {
                    Text(
                        text = interactiveHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No records to summarize in this category.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { (label, count) ->
                    val percentage = if (total > 0) (count * 100) / total else 0
                    val isSelected = selectedItem != null && label.equals(selectedItem, ignoreCase = true)

                    Surface(
                        onClick = { onItemClick?.invoke(label) },
                        enabled = onItemClick != null,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓ $label" else label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count ($percentage%)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (total > 0) count.toFloat() / total.toFloat() else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatLockedSystemsSection(
    repeatSystems: List<RepeatLockedSystemSummary>,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Repeat Locked Systems",
                        tint = if (repeatSystems.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Repeat Locked Systems (2+ Locks)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Grouped by Centre & LAN to isolate chronic hardware/network nodes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (repeatSystems.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${repeatSystems.size} flagged",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (repeatSystems.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            if (repeatSystems.isEmpty()) {
                Text(
                    text = "No systems with 2 or more repeat locks in current filter scope.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                repeatSystems.take(15).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.systemNumber} · ${item.lanName}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = "${item.lockCount}x Locked",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${item.centreCode} · ${item.centreName}" +
                                    listOf(item.districtName, item.stateName).filter { it.isNotBlank() }.joinToString(", ").let {
                                        if (it.isNotBlank()) " ($it)" else ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val reasonSummary = item.topReasons.joinToString(" · ") { "${it.first} (${it.second})" }
                            Text(
                                text = "Top Reasons: $reasonSummary • Shifted: ${item.shiftedCount}/${item.lockCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.macAddresses.isNotEmpty() || item.ipAddresses.isNotEmpty()) {
                                Text(
                                    text = "MAC: ${item.macAddresses.joinToString(", ").ifBlank { "-" }}  |  IP: ${item.ipAddresses.joinToString(", ").ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopedTopReasonsSection(
    selectedScopeTab: Int,
    onScopeTabSelected: (Int) -> Unit,
    centreSummaries: List<ScopedTopReasonsSummary>,
    districtSummaries: List<ScopedTopReasonsSummary>,
    stateSummaries: List<ScopedTopReasonsSummary>,
    modifier: Modifier = Modifier
) {
    val activeList = when (selectedScopeTab) {
        0 -> centreSummaries
        1 -> districtSummaries
        else -> stateSummaries
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Most Frequent Incident Reasons",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Compare recurring incident types by Centre, City / District, or State",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Centre", "District / City", "State").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedScopeTab == idx,
                        onClick = { onScopeTabSelected(idx) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("top_reasons_scope_tab_$idx")
                    )
                }
            }

            HorizontalDivider()

            if (activeList.isEmpty()) {
                Text(
                    text = "No incidents found for this scope.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activeList.take(10).forEach { summary ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = summary.scopeLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${summary.totalIncidents} total • ${summary.repeatSystemsCount} repeat",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            summary.topReasons.take(3).forEachIndexed { rIdx, (reason, count) ->
                                val pct = if (summary.totalIncidents > 0) (count * 100) / summary.totalIncidents else 0
                                Text(
                                    text = "${rIdx + 1}. $reason — $count ($pct%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatLockPreventionCard(
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Actionable Insights",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Action Plan for Repeat Locked Systems",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            val tips = listOf(
                "Quarantine systems flagged 2+ times: replace RAM/SSD or re-seat LAN cable before Shift 1 starts.",
                "Bind static DHCP IP + MAC per switch port to eliminate IP/MAC conflict locks inside high-lock LANs.",
                "Inspect switch uplinks & patch cords in LANs showing multiple distinct systems locking in one shift.",
                "Run a 15-min pre-exam UPS load & thermal check on flagged seats and mark spare buffer nodes in advance."
            )
            tips.forEach { tip ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).size(14.dp)
                    )
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
