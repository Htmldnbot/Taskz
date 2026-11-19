package com.example.ui.groups

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskGroup
import com.example.ui.common.TaskzTextField
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditGroupScreen(
    group: TaskGroup,
    viewModel: GroupsViewModel,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(group.title) }
    var code by rememberSaveable { mutableStateOf(group.code.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(group.description.orEmpty()) }
    var startDate by rememberSaveable { mutableStateOf(group.startDate.orEmpty()) }
    var endDate by rememberSaveable { mutableStateOf(group.endDate.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(group.status) }

    var visibility by rememberSaveable { mutableStateOf(group.visibility) }
    var allowSelfJoin by rememberSaveable { mutableStateOf(group.allowSelfJoin) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(group.joinRequiresApproval) }
    var notifyCreatorOnJoin by rememberSaveable { mutableStateOf(group.notifyCreatorOnJoin) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val quickLans = remember { listOf("LAN 1", "LAN 2", "LAN 3", "LAN 4") }

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

    Scaffold { innerPadding ->
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
                Text(
                    text = "Edit Exam Group",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                placeholder = "Exam name",
                leadingIcon = Icons.Default.Title,
                testTag = "edit_group_title"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = code,
                onValueChange = { code = it },
                label = "Exam / LAN Tag",
                placeholder = "e.g., RRB · LAN 1",
                leadingIcon = Icons.Default.Code,
                testTag = "edit_group_code"
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
                        onClick = { code = toggleLanTagInCode(code, lan) },
                        label = { Text(lan) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                placeholder = "Description...",
                leadingIcon = Icons.Default.Description,
                singleLine = false,
                testTag = "edit_group_desc"
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
                    modifier = Modifier.weight(1f)
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { pickDate(endDate) { endDate = it } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = "Pick End Date")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Status",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active", "completed", "cancelled").forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { status = s },
                        label = { Text(s.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow Self Join", fontWeight = FontWeight.SemiBold)
                        Switch(checked = allowSelfJoin, onCheckedChange = { allowSelfJoin = it })
                    }
                    if (allowSelfJoin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Require Approval", fontWeight = FontWeight.SemiBold)
                            Switch(checked = joinRequiresApproval, onCheckedChange = { joinRequiresApproval = it })
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notify On Join", fontWeight = FontWeight.SemiBold)
                        Switch(checked = notifyCreatorOnJoin, onCheckedChange = { notifyCreatorOnJoin = it })
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Group title is required."
                        return@Button
                    }
                    viewModel.updateGroup(
                        groupId = group.id,
                        title = title,
                        code = code,
                        description = description,
                        startDate = startDate.ifBlank { null },
                        endDate = endDate.ifBlank { null },
                        status = status,
                        visibility = visibility,
                        allowSelfJoin = allowSelfJoin,
                        joinRequiresApproval = joinRequiresApproval,
                        notifyCreatorOnJoin = notifyCreatorOnJoin,
                        maxMembers = group.maxMembers,
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_edit_group")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
