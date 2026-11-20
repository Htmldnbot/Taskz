package com.example.ui.incidents

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.OrgCentre
import com.example.core.model.SystemIncident
import com.example.core.model.TaskGroup
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingTagChip
import com.example.ui.incidents.components.SystemNumberField
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val STANDARD_REASONS = listOf(
    "System Hang",
    "System Lock",
    "Network Disconnection",
    "Keyboard Fault",
    "Mouse Fault",
    "Power Failure",
    "Hardware Failure",
    "Software/Application Error",
    "System Restart",
    "Other"
)

private val STANDARD_SHIFTS = listOf("Shift 1", "Shift 2", "Shift 3", "Shift 4")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntryScreen(
    viewModel: SystemIncidentViewModel,
    modifier: Modifier = Modifier
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val availableCentres by viewModel.availableCentres.collectAsStateWithLifecycle()
    val centreLans by viewModel.availableCentreLans.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCentreDialog by remember { mutableStateOf(false) }
    var showAddCentreDialog by remember { mutableStateOf(false) }
    var editingCentre by remember { mutableStateOf<TaskGroup?>(null) }
    var centreSearchQuery by remember { mutableStateOf("") }
    var showExamEditDialog by remember { mutableStateOf(false) }
    var showReasonMenu by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = NothingTheme.colors.border,
        focusedContainerColor = NothingTheme.colors.surface,
        unfocusedContainerColor = NothingTheme.colors.surface
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = NothingTheme.colors.surface,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = NothingTheme.colors.accentPrimary,
        selectedLabelColor = NothingTheme.colors.onAccentPrimary,
        selectedLeadingIconColor = NothingTheme.colors.onAccentPrimary
    )

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        val dpd = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                viewModel.onExamDateChanged(format.format(chosen.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show()
    }

    fun openTimePicker() {
        val cal = Calendar.getInstance()
        val tpd = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
                viewModel.onIncidentTimeChanged(format.format(chosen.time), chosen.timeInMillis)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )
        tpd.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. CENTRE SUMMARY CARD
        NothingHairlineCard(
            onClick = { showCentreDialog = true },
            containerColor = NothingTheme.colors.surfaceElevated,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.testTag("centre_summary_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Business,
                    contentDescription = "Centre",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingTagChip(
                            text = formState.centreCode.ifBlank { "141" },
                            mono = true
                        )
                        Text(
                            text = formState.centreName.ifBlank { "Yagyavalkya Institute of Technology" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${formState.districtName.ifBlank { "Jaipur" }}, ${formState.stateName.ifBlank { "Rajasthan" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Change Centre",
                    tint = NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2. EXAMINATION DETAILS CARD
        NothingHairlineCard(contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = true,
                    onClick = { openDatePicker() },
                    colors = chipColors,
                    label = {
                        Text(
                            text = formState.examDate.ifBlank { "Select Date" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = "Date",
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.testTag("exam_date_chip")
                )

                TextButton(
                    onClick = { showExamEditDialog = true },
                    modifier = Modifier.testTag("exam_name_btn")
                ) {
                    Text(
                        text = formState.examName.take(26) + if (formState.examName.length > 26) "..." else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Exam",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SHIFT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                STANDARD_SHIFTS.forEach { shift ->
                    val isSelected = formState.shiftName.equals(shift, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onShiftNameChanged(shift) },
                        label = {
                            Text(
                                text = shift,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        colors = chipColors,
                        modifier = Modifier.testTag("shift_chip_$shift")
                    )
                }
            }
        }

        // 3. LAN NAME & IT MANAGER NAME
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NothingSectionHeader(
                title = "LAN (${centreLans.firstOrNull() ?: "LAN 1"} - ${centreLans.lastOrNull() ?: "LAN 11"})"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                centreLans.forEach { lan ->
                    val isSelected = formState.lanName.equals(lan, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onLanNameChanged(lan) },
                        label = {
                            Text(
                                text = lan,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily)
                            )
                        },
                        colors = chipColors,
                        modifier = Modifier.testTag("lan_chip_$lan")
                    )
                }
            }

            OutlinedTextField(
                value = formState.lanName,
                onValueChange = { viewModel.onLanNameChanged(it) },
                label = { Text("Selected LAN / Room *") },
                placeholder = { Text("e.g. ${centreLans.firstOrNull() ?: "LAN 1"}") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lan,
                        contentDescription = "LAN Number",
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TechMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lan_input")
            )

            OutlinedTextField(
                value = formState.itManagerName,
                onValueChange = { viewModel.onItManagerNameChanged(it) },
                label = { Text("IT Manager Name") },
                placeholder = { Text("e.g. Rahul Sharma") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "IT Manager",
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("it_manager_input")
            )
        }

        // 4. MANUAL HARDWARE FIELDS: MAC & IP
        var macFieldValue by remember {
            mutableStateOf(TextFieldValue(formState.macAddress, TextRange(formState.macAddress.length)))
        }
        LaunchedEffect(formState.macAddress) {
            if (formState.macAddress != macFieldValue.text) {
                macFieldValue = TextFieldValue(formState.macAddress, TextRange(formState.macAddress.length))
            }
        }

        var ipFieldValue by remember {
            mutableStateOf(TextFieldValue(formState.ipAddress, TextRange(formState.ipAddress.length)))
        }
        LaunchedEffect(formState.ipAddress) {
            if (formState.ipAddress != ipFieldValue.text) {
                ipFieldValue = TextFieldValue(formState.ipAddress, TextRange(formState.ipAddress.length))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = macFieldValue,
                onValueChange = { incoming ->
                    val (formatted, newCursor) = SystemIncident.formatMacAddressWithCursor(
                        rawText = incoming.text,
                        rawSelectionEnd = incoming.selection.end
                    )
                    macFieldValue = TextFieldValue(formatted, TextRange(newCursor))
                    viewModel.onMacAddressChanged(formatted)
                },
                label = { Text("MAC Address") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = TechMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("mac_address_input")
            )

            OutlinedTextField(
                value = ipFieldValue,
                onValueChange = { incoming ->
                    ipFieldValue = incoming
                    viewModel.onIpAddressChanged(incoming.text)
                },
                label = { Text("IP Address") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = TechMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("ip_address_input")
            )
        }

        // 5. SYSTEM NUMBERS & CANDIDATE SHIFTED
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            SystemNumberField(
                value = formState.lockedSystemNumber,
                onValueChange = { viewModel.onLockedSystemNumberChanged(it) },
                label = "Locked Seat No.",
                modifier = Modifier.weight(1f),
                testTag = "locked_system_input"
            )

            NothingHairlineCard(
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 66.dp)
            ) {
                Text(
                    text = "CANDIDATE SHIFTED?",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontSize = 10.sp
                    ),
                    color = NothingTheme.colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !formState.candidateShifted,
                        onClick = { viewModel.onCandidateShiftedChanged(false) },
                        label = { Text("No", style = MaterialTheme.typography.labelMedium) },
                        colors = chipColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("candidate_shifted_no")
                    )
                    FilterChip(
                        selected = formState.candidateShifted,
                        onClick = { viewModel.onCandidateShiftedChanged(true) },
                        label = { Text("Yes", style = MaterialTheme.typography.labelMedium) },
                        colors = chipColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("candidate_shifted_yes")
                    )
                }
            }
        }

        if (formState.candidateShifted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SystemNumberField(
                    value = formState.newlyAllocatedSystemNumber,
                    onValueChange = { viewModel.onNewlyAllocatedSystemNumberChanged(it) },
                    label = "New Seat No.",
                    modifier = Modifier.weight(1f),
                    testTag = "newly_allocated_system_input"
                )

                NothingHairlineCard(
                    containerColor = NothingTheme.colors.surfaceElevated,
                    contentPadding = PaddingValues(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Replacement required. Must differ from locked system.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTheme.colors.textSecondary
                    )
                }
            }
        }

        // 6. FAILURE TIME & REASON
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingHairlineCard(
                onClick = { openTimePicker() },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("incident_time_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Failure Time",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FAILURE TIME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontSize = 10.sp
                            ),
                            color = NothingTheme.colors.textSecondary
                        )
                        Text(
                            text = formState.incidentTime.ifBlank { "Now" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = TechMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1.1f)) {
                NothingHairlineCard(
                    onClick = { showReasonMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.testTag("reason_selector_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FAILURE REASON",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = TechMonoFontFamily,
                                    fontSize = 10.sp
                                ),
                                color = NothingTheme.colors.textSecondary
                            )
                            Text(
                                text = formState.reason,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand Reasons",
                            tint = NothingTheme.colors.textSecondary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showReasonMenu,
                    onDismissRequest = { showReasonMenu = false }
                ) {
                    STANDARD_REASONS.forEach { reasonItem ->
                        DropdownMenuItem(
                            text = { Text(reasonItem) },
                            onClick = {
                                viewModel.onReasonChanged(reasonItem)
                                showReasonMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (formState.reason.equals("Other", ignoreCase = true)) {
            OutlinedTextField(
                value = formState.customReason,
                onValueChange = { viewModel.onCustomReasonChanged(it) },
                label = { Text("Specify Custom Reason") },
                placeholder = { Text("Describe system issue...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_reason_input")
            )
        }

        // 7. VALIDATION / SUCCESS FEEDBACK BANNERS
        if (!formState.validationError.isNullOrBlank()) {
            NothingHairlineCard(
                containerColor = NothingRed.copy(alpha = 0.12f),
                borderColor = NothingRed.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.testTag("validation_error_banner")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Validation Error",
                        tint = NothingRed
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formState.validationError ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NothingRed
                    )
                }
            }
        }

        if (!formState.successMessage.isNullOrBlank()) {
            NothingHairlineCard(
                containerColor = NothingGreen.copy(alpha = 0.12f),
                borderColor = NothingGreen.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.testTag("success_banner")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = NothingGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formState.successMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NothingGreen
                    )
                }
            }
        }

        // 8. ACTION BUTTONS: SAVE & NEXT / SAVE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.saveIncident(saveAndNext = true) },
                enabled = !formState.isSubmitting,
                modifier = Modifier
                    .weight(1.3f)
                    .height(50.dp)
                    .testTag("btn_save_and_next"),
                shape = RoundedCornerShape(999.dp)
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Next", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { viewModel.saveIncident(saveAndNext = false) },
                enabled = !formState.isSubmitting,
                border = BorderStroke(1.dp, NothingTheme.colors.border),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("btn_save_finish"),
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    // DIALOGS: CENTRE SELECTION & EXAM EDIT
    if (showCentreDialog) {
        val filteredCentres = availableCentres.filter {
            centreSearchQuery.isBlank() ||
                it.title.contains(centreSearchQuery, ignoreCase = true) ||
                it.description?.contains(centreSearchQuery, ignoreCase = true) == true ||
                it.code?.contains(centreSearchQuery, ignoreCase = true) == true
        }

        AlertDialog(
            onDismissRequest = {
                showCentreDialog = false
                centreSearchQuery = ""
            },
            containerColor = NothingTheme.colors.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingDotMatrixText(
                        text = "CENTRES",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showAddCentreDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add New Centre",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = centreSearchQuery,
                        onValueChange = { centreSearchQuery = it },
                        placeholder = { Text("Search code, name, or city...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filteredCentres.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (centreSearchQuery.isNotBlank()) "No centres match '$centreSearchQuery'" else "No examination centres configured yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NothingTheme.colors.textSecondary
                                    )
                                }
                            }
                        } else {
                            items(filteredCentres) { centre ->
                                val isSelected = formState.selectedCentre?.id == centre.id
                                NothingHairlineCard(
                                    onClick = {
                                        viewModel.onCentreSelected(centre)
                                        showCentreDialog = false
                                        centreSearchQuery = ""
                                    },
                                    containerColor = if (isSelected) NothingTheme.colors.surfaceElevated else NothingTheme.colors.surface,
                                    borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else NothingTheme.colors.border,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.onCentreSelected(centre)
                                                showCentreDialog = false
                                                centreSearchQuery = ""
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = centre.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                )
                                            )
                                            val loc = centre.description?.ifBlank { "Jaipur, Rajasthan" } ?: "Jaipur, Rajasthan"
                                            Text(
                                                text = loc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NothingTheme.colors.textSecondary
                                            )
                                        }
                                        IconButton(
                                            onClick = { editingCentre = centre },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit Centre",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showAddCentreDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Centre", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCentreDialog = false
                    centreSearchQuery = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddCentreDialog) {
        var newCode by remember { mutableStateOf("") }
        var newName by remember { mutableStateOf("") }
        var newDistrict by remember { mutableStateOf("Jaipur") }
        var newSubHub by remember { mutableStateOf("") }
        var newState by remember { mutableStateOf("Rajasthan") }
        var newLansText by remember { mutableStateOf("LAN 1, LAN 2") }
        var addError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddCentreDialog = false },
            containerColor = NothingTheme.colors.surface,
            title = {
                NothingDotMatrixText(
                    text = "ADD CENTRE",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it.trim() },
                        label = { Text("Centre Code *") },
                        placeholder = { Text("e.g., 41683, 141") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Centre / Institution Name *") },
                        placeholder = { Text("e.g., iON Digital Zone iDZ Sitapura 2") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDistrict,
                        onValueChange = { newDistrict = it },
                        label = { Text("City / District") },
                        placeholder = { Text("e.g., Jaipur, Ajmer, Kota") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubHub,
                        onValueChange = { newSubHub = it },
                        label = { Text("Sub Hub (Optional)") },
                        placeholder = { Text("e.g., Jaipur 2 (Sitapura)") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newState,
                        onValueChange = { newState = it },
                        label = { Text("State") },
                        placeholder = { Text("e.g., Rajasthan") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLansText,
                        onValueChange = { newLansText = it },
                        label = { Text("Centre LANs") },
                        placeholder = { Text("e.g., LAN 4-11 or LAN 1, LAN 2, LAN 3") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Text(
                            text = addError.orEmpty(),
                            color = NothingRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(999.dp),
                    onClick = {
                        if (newName.isBlank()) {
                            addError = "Please enter the centre name."
                        } else {
                            viewModel.createAndSelectCentre(
                                code = newCode,
                                name = newName,
                                district = newDistrict,
                                state = newState,
                                subHub = newSubHub,
                                availableLans = OrgCentre.parseLansInput(newLansText)
                            )
                            showAddCentreDialog = false
                            showCentreDialog = false
                        }
                    }
                ) {
                    Text("Add & Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCentreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingCentre != null) {
        val target = editingCentre!!
        val locParts = target.description?.split(",")?.map { it.trim() } ?: emptyList()
        var editCode by remember(target) {
            mutableStateOf(target.code ?: target.title.substringBefore("\u00B7").trim().filter { it.isDigit() })
        }
        var editName by remember(target) {
            mutableStateOf(if (target.title.contains("\u00B7")) target.title.substringAfter("\u00B7").trim() else target.title)
        }
        var editDistrict by remember(target) {
            mutableStateOf(if (locParts.size >= 2) locParts[locParts.size - 2] else locParts.firstOrNull() ?: "Jaipur")
        }
        var editSubHub by remember(target) {
            mutableStateOf("")
        }
        var editState by remember(target) {
            mutableStateOf(if (locParts.isNotEmpty()) locParts.last() else "Rajasthan")
        }
        var editLansText by remember(target) {
            mutableStateOf(OrgCentre.defaultLansForCode(editCode).joinToString(", "))
        }
        var editError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingCentre = null },
            containerColor = NothingTheme.colors.surface,
            title = {
                NothingDotMatrixText(
                    text = "EDIT CENTRE",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editCode,
                        onValueChange = { editCode = it.trim() },
                        label = { Text("Centre Code *") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Centre Name *") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDistrict,
                        onValueChange = { editDistrict = it },
                        label = { Text("City / District") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSubHub,
                        onValueChange = { editSubHub = it },
                        label = { Text("Sub Hub (Optional)") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editState,
                        onValueChange = { editState = it },
                        label = { Text("State") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editLansText,
                        onValueChange = { editLansText = it },
                        label = { Text("Centre LANs") },
                        placeholder = { Text("e.g., LAN 4-11 or LAN 1, LAN 2") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editError != null) {
                        Text(
                            text = editError.orEmpty(),
                            color = NothingRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(999.dp),
                    onClick = {
                        if (editName.isBlank()) {
                            editError = "Please enter the centre name."
                        } else {
                            viewModel.updateCentre(
                                centreId = target.id,
                                code = editCode,
                                name = editName,
                                district = editDistrict,
                                state = editState,
                                subHub = editSubHub,
                                availableLans = OrgCentre.parseLansInput(editLansText)
                            )
                            editingCentre = null
                        }
                    }
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCentre = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExamEditDialog) {
        var tempExamName by remember { mutableStateOf(formState.examName) }
        AlertDialog(
            onDismissRequest = { showExamEditDialog = false },
            containerColor = NothingTheme.colors.surface,
            title = {
                NothingDotMatrixText(
                    text = "EDIT EXAM NAME",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = tempExamName,
                    onValueChange = { tempExamName = it },
                    label = { Text("Examination Name") },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(999.dp),
                    onClick = {
                        viewModel.onExamNameChanged(tempExamName)
                        showExamEditDialog = false
                    }
                ) {
                    Text("Update", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExamEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
