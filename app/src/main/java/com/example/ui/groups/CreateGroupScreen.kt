package com.example.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.TaskGroup
import com.example.ui.common.TaskzTextField

@Composable
fun CreateGroupScreen(
    viewModel: GroupsViewModel,
    onSuccess: (TaskGroup) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }

    var visibility by rememberSaveable { mutableStateOf("everyone") } // "everyone" or "restricted"
    var allowSelfJoin by rememberSaveable { mutableStateOf(true) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(false) }
    var notifyCreatorOnJoin by rememberSaveable { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
                    text = "Create Group / Event",
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
                label = "Group Title *",
                placeholder = "e.g., Annual Board Examination 2026",
                leadingIcon = Icons.Default.Title,
                testTag = "input_group_title"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = code,
                onValueChange = { code = it },
                label = "Group Code (Optional)",
                placeholder = "e.g., EXAM-2026",
                leadingIcon = Icons.Default.Code,
                testTag = "input_group_code"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (Optional)",
                placeholder = "Event or department scope details...",
                leadingIcon = Icons.Default.Description,
                singleLine = false,
                testTag = "input_group_description"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskzTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = "Start Date",
                    placeholder = "YYYY-MM-DD",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_group_start_date"
                )
                TaskzTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = "End Date",
                    placeholder = "YYYY-MM-DD",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_group_end_date"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Visibility & Permissions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Who can see this group?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = visibility == "everyone",
                            onClick = { visibility = "everyone" },
                            label = { Text("Everyone") }
                        )
                        FilterChip(
                            selected = visibility == "restricted",
                            onClick = { visibility = "restricted" },
                            label = { Text("Selected Employees Only") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Can employees join themselves?",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Allow active employees to join this group",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    text = "Require approval before joining?",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Creator must approve join requests",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = "Notify creator when somebody joins?",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Send alert on join or request",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Group title is required."
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_create_group")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Create Group / Event")
                }
            }
        }
    }
}
