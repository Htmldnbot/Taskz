package com.example.ui.incidents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingFilterToggleButton
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatTile
import com.example.ui.common.NothingTagChip
import com.example.ui.common.OrderedCascadingFilterBar
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import java.util.Locale

enum class ReportBreakdownTab {
    OVERVIEW,
    EXAM_WISE,
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
    val selectedExam by viewModel.filterExam.collectAsStateWithLifecycle()
    val selectedShift by viewModel.filterShift.collectAsStateWithLifecycle()
    val selectedDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()

    val availableLans by viewModel.availableLans.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentreNames.collectAsStateWithLifecycle()
    val availableCities by viewModel.availableCities.collectAsStateWithLifecycle()
    val availableStates by viewModel.availableStates.collectAsStateWithLifecycle()
    val availableExams by viewModel.availableExams.collectAsStateWithLifecycle()
    val availableShifts by viewModel.availableShifts.collectAsStateWithLifecycle()

    val repeatLockedSystems by viewModel.repeatLockedSystems.collectAsStateWithLifecycle()
    val topReasonsByCentre by viewModel.topReasonsByCentre.collectAsStateWithLifecycle()
    val topReasonsByDistrict by viewModel.topReasonsByDistrict.collectAsStateWithLifecycle()
    val topReasonsByState by viewModel.topReasonsByState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(ReportBreakdownTab.OVERVIEW) }
    var topReasonsScopeTab by remember { mutableIntStateOf(0) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val activeFilterCount = listOfNotNull(
        selectedState?.takeIf { it.isNotBlank() },
        selectedCity?.takeIf { it.isNotBlank() },
        selectedCentre?.takeIf { it.isNotBlank() },
        selectedExam?.takeIf { it.isNotBlank() },
        selectedLan?.takeIf { it.isNotBlank() },
        selectedShift?.takeIf { it.isNotBlank() },
        selectedZone?.takeIf { it.isNotBlank() },
        selectedDateRange?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
    ).size + if (myReportsOnly) 1 else 0
    val hasActiveFilter = activeFilterCount > 0

    val totalCount = incidents.size
    val shiftedCount = incidents.count { it.candidateShifted }

    val reasonCounts = incidents.groupBy { it.reason }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val examCounts = incidents.groupBy { it.examName.ifBlank { "Unspecified Exam" } }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NothingTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                NothingDotMatrixText(
                    text = "ANALYTICS",
                    fontSize = 18.sp,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = if (hasActiveFilter) "${incidents.size} filtered of ${allIncidents.size} total" else "${allIncidents.size} total examination records",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
            IconButton(
                onClick = { viewModel.refreshIncidents() },
                modifier = Modifier.testTag("refresh_reports_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh from Cloud",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Export Action Card
        NothingHairlineCard(
            containerColor = NothingTheme.colors.surfaceElevated,
            contentPadding = PaddingValues(12.dp)
        ) {
            Text(
                text = "Export Incident Register",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (hasActiveFilter) {
                    "Exporting ${incidents.size} filtered records with all technical parameters to CSV."
                } else {
                    "Generate UTF-8 Excel/CSV report with 15 technical fields & replacement logs."
                },
                style = MaterialTheme.typography.labelSmall,
                color = NothingTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.exportReport() },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("export_csv_btn")
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasActiveFilter) "Export Filtered (${incidents.size})" else "Export All to CSV",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingSectionHeader(title = "BREAKDOWN VIEW", useDotMatrix = true)
            NothingFilterToggleButton(
                activeCount = activeFilterCount,
                isExpanded = showFilters,
                onClick = { showFilters = !showFilters },
                testTag = "reports_filter_toggle_button"
            )
        }

        val tabScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReportTabPill("Overview", activeTab == ReportBreakdownTab.OVERVIEW) { activeTab = ReportBreakdownTab.OVERVIEW }
            ReportTabPill("Exam", activeTab == ReportBreakdownTab.EXAM_WISE, Icons.Default.Assignment) { activeTab = ReportBreakdownTab.EXAM_WISE }
            ReportTabPill("LAN", activeTab == ReportBreakdownTab.LAN_WISE, Icons.Default.Router) { activeTab = ReportBreakdownTab.LAN_WISE }
            ReportTabPill("Centre", activeTab == ReportBreakdownTab.CENTER_WISE, Icons.Default.Map) { activeTab = ReportBreakdownTab.CENTER_WISE }
            ReportTabPill("City", activeTab == ReportBreakdownTab.CITY_WISE, Icons.Default.LocationCity) { activeTab = ReportBreakdownTab.CITY_WISE }
            ReportTabPill("State", activeTab == ReportBreakdownTab.STATE_WISE, Icons.Default.Public) { activeTab = ReportBreakdownTab.STATE_WISE }
            ReportTabPill("Zone", activeTab == ReportBreakdownTab.ZONE_WISE, Icons.Default.FilterList) { activeTab = ReportBreakdownTab.ZONE_WISE }
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
            selectedExam = selectedExam,
            examOptions = availableExams,
            onExamSelected = { viewModel.setFilterExam(it) },
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
            expanded = showFilters,
            onToggleExpanded = { showFilters = !showFilters },
            extraActiveCount = (if (myReportsOnly) 1 else 0) + (if (selectedZone != null) 1 else 0),
            extraContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReportTabPill(
                        label = if (myReportsOnly) "My Reports ✓" else "My Reports Only",
                        selected = myReportsOnly,
                        icon = Icons.Default.Person,
                        modifier = Modifier.testTag("reports_my_reports_chip"),
                        onClick = { viewModel.setFilterMyReportsOnly(!myReportsOnly) }
                    )
                    if (hasActiveFilter) {
                        ReportTabPill(
                            label = "Clear Filters",
                            selected = false,
                            icon = Icons.Default.Clear,
                            onClick = { viewModel.clearAllFilters() }
                        )
                    }
                }
            }
        )

        // KPI Telemetry Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NothingStatTile(
                value = "$totalCount",
                label = "Incidents",
                modifier = Modifier.weight(1f)
            )
            NothingStatTile(
                value = "${repeatLockedSystems.size}",
                label = "Repeat",
                statusDotColor = if (repeatLockedSystems.isNotEmpty()) NothingRed else null,
                modifier = Modifier.weight(1f)
            )
            NothingStatTile(
                value = "$shiftedCount",
                label = "Shifted",
                statusDotColor = if (shiftedCount > 0) NothingAmber else null,
                modifier = Modifier.weight(1f)
            )
        }

        RepeatLockedSystemsSection(
            repeatSystems = repeatLockedSystems,
            modifier = Modifier.testTag("repeat_locked_systems_section")
        )

        ScopedTopReasonsSection(
            selectedScopeTab = topReasonsScopeTab,
            onScopeTabSelected = { topReasonsScopeTab = it },
            centreSummaries = topReasonsByCentre,
            districtSummaries = topReasonsByDistrict,
            stateSummaries = topReasonsByState,
            modifier = Modifier.testTag("top_incident_reasons_section")
        )

        RepeatLockPreventionCard(
            modifier = Modifier.testTag("repeat_lock_prevention_card")
        )

        when (activeTab) {
            ReportBreakdownTab.OVERVIEW -> {
                BreakdownCard(title = "By Failure Reason", items = reasonCounts, total = totalCount)
                BreakdownCard(
                    title = "By Exam",
                    items = examCounts,
                    total = totalCount,
                    onItemClick = { exam -> viewModel.setFilterExam(if (selectedExam == exam) null else exam) },
                    selectedItem = selectedExam
                )
                BreakdownCard(
                    title = "By LAN",
                    items = lanCounts,
                    total = totalCount,
                    onItemClick = { lan -> viewModel.setFilterLan(if (selectedLan == lan) null else lan) },
                    selectedItem = selectedLan
                )
                BreakdownCard(
                    title = "By Examination Centre",
                    items = centreCounts,
                    total = totalCount,
                    onItemClick = { centre -> viewModel.setFilterCentre(if (selectedCentre == centre) null else centre) },
                    selectedItem = selectedCentre
                )
            }
            ReportBreakdownTab.EXAM_WISE -> {
                BreakdownCard(
                    title = "Exam-wise Distribution",
                    items = examCounts,
                    total = totalCount,
                    onItemClick = { exam -> viewModel.setFilterExam(if (selectedExam == exam) null else exam) },
                    selectedItem = selectedExam,
                    interactiveHint = "Tap Exam to filter"
                )
                if (selectedExam != null) {
                    NothingHairlineCard(
                        containerColor = NothingTheme.colors.surfaceElevated,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exam: $selectedExam ($totalCount)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setFilterExam(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Exam filter")
                            }
                        }
                    }
                }
                BreakdownCard(title = "Failure Reasons in Exam", items = reasonCounts, total = totalCount)
            }
            ReportBreakdownTab.LAN_WISE -> {
                BreakdownCard(
                    title = "LAN-wise Distribution",
                    items = lanCounts,
                    total = totalCount,
                    onItemClick = { lan -> viewModel.setFilterLan(if (selectedLan == lan) null else lan) },
                    selectedItem = selectedLan,
                    interactiveHint = "Tap LAN to filter"
                )
                if (selectedLan != null) {
                    NothingHairlineCard(
                        containerColor = NothingTheme.colors.surfaceElevated,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered by $selectedLan ($totalCount)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { viewModel.setFilterLan(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear LAN filter")
                            }
                        }
                    }
                }
                BreakdownCard(title = "Failure Reasons in Current View", items = reasonCounts, total = totalCount)
            }
            ReportBreakdownTab.CENTER_WISE -> {
                BreakdownCard(
                    title = "Centre-wise Distribution",
                    items = centreCounts,
                    total = totalCount,
                    onItemClick = { centre -> viewModel.setFilterCentre(if (selectedCentre == centre) null else centre) },
                    selectedItem = selectedCentre,
                    interactiveHint = "Tap Centre to filter"
                )
                if (selectedCentre != null) {
                    NothingHairlineCard(
                        containerColor = NothingTheme.colors.surfaceElevated,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Centre: $selectedCentre",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.setFilterCentre(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Centre filter")
                            }
                        }
                    }
                }
                BreakdownCard(title = "LAN Breakdown", items = lanCounts, total = totalCount)
            }
            ReportBreakdownTab.CITY_WISE -> {
                BreakdownCard(
                    title = "City / District-wise Distribution",
                    items = cityCounts,
                    total = totalCount,
                    onItemClick = { city -> viewModel.setFilterCity(if (selectedCity == city) null else city) },
                    selectedItem = selectedCity,
                    interactiveHint = "Tap City to filter"
                )
                BreakdownCard(title = "Failure Reasons", items = reasonCounts, total = totalCount)
            }
            ReportBreakdownTab.STATE_WISE -> {
                BreakdownCard(
                    title = "State-wise Distribution",
                    items = stateCounts,
                    total = totalCount,
                    onItemClick = { state -> viewModel.setFilterState(if (selectedState == state) null else state) },
                    selectedItem = selectedState,
                    interactiveHint = "Tap State to filter"
                )
                BreakdownCard(title = "Cities within State", items = cityCounts, total = totalCount)
            }
            ReportBreakdownTab.ZONE_WISE -> {
                BreakdownCard(
                    title = "Zone-wise Distribution",
                    items = zoneCounts,
                    total = totalCount,
                    onItemClick = { zone -> viewModel.setFilterZone(if (selectedZone == zone) null else zone) },
                    selectedItem = selectedZone,
                    interactiveHint = "Tap Zone to filter"
                )
                BreakdownCard(title = "States within Zone", items = stateCounts, total = totalCount)
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ReportTabPill(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) NothingTheme.colors.accentPrimary else NothingTheme.colors.surface,
        border = BorderStroke(1.dp, if (selected) NothingTheme.colors.accentPrimary else NothingTheme.colors.border),
        modifier = modifier.heightIn(min = 34.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = NothingTheme.colors.surface
) {
    NothingStatTile(
        value = value,
        label = title,
        modifier = modifier
    )
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
    NothingHairlineCard(contentPadding = PaddingValues(12.dp)) {
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
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        NothingDivider()
        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            Text(
                text = "No records to summarize in this category.",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTheme.colors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { (label, count) ->
                    val percentage = if (total > 0) (count * 100) / total else 0
                    val isSelected = selectedItem != null && label.equals(selectedItem, ignoreCase = true)

                    Surface(
                        onClick = { onItemClick?.invoke(label) },
                        enabled = onItemClick != null,
                        color = if (isSelected) NothingTheme.colors.surfaceElevated else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓ $label" else label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count ($percentage%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            LinearProgressIndicator(
                                progress = { if (total > 0) count.toFloat() / total.toFloat() else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = NothingTheme.colors.border
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
    NothingHairlineCard(
        modifier = modifier,
        borderColor = if (repeatSystems.isNotEmpty()) NothingRed.copy(alpha = 0.45f) else NothingTheme.colors.border,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Repeat Locked Systems",
                    tint = if (repeatSystems.isNotEmpty()) NothingRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Repeat Locked Systems (2+ Locks)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Chronic hardware/network nodes by Centre & LAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }
            NothingTagChip(
                text = "${repeatSystems.size} FLAGGED",
                containerColor = if (repeatSystems.isNotEmpty()) NothingRed.copy(alpha = 0.14f) else NothingTheme.colors.surfaceElevated,
                contentColor = if (repeatSystems.isNotEmpty()) NothingRed else NothingTheme.colors.textSecondary,
                borderColor = if (repeatSystems.isNotEmpty()) NothingRed.copy(alpha = 0.4f) else NothingTheme.colors.border
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        NothingDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (repeatSystems.isEmpty()) {
            Text(
                text = "No systems with 2 or more repeat locks in current filter scope.",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTheme.colors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeatSystems.take(15).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NothingTheme.colors.surfaceElevated,
                        border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.systemNumber} · ${item.lanName}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = TechMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                NothingTagChip(
                                    text = "${item.lockCount}x LOCKED",
                                    containerColor = NothingRed,
                                    contentColor = Color.White,
                                    borderColor = NothingRed
                                )
                            }
                            Text(
                                text = "${item.centreCode} · ${item.centreName}" +
                                    listOf(item.districtName, item.stateName).filter { it.isNotBlank() }.joinToString(", ").let {
                                        if (it.isNotBlank()) " ($it)" else ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary
                            )
                            val reasonSummary = item.topReasons.joinToString(" · ") { "${it.first} (${it.second})" }
                            Text(
                                text = "Reasons: $reasonSummary · Shifted: ${item.shiftedCount}/${item.lockCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (item.macAddresses.isNotEmpty() || item.ipAddresses.isNotEmpty()) {
                                Text(
                                    text = "MAC: ${item.macAddresses.joinToString(", ").ifBlank { "-" }}  |  IP: ${item.ipAddresses.joinToString(", ").ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = NothingTheme.colors.textSecondary
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

    NothingHairlineCard(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = "Most Frequent Incident Reasons",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Compare recurring incident types by Centre, City / District, or State",
            style = MaterialTheme.typography.labelSmall,
            color = NothingTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Centre", "District / City", "State").forEachIndexed { idx, label ->
                ReportTabPill(
                    label = label,
                    selected = selectedScopeTab == idx,
                    modifier = Modifier.testTag("top_reasons_scope_tab_$idx"),
                    onClick = { onScopeTabSelected(idx) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        NothingDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (activeList.isEmpty()) {
            Text(
                text = "No incidents found for this scope.",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTheme.colors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeList.take(10).forEach { summary ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NothingTheme.colors.surfaceElevated,
                        border = BorderStroke(1.dp, NothingTheme.colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = summary.scopeLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${summary.totalIncidents} total · ${summary.repeatSystemsCount} repeat",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            summary.topReasons.take(3).forEachIndexed { rIdx, (reason, count) ->
                                val pct = if (summary.totalIncidents > 0) (count * 100) / summary.totalIncidents else 0
                                Text(
                                    text = "${rIdx + 1}. $reason — $count ($pct%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTheme.colors.textSecondary
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
    NothingHairlineCard(
        modifier = modifier,
        containerColor = NothingTheme.colors.surfaceElevated,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Actionable Insights",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Action Plan for Repeat Locked Systems",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        val tips = listOf(
            "Quarantine systems flagged 2+ times: replace RAM/SSD or re-seat LAN cable before Shift 1 starts.",
            "Bind static DHCP IP + MAC per switch port to eliminate IP/MAC conflict locks inside high-lock LANs.",
            "Inspect switch uplinks & patch cords in LANs showing multiple distinct systems locking in one shift.",
            "Run a 15-min pre-exam UPS load & thermal check on flagged seats and mark spare buffer nodes in advance."
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tips.forEach { tip ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp).size(13.dp)
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
