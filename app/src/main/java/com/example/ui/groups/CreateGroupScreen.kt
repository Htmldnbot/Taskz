package com.example.ui.groups

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskGroup
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(
    viewModel: GroupsViewModel,
    onSuccess: (TaskGroup) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var endDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    var visibility by rememberSaveable { mutableStateOf("everyone") } // "everyone" or "restricted"
    var allowSelfJoin by rememberSaveable { mutableStateOf(true) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(false) }
    var notifyCreatorOnJoin by rememberSaveable { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by com.example.TaskzApplication.instance.taskzSyncManager.orgHierarchy.collectAsStateWithLifecycle()

    val quickLans = remember(authContext.scope, orgHierarchy.centres) {
        val matched = orgHierarchy.centres.firstOrNull {
            (!authContext.scope.centreId.isNullOrBlank() && it.id == authContext.scope.centreId) ||
                (!authContext.scope.centreCode.isNullOrBlank() && it.code.equals(authContext.scope.centreCode, ignoreCase = true))
        }
        matched?.resolvedLans() ?: (1..11).map { "LAN $it" }
    }

    fun pickDate(current: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { LocalDate.parse(current.trim()) }.getOrElse { LocalDate.now() }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                NothingDotMatrixText(
                    text = "CREATE EXAM GROUP",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TaskzTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorMessage = null
                },
                label = "Exam Name *",
                placeholder = "e.g., Railway RRB / Banking IBPS",
                leadingIcon = Icons.Default.Title,
                testTag = "input_group_title"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = code,
                onValueChange = { code = it },
                label = "Exam / LAN Tag (Optional)",
                placeholder = "e.g., RRB · LAN 1",
                leadingIcon = Icons.Default.Code,
                testTag = "input_group_code"
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickLans.forEach { lan ->
                    val isSelected = code.contains(lan, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            code = toggleLanTagInCode(code, lan)
                        },
                        label = {
                            Text(
                                text = lan,
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
                            selected = isSelected,
                            borderColor = NothingTheme.colors.border,
                            selectedBorderColor = NothingTheme.colors.accentPrimary
                        ),
                        modifier = Modifier.testTag("chip_group_${lan.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = description,
                onValueChange = { description = it },
                label = "Notes (Optional)",
                placeholder = "Exam notes or centre LAN allocation...",
                leadingIcon = Icons.Default.Description,
                singleLine = false,
                testTag = "input_group_description"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskzTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = "Start Date",
                    placeholder = "YYYY-MM-DD",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_group_start_date"
                )
                IconButton(
                    onClick = { pickDate(startDate) { startDate = it } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick Start Date")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskzTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = "End Date",
                    placeholder = "YYYY-MM-DD",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_group_end_date"
                )
                IconButton(
                    onClick = { pickDate(endDate) { endDate = it } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick End Date")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NothingSectionHeader(title = "VISIBILITY & JOIN SETTINGS")

            Spacer(modifier = Modifier.height(10.dp))

            NothingHairlineCard(
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column {
                    Text(
                        text = "Visibility",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("everyone" to "Everyone", "restricted" to "Members Only").forEach { (key, label) ->
                            val selected = visibility == key
                            FilterChip(
                                selected = selected,
                                onClick = { visibility = key },
                                label = {
                                    Text(
                                        text = label,
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
                                    selected = selected,
                                    borderColor = NothingTheme.colors.border,
                                    selectedBorderColor = NothingTheme.colors.accentPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allow 1-Click Self Join",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Switch(
                            checked = allowSelfJoin,
                            onCheckedChange = { allowSelfJoin = it }
                        )
                    }

                    if (allowSelfJoin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Require Approval",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Switch(
                                checked = joinRequiresApproval,
                                onCheckedChange = { joinRequiresApproval = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notify on Join",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Switch(
                            checked = notifyCreatorOnJoin,
                            onCheckedChange = { notifyCreatorOnJoin = it }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = NothingRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Exam name is required."
                        return@Button
                    }
                    viewModel.createGroup(
                        title = title,
                        code = code,
                        description = description,
                        startDate = startDate.ifBlank { null },
                        endDate = endDate.ifBlank { null },
                        visibility = visibility,
                        allowSelfJoin = allowSelfJoin,
                        joinRequiresApproval = joinRequiresApproval,
                        notifyCreatorOnJoin = notifyCreatorOnJoin,
                        maxMembers = null,
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingTheme.colors.accentPrimary,
                    contentColor = NothingTheme.colors.onAccentPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_create_group")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NothingTheme.colors.onAccentPrimary
                    )
                } else {
                    Text("Create Exam Group", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

internal fun toggleLanTagInCode(currentCode: String, lan: String): String {
    val parts = currentCode.split("·", ",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    val existingIdx = parts.indexOfFirst { it.equals(lan, ignoreCase = true) }
    if (existingIdx >= 0) {
        parts.removeAt(existingIdx)
    } else {
        parts.add(lan)
    }
    return parts.joinToString(" · ")
}
