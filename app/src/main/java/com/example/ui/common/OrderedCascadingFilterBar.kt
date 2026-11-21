package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

/**
 * Compact on-demand filter trigger pill so screens stay uncluttered until the user taps Filters.
 */
@Composable
fun NothingFilterToggleButton(
    activeCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "filter_toggle_button"
) {
    val isHighlighted = isExpanded || activeCount > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) NothingTheme.colors.accentPrimary else NothingTheme.colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) NothingTheme.colors.accentPrimary else NothingTheme.colors.border
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = if (isExpanded) "Hide Filters" else "Show Filters",
                tint = if (isHighlighted) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (activeCount > 0) "Filters ($activeCount)" else "Filters",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isHighlighted) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Shared ordered cascading dropdown filter bar:
 * 1. State -> 2. City -> 3. Test Centre -> 4. Exam -> 5. LAN -> Shift -> Date
 *
 * When [expanded] is supplied (recommended), the entire filter card stays hidden until toggled on.
 */
@Composable
fun OrderedCascadingFilterBar(
    selectedState: String?,
    stateOptions: List<String>,
    onStateSelected: (String?) -> Unit,
    selectedCity: String?,
    cityOptions: List<String>,
    onCitySelected: (String?) -> Unit,
    selectedCentre: String?,
    centreOptions: List<String>,
    onCentreSelected: (String?) -> Unit,
    selectedLan: String?,
    lanOptions: List<String>,
    onLanSelected: (String?) -> Unit,
    selectedShift: String?,
    shiftOptions: List<String>,
    onShiftSelected: (String?) -> Unit,
    selectedDate: String?,
    dateOptions: List<String>,
    onDateSelected: (String?) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    selectedExam: String? = null,
    examOptions: List<String> = emptyList(),
    onExamSelected: (String?) -> Unit = {},
    showExamFilter: Boolean = true,
    tagPrefix: String = "filter_dropdown",
    initiallyExpanded: Boolean = true,
    expanded: Boolean? = null,
    onToggleExpanded: (() -> Unit)? = null,
    extraActiveCount: Int = 0,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    var internalExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: internalExpanded
    val isExternallyControlled = expanded != null

    val cascadingActiveCount = listOfNotNull(
        selectedState?.takeIf { it.isNotBlank() },
        selectedCity?.takeIf { it.isNotBlank() },
        selectedCentre?.takeIf { it.isNotBlank() },
        selectedExam?.takeIf { it.isNotBlank() },
        selectedLan?.takeIf { it.isNotBlank() },
        selectedShift?.takeIf { it.isNotBlank() },
        selectedDate?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
    ).size
    val totalActiveCount = cascadingActiveCount + extraActiveCount

    val handleToggle: () -> Unit = {
        if (onToggleExpanded != null) {
            onToggleExpanded()
        } else {
            internalExpanded = !internalExpanded
        }
    }

    val panelBody: @Composable () -> Unit = {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = NothingTheme.colors.surface,
            border = BorderStroke(1.dp, NothingTheme.colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (totalActiveCount > 0) "FILTERS ($totalActiveCount)" else "FILTERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (totalActiveCount > 0) {
                            TextButton(
                                onClick = onClearAll,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("filter_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear all filters",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        TextButton(
                            onClick = handleToggle,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(if (isExternallyControlled) "filter_panel_hide_button" else "filter_toggle_button")
                        ) {
                            Text(
                                text = if (isExpanded) "Hide" else "Show",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse filters" else "Expand filters",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (extraContent != null) {
                            extraContent()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OrderedFilterDropdownButton(
                                label = "1. State",
                                selectedValue = selectedState,
                                options = stateOptions,
                                onSelect = onStateSelected,
                                icon = Icons.Default.Public,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("${tagPrefix}_state")
                            )
                            OrderedFilterDropdownButton(
                                label = "2. City",
                                selectedValue = selectedCity,
                                options = cityOptions,
                                onSelect = onCitySelected,
                                icon = Icons.Default.LocationCity,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("${tagPrefix}_city")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OrderedFilterDropdownButton(
                                label = "3. Test Centre",
                                selectedValue = selectedCentre,
                                options = centreOptions,
                                onSelect = onCentreSelected,
                                icon = Icons.Default.Business,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("${tagPrefix}_centre")
                            )
                            if (showExamFilter) {
                                OrderedFilterDropdownButton(
                                    label = "4. Exam",
                                    selectedValue = selectedExam,
                                    options = examOptions,
                                    onSelect = onExamSelected,
                                    icon = Icons.Default.Assignment,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("${tagPrefix}_exam")
                                )
                            } else {
                                OrderedFilterDropdownButton(
                                    label = "4. LAN",
                                    selectedValue = selectedLan,
                                    options = lanOptions,
                                    onSelect = onLanSelected,
                                    icon = Icons.Default.Lan,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("${tagPrefix}_lan")
                                )
                            }
                        }

                        if (showExamFilter) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrderedFilterDropdownButton(
                                    label = "5. LAN",
                                    selectedValue = selectedLan,
                                    options = lanOptions,
                                    onSelect = onLanSelected,
                                    icon = Icons.Default.Lan,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("${tagPrefix}_lan")
                                )
                                OrderedFilterDropdownButton(
                                    label = "Shift",
                                    selectedValue = selectedShift,
                                    options = shiftOptions,
                                    onSelect = onShiftSelected,
                                    icon = Icons.Default.Schedule,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("${tagPrefix}_shift")
                                )
                            }

                            OrderedFilterDropdownButton(
                                label = "Date",
                                selectedValue = selectedDate?.takeIf { !it.equals("All", ignoreCase = true) },
                                options = dateOptions.filterNot { it.equals("All", ignoreCase = true) },
                                onSelect = onDateSelected,
                                icon = Icons.Default.CalendarMonth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("${tagPrefix}_date")
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrderedFilterDropdownButton(
                                    label = "Shift",
                                    selectedValue = selectedShift,
                                    options = shiftOptions,
                                    onSelect = onShiftSelected,
                                    icon = Icons.Default.Schedule,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("${tagPrefix}_shift")
                                )
                                OrderedFilterDropdownButton(
                                    label = "Date",
                                    selectedValue = selectedDate?.takeIf { !it.equals("All", ignoreCase = true) },
                                    options = dateOptions.filterNot { it.equals("All", ignoreCase = true) },
                                    onSelect = onDateSelected,
                                    icon = Icons.Default.CalendarMonth,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("${tagPrefix}_date")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isExternallyControlled) {
        AnimatedVisibility(visible = isExpanded) {
            panelBody()
        }
    } else {
        panelBody()
    }
}

@Composable
fun OrderedFilterDropdownButton(
    label: String,
    selectedValue: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSelection = !selectedValue.isNullOrBlank()

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (hasSelection) {
                    NothingTheme.colors.accentPrimary
                } else {
                    NothingTheme.colors.surfaceElevated
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (hasSelection) {
                    NothingTheme.colors.accentPrimary
                } else {
                    NothingTheme.colors.border
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            contentDescription = label,
                            tint = if (hasSelection) {
                                NothingTheme.colors.onAccentPrimary
                            } else {
                                NothingTheme.colors.textSecondary
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (hasSelection) "$label: $selectedValue" else "$label: All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (hasSelection) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (hasSelection) {
                            NothingTheme.colors.onAccentPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Open $label dropdown",
                    tint = if (hasSelection) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "All ($label)",
                        fontWeight = if (!hasSelection) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No records in current scope",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTheme.colors.textSecondary
                        )
                    },
                    enabled = false,
                    onClick = {}
                )
            } else {
                options.forEach { opt ->
                    val isCurrent = selectedValue != null && opt.equals(selectedValue, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
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
}
