package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * Shared ordered cascading dropdown filter bar:
 * 1. State -> 2. City -> 3. Test Centre -> 4. LAN -> Shift -> Date
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
    tagPrefix: String = "filter_dropdown",
    initiallyExpanded: Boolean = true
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val activeCount = listOfNotNull(
        selectedState?.takeIf { it.isNotBlank() },
        selectedCity?.takeIf { it.isNotBlank() },
        selectedCentre?.takeIf { it.isNotBlank() },
        selectedLan?.takeIf { it.isNotBlank() },
        selectedShift?.takeIf { it.isNotBlank() },
        selectedDate?.takeIf { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
    ).size

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (activeCount > 0) "Filters ($activeCount active)" else "Filters",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activeCount > 0) {
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("filter_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear all filters",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_toggle_button")
                    ) {
                        Text(
                            text = if (expanded) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse filters" else "Expand filters",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: 1. State -> 2. City
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                    // Row 2: 3. Test Centre -> 4. LAN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OrderedFilterDropdownButton(
                            label = "3. Test Centre",
                            selectedValue = selectedCentre,
                            options = centreOptions,
                            onSelect = onCentreSelected,
                            icon = Icons.Default.Business,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("${tagPrefix}_centre")
                        )
                        OrderedFilterDropdownButton(
                            label = "4. LAN",
                            selectedValue = selectedLan,
                            options = lanOptions,
                            onSelect = onLanSelected,
                            icon = Icons.Default.Lan,
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("${tagPrefix}_lan")
                        )
                    }

                    // Row 3: Shift -> Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (hasSelection) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
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
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (hasSelection) "$label: $selectedValue" else "$label: All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (hasSelection) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (hasSelection) {
                            MaterialTheme.colorScheme.onPrimaryContainer
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = if (!hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
