package com.example.ui.incidents

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskGroup
import com.example.ui.incidents.components.SystemNumberField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    val context = LocalContext.current

    var showCentreDialog by remember { mutableStateOf(false) }
    var showExamEditDialog by remember { mutableStateOf(false) }
    var showReasonMenu by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Date Picker Dialog
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

    // Time Picker Dialog
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ==========================================
        // 1. SMART AUTO-FILL: CENTRE SUMMARY CARD
        // ==========================================
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("centre_summary_card")
                .clickable { showCentreDialog = true },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Business,
                    contentDescription = "Centre",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${formState.centreCode.ifBlank { "141" }} \u00B7 ${formState.centreName.ifBlank { "Yagyavalkya Institute of Technology" }}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${formState.districtName.ifBlank { "Jaipur" }}, ${formState.stateName.ifBlank { "Rajasthan" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Change Centre",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==========================================
        // 2. EXAMINATION DETAILS ROW
        // ==========================================
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Date picker chip
                    FilterChip(
                        selected = true,
                        onClick = { openDatePicker() },
                        label = {
                            Text(
                                text = formState.examDate.ifBlank { "Select Date" },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Date",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("exam_date_chip")
                    )

                    // Exam name button
                    TextButton(
                        onClick = { showExamEditDialog = true },
                        modifier = Modifier.testTag("exam_name_btn")
                    ) {
                        Text(
                            text = formState.examName.take(28) + if (formState.examName.length > 28) "..." else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Exam",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Shift chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Shift:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    STANDARD_SHIFTS.forEach { shift ->
                        val isSelected = formState.shiftName.equals(shift, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onShiftNameChanged(shift) },
                            label = { Text(shift, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Done,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("shift_chip_$shift")
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. LAN NAME & IT MANAGER NAME
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // LAN selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lan,
                    contentDescription = "LAN",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LAN Selection:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val lans = (1..15).map { "LAN $it" }
            val lanScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(lanScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lans.forEach { lan ->
                    val isSelected = formState.lanName.equals(lan, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onLanNameChanged(lan) },
                        label = { Text(lan, style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.testTag("lan_chip_$lan")
                    )
                }
            }

            // IT Manager name
            OutlinedTextField(
                value = formState.itManagerName,
                onValueChange = { viewModel.onItManagerNameChanged(it) },
                label = { Text("IT Manager Name") },
                placeholder = { Text("e.g. Rahul Sharma") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "IT Manager",
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("it_manager_input")
            )
        }

        // ==========================================
        // 4. MANUAL HARDWARE FIELDS: MAC & IP
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = formState.macAddress,
                onValueChange = { viewModel.onMacAddressChanged(it) },
                label = { Text("MAC Address") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("mac_address_input")
            )

            OutlinedTextField(
                value = formState.ipAddress,
                onValueChange = { viewModel.onIpAddressChanged(it) },
                label = { Text("IP Address") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("ip_address_input")
            )
        }

        // ==========================================
        // 5. SYSTEM NUMBERS & CANDIDATE SHIFTED
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            SystemNumberField(
                value = formState.lockedSystemNumber,
                onValueChange = { viewModel.onLockedSystemNumberChanged(it) },
                label = "Locked System",
                modifier = Modifier.weight(1f),
                testTag = "locked_system_input"
            )

            // Candidate Shifted Toggle Card
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Candidate Shifted?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !formState.candidateShifted,
                            onClick = { viewModel.onCandidateShiftedChanged(false) },
                            label = { Text("No", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("candidate_shifted_no")
                        )
                        FilterChip(
                            selected = formState.candidateShifted,
                            onClick = { viewModel.onCandidateShiftedChanged(true) },
                            label = { Text("Yes", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("candidate_shifted_yes")
                        )
                    }
                }
            }
        }

        // If Candidate Shifted is Yes -> Show Newly Allocated System Number
        if (formState.candidateShifted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SystemNumberField(
                    value = formState.newlyAllocatedSystemNumber,
                    onValueChange = { viewModel.onNewlyAllocatedSystemNumberChanged(it) },
                    label = "Newly Allocated System",
                    modifier = Modifier.weight(1f),
                    testTag = "newly_allocated_system_input"
                )

                // Informative badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "Replacement required. Must differ from locked system.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // ==========================================
        // 6. FAILURE TIME & REASON
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Failure Time Card
            OutlinedCard(
                modifier = Modifier
                    .weight(0.9f)
                    .clickable { openTimePicker() }
                    .testTag("incident_time_card"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Failure Time",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Failure Time", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = formState.incidentTime.ifBlank { "Now" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Reason Dropdown Trigger
            Column(modifier = Modifier.weight(1.1f)) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReasonMenu = true }
                        .testTag("reason_selector_card"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Failure Reason", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = formState.reason,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand Reasons",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

        // Custom Reason field if "Other" is chosen
        if (formState.reason.equals("Other", ignoreCase = true)) {
            OutlinedTextField(
                value = formState.customReason,
                onValueChange = { viewModel.onCustomReasonChanged(it) },
                label = { Text("Specify Custom Reason") },
                placeholder = { Text("Describe system issue...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_reason_input")
            )
        }

        // ==========================================
        // 7. VALIDATION / SUCCESS FEEDBACK BANNERS
        // ==========================================
        if (!formState.validationError.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("validation_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Validation Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formState.validationError ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (!formState.successMessage.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("success_banner")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formState.successMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // ==========================================
        // 8. ACTION BUTTONS: SAVE & NEXT / SAVE
        // ==========================================
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.saveIncident(saveAndNext = true) },
                enabled = !formState.isSubmitting,
                modifier = Modifier
                    .weight(1.3f)
                    .height(52.dp)
                    .testTag("btn_save_and_next"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
                    Text("Save & Next", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = { viewModel.saveIncident(saveAndNext = false) },
                enabled = !formState.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_save_finish"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ==========================================
    // DIALOGS: CENTRE SELECTION & EXAM EDIT
    // ==========================================
    if (showCentreDialog) {
        AlertDialog(
            onDismissRequest = { showCentreDialog = false },
            title = { Text("Select Examination Centre") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (availableCentres.isEmpty()) {
                        Text("No other centres available. Default centre is active.")
                    } else {
                        availableCentres.forEach { centre ->
                            val isSelected = formState.selectedCentre?.id == centre.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onCentreSelected(centre)
                                        showCentreDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.onCentreSelected(centre)
                                        showCentreDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = centre.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    val loc = centre.description?.ifBlank { "Jaipur, Rajasthan" } ?: "Jaipur, Rajasthan"
                                    Text(
                                        text = loc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCentreDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showExamEditDialog) {
        var tempExamName by remember { mutableStateOf(formState.examName) }
        AlertDialog(
            onDismissRequest = { showExamEditDialog = false },
            title = { Text("Edit Examination Name") },
            text = {
                OutlinedTextField(
                    value = tempExamName,
                    onValueChange = { tempExamName = it },
                    label = { Text("Examination Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onExamNameChanged(tempExamName)
                    showExamEditDialog = false
                }) {
                    Text("Update")
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
