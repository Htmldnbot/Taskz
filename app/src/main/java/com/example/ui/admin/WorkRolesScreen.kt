package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.WorkRole
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingEmptyState
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingStatusDot
import com.example.ui.common.NothingTagChip
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme

@Composable
fun WorkRolesScreen(
    viewModel: WorkRolesViewModel,
    modifier: Modifier = Modifier
) {
    val roles by viewModel.workRolesFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var roleToEdit by remember { mutableStateOf<WorkRole?>(null) }

    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newSortOrder by remember { mutableStateOf("0") }
    var createError by remember { mutableStateOf<String?>(null) }

    var editName by remember { mutableStateOf("") }
    var editCode by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editActive by remember { mutableStateOf(true) }
    var editSortOrder by remember { mutableStateOf("0") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingSectionHeader(
                title = "WORK ROLES (${roles.size})",
                useDotMatrix = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    newName = ""
                    newCode = ""
                    newDesc = ""
                    newSortOrder = "0"
                    createError = null
                    showCreateDialog = true
                },
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("btn_create_work_role")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Role", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading && roles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (roles.isEmpty()) {
            NothingEmptyState(
                title = "No Work Roles",
                subtitle = "Tap Add Role to define operational roles.",
                icon = Icons.Default.Work
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(roles, key = { it.id }) { role ->
                    NothingHairlineCard(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NothingStatusDot(
                                        color = if (role.isActive) NothingGreen else NothingTheme.colors.textSecondary,
                                        size = 7.dp
                                    )
                                    Text(
                                        text = role.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!role.code.isNullOrBlank()) {
                                        NothingTagChip(text = role.code, mono = true)
                                    }
                                }

                                if (!role.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = role.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingTheme.colors.textSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = role.isActive,
                                    onCheckedChange = { viewModel.toggleRoleActive(role) }
                                )

                                IconButton(
                                    onClick = {
                                        roleToEdit = role
                                        editName = role.name
                                        editCode = role.code.orEmpty()
                                        editDesc = role.description.orEmpty()
                                        editActive = role.isActive
                                        editSortOrder = role.sortOrder.toString()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Role",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = NothingTheme.colors.surface,
            title = {
                NothingDotMatrixText(
                    text = "NEW WORK ROLE",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskzTextField(
                        value = newName,
                        onValueChange = { newName = it; createError = null },
                        label = "Role Name *",
                        placeholder = "e.g., Invigilator"
                    )
                    TaskzTextField(
                        value = newCode,
                        onValueChange = { newCode = it },
                        label = "Role Code (Optional)",
                        placeholder = "e.g., INV"
                    )
                    TaskzTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = "Description",
                        placeholder = "Duties and expectations..."
                    )
                    TaskzTextField(
                        value = newSortOrder,
                        onValueChange = { newSortOrder = it.filter { c -> c.isDigit() } },
                        label = "Sort Order"
                    )
                    if (createError != null) {
                        Text(
                            text = createError!!,
                            color = NothingRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isBlank()) {
                            createError = "Role name is required."
                            return@Button
                        }
                        viewModel.createRole(
                            name = newName,
                            code = newCode,
                            description = newDesc,
                            sortOrder = newSortOrder.toIntOrNull() ?: 0
                        )
                        showCreateDialog = false
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Create Role", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (roleToEdit != null) {
        AlertDialog(
            onDismissRequest = { roleToEdit = null },
            containerColor = NothingTheme.colors.surface,
            title = {
                NothingDotMatrixText(
                    text = "EDIT WORK ROLE",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskzTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = "Role Name *"
                    )
                    TaskzTextField(
                        value = editCode,
                        onValueChange = { editCode = it },
                        label = "Role Code"
                    )
                    TaskzTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = "Description"
                    )
                    TaskzTextField(
                        value = editSortOrder,
                        onValueChange = { editSortOrder = it.filter { c -> c.isDigit() } },
                        label = "Sort Order"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Status", fontWeight = FontWeight.SemiBold)
                        Switch(checked = editActive, onCheckedChange = { editActive = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateRole(
                                id = roleToEdit!!.id,
                                name = editName,
                                code = editCode,
                                description = editDesc,
                                isActive = editActive,
                                sortOrder = editSortOrder.toIntOrNull() ?: 0
                            )
                            roleToEdit = null
                        }
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { roleToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
