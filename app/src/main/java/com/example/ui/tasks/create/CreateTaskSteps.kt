package com.example.ui.tasks.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.core.model.AlarmType
import com.example.core.model.CompletionPolicy
import com.example.core.model.FormFieldType
import com.example.core.model.ResponseFormField
import com.example.core.model.ResponseType
import com.example.core.model.TaskGroup
import com.example.core.model.TaskImportance
import com.example.core.model.TaskType
import com.example.core.model.TimeUrgency
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import com.example.ui.theme.TaskzWarning

// -------------------------------------------------------------
// STEP 1: BASIC INFORMATION
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepBasicInfo(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    selectedGroup: TaskGroup?,
    onGroupSelected: (TaskGroup?) -> Unit,
    availableGroups: List<TaskGroup>,
    modifier: Modifier = Modifier
) {
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 1: Basic Information",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter the primary task details and target operations location.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Task Title *") },
            placeholder = { Text("e.g. Download examination drive / Stop candidate entry") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_task_title"),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Operational Instructions") },
            placeholder = { Text("Detailed standard operating procedure (SOP)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        OutlinedTextField(
            value = location,
            onValueChange = onLocationChange,
            label = { Text("Examination Centre / Lab / Room") },
            placeholder = { Text("e.g. Server Room A / Block 3 Lab 2") },
            modifier = Modifier.fillMaxWidth()
        )

        // Group Selector
        if (availableGroups.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = groupDropdownExpanded,
                onExpandedChange = { groupDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedGroup?.title ?: "No Group (Standalone)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Operations Group") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = groupDropdownExpanded,
                    onDismissRequest = { groupDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No Group (Standalone)") },
                        onClick = {
                            onGroupSelected(null)
                            groupDropdownExpanded = false
                        }
                    )
                    availableGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.title) },
                            onClick = {
                                onGroupSelected(group)
                                groupDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 2: TASK TYPE
// -------------------------------------------------------------
@Composable
fun StepTaskType(
    selectedType: TaskType,
    onTypeSelected: (TaskType) -> Unit,
    targetPercentage: Int,
    onTargetPercentageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Step 2: What type of task is this?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select the execution archetype for this examination operation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskType.entries.forEach { type ->
            val isSelected = selectedType == type
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTypeSelected(type) }
                    .testTag("task_type_${type.name}")
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (type) {
                                TaskType.SIMPLE -> Icons.Default.TaskAlt
                                TaskType.TIME_SPECIFIC -> Icons.Default.Schedule
                                TaskType.DEADLINE -> Icons.Default.HourglassTop
                                TaskType.IMMEDIATE -> Icons.Default.FlashOn
                                TaskType.PROGRESS -> Icons.Default.TrendingUp
                                TaskType.MULTI_CONFIRMATION -> Icons.Default.Groups
                                TaskType.RECURRING -> Icons.Default.Repeat
                            },
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = type.label, fontWeight = FontWeight.Bold)
                        Text(text = type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Conditional field for PROGRESS task
        if (selectedType == TaskType.PROGRESS) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = targetPercentage.toString(),
                onValueChange = { onTargetPercentageChange(it.toIntOrNull() ?: 100) },
                label = { Text("Target Goal Percentage (%)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 3: TIMING & URGENCY
// -------------------------------------------------------------
@Composable
fun StepTiming(
    taskType: TaskType,
    urgencyType: TimeUrgency,
    onUrgencySelected: (TimeUrgency) -> Unit,
    executionTime: String,
    onExecutionTimeChange: (String) -> Unit,
    deadlineTime: String,
    onDeadlineTimeChange: (String) -> Unit,
    repeatIntervalMinutes: Int?,
    onRepeatIntervalChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 3: When does this task need action?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (taskType == TaskType.IMMEDIATE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TaskzError.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = TaskzError)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Immediate / Urgent Task: No future scheduling needed. Alarms and notifications trigger immediately upon creation.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = TaskzError
                    )
                }
            }
        } else {
            TimeUrgency.entries.forEach { urgency ->
                val isSelected = urgencyType == urgency
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUrgencySelected(urgency) }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = urgency.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            OutlinedTextField(
                value = executionTime,
                onValueChange = onExecutionTimeChange,
                label = { Text("Scheduled Execution Time (e.g. 06:00 AM or ISO)") },
                placeholder = { Text("2026-10-17T06:00:00Z") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = deadlineTime,
                onValueChange = onDeadlineTimeChange,
                label = { Text("Deadline / Cutoff Time (Optional)") },
                placeholder = { Text("2026-10-17T07:00:00Z") },
                modifier = Modifier.fillMaxWidth()
            )

            if (taskType == TaskType.RECURRING || urgencyType == TimeUrgency.RECURRING_INTERVAL) {
                OutlinedTextField(
                    value = (repeatIntervalMinutes ?: 15).toString(),
                    onValueChange = { onRepeatIntervalChange(it.toIntOrNull()) },
                    label = { Text("Recurrence Interval (Minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 4: TASK IMPORTANCE
// -------------------------------------------------------------
@Composable
fun StepImportance(
    selectedImportance: TaskImportance,
    onImportanceSelected: (TaskImportance) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 4: How important is this task?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Importance determines visual prominence, escalation rules, and default alarm intensity. It is independent of urgency.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TaskImportance.entries.forEach { imp ->
            val isSelected = selectedImportance == imp
            val color = when (imp) {
                TaskImportance.CRITICAL -> TaskzError
                TaskImportance.HIGH -> TaskzWarning
                TaskImportance.NORMAL -> MaterialTheme.colorScheme.primary
                TaskImportance.LOW -> MaterialTheme.colorScheme.secondary
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onImportanceSelected(imp) }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(text = imp.label, fontWeight = FontWeight.Bold, color = color)
                        Text(
                            text = when (imp) {
                                TaskImportance.CRITICAL -> "Full-screen lock-screen alarms, loud persistent alerts, emergency escalation"
                                TaskImportance.HIGH -> "High priority notifications with sound, advance warnings, prominent tags"
                                TaskImportance.NORMAL -> "Standard notifications and reminders for operational shifts"
                                TaskImportance.LOW -> "Silent / passive reminder for non-critical checklist actions"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 5: DYNAMIC RESPONSE FORM BUILDER
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepResponseForm(
    responseType: ResponseType,
    onResponseTypeChange: (ResponseType) -> Unit,
    customFields: List<ResponseFormField>,
    onAddField: (ResponseFormField) -> Unit,
    onRemoveField: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newFieldLabel by remember { mutableStateOf("") }
    var newFieldType by remember { mutableStateOf(FormFieldType.NUMBER) }
    var newFieldRequired by remember { mutableStateOf(true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 5: Response Form Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Define the structured response inputs required from assigned employees upon completion.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Primary Response Archetype
        ResponseType.entries.forEach { rt ->
            FilterChip(
                selected = responseType == rt,
                onClick = { onResponseTypeChange(rt) },
                label = { Text(rt.label) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Custom Dynamic Form Fields (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        customFields.forEach { field ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = field.label, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${field.fieldType.label} • ${if (field.isRequired) "Required" else "Optional"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemoveField(field.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TaskzError)
                    }
                }
            }
        }

        // Add custom field box
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Form Field", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = newFieldLabel,
                    onValueChange = { newFieldLabel = it },
                    label = { Text("Field Label") },
                    placeholder = { Text("e.g. Registered Candidates / Biometric Failed") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Field Type:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    listOf(FormFieldType.NUMBER, FormFieldType.TEXT, FormFieldType.YES_NO).forEach { type ->
                        FilterChip(
                            selected = newFieldType == type,
                            onClick = { newFieldType = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Required Field")
                    Switch(checked = newFieldRequired, onCheckedChange = { newFieldRequired = it })
                }

                Button(
                    onClick = {
                        if (newFieldLabel.isNotBlank()) {
                            onAddField(
                                ResponseFormField(
                                    label = newFieldLabel.trim(),
                                    fieldType = newFieldType,
                                    isRequired = newFieldRequired
                                )
                            )
                            newFieldLabel = ""
                        }
                    },
                    enabled = newFieldLabel.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Field to Form")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 6: ASSIGNMENT, LAN SCOPE & COMPLETION POLICY
// -------------------------------------------------------------
@Composable
fun StepAssignment(
    lanListText: String,
    onLanListChange: (String) -> Unit,
    completionPolicy: CompletionPolicy,
    onCompletionPolicyChange: (CompletionPolicy) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 6: Assignment & Completion Policy",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure LANs and completion rules. For multi-LAN operations, each LAN tracks progress independently.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = lanListText,
            onValueChange = onLanListChange,
            label = { Text("LAN Numbers / Scope (Comma-separated)") },
            placeholder = { Text("LAN1, LAN2, LAN3, LAN4, LAN5") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            text = "Task Completion Policy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        CompletionPolicy.entries.forEach { policy ->
            val isSelected = completionPolicy == policy
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCompletionPolicyChange(policy) }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Lan,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = policy.label, fontWeight = FontWeight.Bold)
                        Text(text = policy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 7: ALARM CONFIGURATION & REMINDERS
// -------------------------------------------------------------
@Composable
fun StepAlarmConfig(
    alarmType: AlarmType,
    onAlarmTypeChange: (AlarmType) -> Unit,
    advanceReminders: List<Int>,
    onToggleReminder: (Int) -> Unit,
    remindUntilCompleted: Boolean,
    onRemindUntilCompletedChange: (Boolean) -> Unit,
    overdueIntervalMinutes: Int,
    onOverdueIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 7: Alarm & Reminder Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        AlarmType.entries.forEach { type ->
            val isSelected = alarmType == type
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAlarmTypeChange(type) }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (type) {
                            AlarmType.FULL_SCREEN_ALARM -> Icons.Default.Alarm
                            AlarmType.SOUND_ALERT -> Icons.Default.NotificationsActive
                            AlarmType.NORMAL_NOTIFICATION -> Icons.Default.Schedule
                            AlarmType.SILENT -> Icons.Default.Check
                        },
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = type.label, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Advance Reminders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 5, 10, 15, 30).forEach { mins ->
                val active = advanceReminders.contains(mins)
                FilterChip(
                    selected = active,
                    onClick = { onToggleReminder(mins) },
                    label = { Text(if (mins == 0) "At time" else "${mins}m") }
                )
            }
        }

        // Remind until completed
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repeat Reminder if Overdue", fontWeight = FontWeight.Bold)
                        Text("Triggers follow-up alarms until task completion response is registered", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = remindUntilCompleted, onCheckedChange = onRemindUntilCompletedChange)
                }

                if (remindUntilCompleted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = overdueIntervalMinutes.toString(),
                        onValueChange = { onOverdueIntervalChange(it.toIntOrNull() ?: 5) },
                        label = { Text("Repeat interval (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 8: VISIBILITY & JOIN SETTINGS
// -------------------------------------------------------------
@Composable
fun StepVisibility(
    visibility: String,
    onVisibilityChange: (String) -> Unit,
    allowSelfJoin: Boolean,
    onAllowSelfJoinChange: (Boolean) -> Unit,
    joinRequiresApproval: Boolean,
    onJoinRequiresApprovalChange: (Boolean) -> Unit,
    requiresCreatorConfirmation: Boolean,
    onRequiresCreatorConfirmationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 8: Visibility & Join Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow Self Join", fontWeight = FontWeight.Bold)
                Text("Employees in group can request or self-assign to this task", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = allowSelfJoin, onCheckedChange = onAllowSelfJoinChange)
        }

        if (allowSelfJoin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Join Requires Approval", fontWeight = FontWeight.Bold)
                    Text("Manager must approve before employee is assigned", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = joinRequiresApproval, onCheckedChange = onJoinRequiresApprovalChange)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Require Creator Confirmation to Complete", fontWeight = FontWeight.Bold)
                Text("Moves task to PENDING_CONFIRMATION on completion submission", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = requiresCreatorConfirmation, onCheckedChange = onRequiresCreatorConfirmationChange)
        }
    }
}

// -------------------------------------------------------------
// STEP 9: FINAL REVIEW & VALIDATION (PART 23)
// -------------------------------------------------------------
@Composable
fun StepReviewCreate(
    title: String,
    taskType: TaskType,
    importance: TaskImportance,
    executionTime: String,
    deadlineTime: String,
    responseType: ResponseType,
    lanList: List<String>,
    completionPolicy: CompletionPolicy,
    alarmType: AlarmType,
    advanceReminders: List<Int>,
    validationErrors: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 9: Review & Create",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (validationErrors.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TaskzError.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TaskzError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Please resolve validation errors:", fontWeight = FontWeight.Bold, color = TaskzError)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    validationErrors.forEach { err ->
                        Text("• $err", style = MaterialTheme.typography.bodySmall, color = TaskzError)
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewRow("Task Title", title)
                ReviewRow("Task Type", taskType.label)
                ReviewRow("Importance", importance.label)
                ReviewRow("Execution Time", if (taskType == TaskType.IMMEDIATE) "Immediate" else executionTime.ifBlank { "Not set" })
                ReviewRow("Deadline", deadlineTime.ifBlank { "None" })
                ReviewRow("Response Type", responseType.label)
                ReviewRow("LAN Scope", if (lanList.isEmpty()) "Single Assignee / All" else lanList.joinToString(", "))
                ReviewRow("Completion Policy", completionPolicy.label)
                ReviewRow("Alarm Intensity", alarmType.label)
                ReviewRow("Advance Reminders", advanceReminders.joinToString(", ") { if (it == 0) "At task time" else "${it}m before" })
            }
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
