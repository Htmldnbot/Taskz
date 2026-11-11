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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val selectedDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()

    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentreNames.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableZones = viewModel.availableZones

    var activeTab by remember { mutableStateOf(ReportBreakdownTab.OVERVIEW) }

    val hasActiveFilter = myReportsOnly || selectedLan != null || selectedCentre != null ||
        selectedCity != null || selectedState != null || selectedZone != null ||
        (selectedDateRange != null && selectedDateRange != "all")

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

        // Filter Controls Bar (Scope Filters)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Filters",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        val allFilterLans = remember(availableLans) {
            val presets = (1..25).map { "LAN $it" }
            (availableLans + presets).distinct().sortedWith(compareBy({
                it.removePrefix("LAN ").trim().toIntOrNull() ?: Int.MAX_VALUE
            }, { it }))
        }

        // Dropdown Filters Row 1: LAN, Centre, Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdownButton(
                label = "LAN",
                selectedValue = selectedLan,
                options = allFilterLans,
                onSelect = { viewModel.setFilterLan(it) },
                icon = Icons.Default.Router,
                modifier = Modifier.weight(1f).testTag("reports_dropdown_lan")
            )

            FilterDropdownButton(
                label = "Centre",
                selectedValue = selectedCentre,
                options = availableCentres,
                onSelect = { viewModel.setFilterCentre(it) },
                icon = Icons.Default.Map,
                modifier = Modifier.weight(1.2f).testTag("reports_dropdown_centre")
            )

            FilterDropdownButton(
                label = "Date",
                selectedValue = when (selectedDateRange) {
                    "today" -> "Today"
                    "yesterday" -> "Yesterday"
                    else -> null
                },
                options = listOf("Today", "Yesterday"),
                onSelect = { selected ->
                    viewModel.setFilterDateRange(
                        when (selected) {
                            "Today" -> "today"
                            "Yesterday" -> "yesterday"
                            else -> "all"
                        }
                    )
                },
                modifier = Modifier.weight(0.9f).testTag("reports_dropdown_date")
            )
        }

        // Dropdown Filters Row 2: City, State, Zone
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdownButton(
                label = "City",
                selectedValue = selectedCity,
                options = (availableCities + listOf("Jaipur")).distinct().sorted(),
                onSelect = { viewModel.setFilterCity(it) },
                icon = Icons.Default.LocationCity,
                modifier = Modifier.weight(1f).testTag("reports_dropdown_city")
            )

            FilterDropdownButton(
                label = "State",
                selectedValue = selectedState,
                options = (availableStates + listOf("Rajasthan")).distinct().sorted(),
                onSelect = { viewModel.setFilterState(it) },
                icon = Icons.Default.Public,
                modifier = Modifier.weight(1f).testTag("reports_dropdown_state")
            )

            FilterDropdownButton(
                label = "Zone",
                selectedValue = selectedZone,
                options = availableZones,
                onSelect = { viewModel.setFilterZone(it) },
                icon = Icons.Default.FilterList,
                modifier = Modifier.weight(1f).testTag("reports_dropdown_zone")
            )
        }

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
                title = "Shifted Systems",
                value = "$shiftedCount",
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
            MetricCard(
                title = "Pending Sync",
                value = "$pendingSyncCount",
                modifier = Modifier.weight(1f),
                containerColor = if (pendingSyncCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
            )
        }

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
