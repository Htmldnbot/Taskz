package com.example.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Shift
import com.example.core.model.TaskGroup
import com.example.core.model.TaskItem
import com.example.core.model.UserProfile
import com.example.core.model.WorkRole
import com.example.ui.common.TaskzTextField
import com.example.ui.groups.GroupsViewModel
import com.example.ui.shifts.ShiftsViewModel

data class ManpowerRow(
    val roleId: String,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    preselectedGroupId: String? = null,
    preselectedShiftId: String? = null,
    tasksViewModel: TasksViewModel,
    groupsViewModel: GroupsViewModel,
    shiftsViewModel: ShiftsViewModel,
    onSuccess: (TaskItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by groupsViewModel.groupsFlow.collectAsStateWithLifecycle()
    val activeRoles by tasksViewModel.activeWorkRolesFlow.collectAsStateWithLifecycle()
    val directoryEmployees by tasksViewModel.directoryEmployees.collectAsStateWithLifecycle()
    val isLoading by tasksViewModel.isLoading.collectAsStateWithLifecycle()

    var selectedGroupId by rememberSaveable { mutableStateOf(preselectedGroupId ?: groups.firstOrNull()?.id ?: "") }
    var selectedShiftId by rememberSaveable { mutableStateOf(preselectedShiftId) }

    // Update selectedGroupId if groups become available
    if (selectedGroupId.isEmpty() && groups.isNotEmpty()) {
        selectedGroupId = groups.first().id
    }

    val shiftsForGroup by shiftsViewModel.getShiftsForGroup(selectedGroupId).collectAsStateWithLifecycle()

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var startsAt by rememberSaveable { mutableStateOf("2026-10-16T09:00") }
    var dueAt by rememberSaveable { mutableStateOf("2026-10-16T17:00") }
    var priority by rememberSaveable { mutableStateOf("normal") } // low, normal, high, urgent

    // Visibility
    var visibilityOption by rememberSaveable { mutableStateOf("everyone") } // "everyone" or "restricted"
    val selectedEmployeeIds = remember { mutableStateListOf<String>() }
    var employeeSearchQuery by remember { mutableStateOf("") }

    // Join Rules
    var allowSelfJoin by rememberSaveable { mutableStateOf(true) }
    var joinRequiresApproval by rememberSaveable { mutableStateOf(true) }
    var notifyCreatorOnJoin by rememberSaveable { mutableStateOf(true) }
    var requiresCompletionConfirmation by rememberSaveable { mutableStateOf(false) }
    var maxParticipantsStr by rememberSaveable { mutableStateOf("") }

    // Manpower Requirements (Role | Count)
    val manpowerRequirements = remember {
        mutableStateListOf<ManpowerRow>().apply {
            if (activeRoles.isNotEmpty()) {
                add(ManpowerRow(roleId = activeRoles.first().id, count = 2))
            }
        }
    }

    // Auto add initial manpower row if empty when roles load
    if (manpowerRequirements.isEmpty() && activeRoles.isNotEmpty()) {
        manpowerRequirements.add(ManpowerRow(roleId = activeRoles.first().id, count = 1))
    }

    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var shiftDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    text = "Create Task",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group Picker
            Text("Assign to Group / Event *", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = groupDropdownExpanded,
                onExpandedChange = { groupDropdownExpanded = !groupDropdownExpanded }
            ) {
                val selectedGroupTitle = groups.find { it.id == selectedGroupId }?.title ?: "Select Group"
                OutlinedTextField(
                    value = selectedGroupTitle,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = groupDropdownExpanded,
                    onDismissRequest = { groupDropdownExpanded = false }
                ) {
                    groups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.title) },
                            onClick = {
                                selectedGroupId = g.id
                                selectedShiftId = null
                                groupDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shift Picker (Optional)
            Text("Assign to Shift (Optional)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = shiftDropdownExpanded,
                onExpandedChange = { shiftDropdownExpanded = !shiftDropdownExpanded }
            ) {
                val selectedShiftName = shiftsForGroup.find { it.id == selectedShiftId }?.name ?: "No Shift (General Group Task)"
                OutlinedTextField(
                    value = selectedShiftName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = shiftDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = shiftDropdownExpanded,
                    onDismissRequest = { shiftDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None (General Task)") },
                        onClick = {
                            selectedShiftId = null
                            shiftDropdownExpanded = false
                        }
                    )
                    shiftsForGroup.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s.name} (${s.startsAt} - ${s.endsAt})") },
                            onClick = {
                                selectedShiftId = s.id
                                shiftDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TaskzTextField(
                value = title,
                onValueChange = { title = it; errorMessage = null },
                label = "Task Title *",
                placeholder = "e.g., Main Hall Examination Invigilation",
                leadingIcon = Icons.Default.Title,
                testTag = "input_task_title"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                placeholder = "Scope of work, briefing notes...",
                leadingIcon = Icons.Default.Description,
                singleLine = false,
                testTag = "input_task_description"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = location,
                onValueChange = { location = it },
                label = "Location",
                placeholder = "e.g., Room 302, Building B",
                leadingIcon = Icons.Default.Place,
                testTag = "input_task_location"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskzTextField(
                    value = startsAt,
                    onValueChange = { startsAt = it },
                    label = "Starts At",
                    placeholder = "YYYY-MM-DDTHH:MM",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_task_start"
                )
                TaskzTextField(
                    value = dueAt,
                    onValueChange = { dueAt = it },
                    label = "Due At",
                    placeholder = "YYYY-MM-DDTHH:MM",
                    leadingIcon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    testTag = "input_task_due"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Priority Selection (Low, Normal, High, Urgent; default Normal)
            Text("Priority", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low", "normal", "high", "urgent").forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("priority_$p")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Visibility Section
            Text("Who can see this task?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = visibilityOption == "everyone",
                            onClick = { visibilityOption = "everyone" },
                            label = { Text("Everyone in this Group") }
                        )
                        FilterChip(
                            selected = visibilityOption == "restricted",
                            onClick = { visibilityOption = "restricted" },
                            label = { Text("Selected Employees Only") }
                        )
                    }

                    if (visibilityOption == "restricted") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Search and select employees:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = employeeSearchQuery,
                            onValueChange = {
                                employeeSearchQuery = it
                                tasksViewModel.searchEmployees(it)
                            },
                            placeholder = { Text("Search by Name or Employee ID...") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            directoryEmployees.take(5).forEach { emp ->
                                val isSelected = selectedEmployeeIds.contains(emp.id)
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isSelected) selectedEmployeeIds.remove(emp.id)
                                            else selectedEmployeeIds.add(emp.id)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${emp.fullName} (${emp.employeeId})",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Task Join Rules Section
            Text("Task Joining Rules", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Can employees join themselves?", fontWeight = FontWeight.SemiBold)
                            Text("Allow self-joining to this task", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = allowSelfJoin, onCheckedChange = { allowSelfJoin = it })
                    }

                    if (allowSelfJoin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Require creator approval?", fontWeight = FontWeight.SemiBold)
                                Text("Task creator must approve applicant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = joinRequiresApproval, onCheckedChange = { joinRequiresApproval = it })
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notify creator on join / request?", fontWeight = FontWeight.SemiBold)
                            Text("Send notification when someone joins or requests", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notifyCreatorOnJoin, onCheckedChange = { notifyCreatorOnJoin = it })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require confirmation before completion?", fontWeight = FontWeight.SemiBold)
                            Text("Creator must verify and confirm task completion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = requiresCompletionConfirmation, onCheckedChange = { requiresCompletionConfirmation = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Manpower Requirements Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Manpower Requirements", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Specify required headcounts per role", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        val firstRole = activeRoles.firstOrNull()
                        if (firstRole != null) {
                            manpowerRequirements.add(ManpowerRow(roleId = firstRole.id, count = 1))
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Role")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (manpowerRequirements.isEmpty()) {
                        Text(
                            text = "No roles configured. Click 'Add Role' above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        manpowerRequirements.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Role Selector
                                var roleExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = roleExpanded,
                                    onExpandedChange = { roleExpanded = !roleExpanded },
                                    modifier = Modifier.weight(2f)
                                ) {
                                    val currentRole = activeRoles.find { it.id == row.roleId }?.name ?: "Role"
                                    OutlinedTextField(
                                        value = currentRole,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                                        modifier = Modifier.menuAnchor(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = roleExpanded,
                                        onDismissRequest = { roleExpanded = false }
                                    ) {
                                        activeRoles.forEach { r ->
                                            DropdownMenuItem(
                                                text = { Text(r.name) },
                                                onClick = {
                                                    manpowerRequirements[index] = row.copy(roleId = r.id)
                                                    roleExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Count input
                                OutlinedTextField(
                                    value = row.count.toString(),
                                    onValueChange = { newVal ->
                                        val parsed = newVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        manpowerRequirements[index] = row.copy(count = maxOf(1, parsed))
                                    },
                                    label = { Text("Count") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { manpowerRequirements.removeAt(index) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Role",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Task title is required."
                        return@Button
                    }
                    if (selectedGroupId.isBlank()) {
                        errorMessage = "Please select a group for this task."
                        return@Button
                    }

                    tasksViewModel.createTask(
                        groupId = selectedGroupId,
                        shiftId = selectedShiftId,
                        title = title,
                        description = description,
                        location = location,
                        startsAt = startsAt,
                        dueAt = dueAt,
                        priority = priority,
                        visibility = visibilityOption,
                        selectedEmployeeIds = selectedEmployeeIds.toList(),
                        allowSelfJoin = allowSelfJoin,
                        joinRequiresApproval = joinRequiresApproval,
                        notifyCreatorOnJoin = notifyCreatorOnJoin,
                        requiresCompletionConfirmation = requiresCompletionConfirmation,
                        maxParticipants = maxParticipantsStr.toIntOrNull(),
                        requirements = manpowerRequirements.map { it.roleId to it.count },
                        onSuccess = onSuccess
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_create_task")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Create Task")
                }
            }
        }
    }
}
