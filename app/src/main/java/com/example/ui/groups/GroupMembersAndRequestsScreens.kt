package com.example.ui.groups

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.GroupJoinRequest
import com.example.core.model.GroupMember
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.core.model.ScopedMemberSearchResult
import com.example.ui.theme.TaskzWarning
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GroupMembersScreen(
    groupId: String,
    viewModel: GroupsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val members by viewModel.groupMembers.collectAsStateWithLifecycle()
    val directoryMembers by viewModel.scopedDirectoryMembers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canManage = viewModel.canManageGroups()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        viewModel.loadGroupDetails(groupId)
        viewModel.loadScopedDirectoryMembers("")
    }

    LaunchedEffect(Unit) {
        viewModel.actionEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Group Members",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${members.size} active members",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canManage) {
                    Button(
                        onClick = {
                            viewModel.loadScopedDirectoryMembers("")
                            showAddMemberDialog = true
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_add_group_members")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Members", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (members.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "No members yet")
                        if (canManage) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.loadScopedDirectoryMembers("")
                                    showAddMemberDialog = true
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Add Members")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(members, key = { it.id }) { member ->
                        MemberItem(member = member)
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        val existingIds = remember(members) { members.map { it.userId }.toSet() }
        val candidates = remember(directoryMembers, existingIds) {
            directoryMembers.filter { it.id !in existingIds }
        }
        var search by remember { mutableStateOf("") }
        var selectedIds by remember { mutableStateOf(setOf<String>()) }
        val visibleCandidates = remember(candidates, search) {
            if (search.isBlank()) candidates else candidates.filter {
                it.fullName.contains(search, ignoreCase = true) ||
                    it.employeeId.contains(search, ignoreCase = true) ||
                    it.activeRoleCode.contains(search, ignoreCase = true) ||
                    (it.centreCode?.contains(search, ignoreCase = true) == true)
            }
        }
        val eligibleVisibleIds = remember(visibleCandidates) {
            visibleCandidates.filter { !it.isOnLeave }.map { it.id }.toSet()
        }

        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Members", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.loadScopedDirectoryMembers(it)
                        },
                        placeholder = { Text("Search name, ID, centre...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search members") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_group_member_search")
                    )
                    if (eligibleVisibleIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds = if (eligibleVisibleIds.all { it in selectedIds }) {
                                    selectedIds - eligibleVisibleIds
                                } else {
                                    selectedIds + eligibleVisibleIds
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_select_all_group_members")
                        ) {
                            Text("Select / Deselect All (${eligibleVisibleIds.size})")
                        }
                    }
                    if (visibleCandidates.isEmpty()) {
                        Text(
                            text = "No members found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(visibleCandidates, key = { it.id }) { candidate ->
                                val isChecked = candidate.id in selectedIds
                                val isUnavailable = candidate.isOnLeave
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable(enabled = !isUnavailable) {
                                            selectedIds = if (isChecked) selectedIds - candidate.id else selectedIds + candidate.id
                                        }
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        enabled = !isUnavailable,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + candidate.id else selectedIds - candidate.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = candidate.fullName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (isUnavailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isUnavailable) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = TaskzWarning.copy(alpha = 0.18f),
                                                    modifier = Modifier.testTag("badge_on_leave_${candidate.employeeId}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.EventBusy,
                                                            contentDescription = "On Leave",
                                                            tint = TaskzWarning,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "On Leave",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = TaskzWarning
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${candidate.employeeId} • ${com.example.core.model.TaskzRole.fromCode(candidate.activeRoleCode).displayName}" +
                                                (candidate.centreCode?.let { " • $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = candidates.filter { it.id in selectedIds }
                        viewModel.addGroupMembersAndSyncTasks(groupId, chosen) {
                            showAddMemberDialog = false
                        }
                    },
                    enabled = selectedIds.isNotEmpty() && !isLoading,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_add_group_members")
                ) {
                    Text("Add (${selectedIds.size})")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddMemberDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("dialog_add_group_members")
        )
    }
}

@Composable
private fun MemberItem(member: GroupMember) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.fullName?.take(2)?.uppercase() ?: "EM",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.fullName ?: "Employee",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = member.employeeId ?: "EMP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (member.groupRole == "owner") MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = member.groupRole.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (member.groupRole == "owner") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun GroupJoinRequestsScreen(
    groupId: String,
    viewModel: GroupsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requests by viewModel.groupJoinRequests.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.loadGroupDetails(groupId)
    }

    BackHandler(onBack = onBackClick)

    Scaffold { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                Column {
                    Text(
                        text = "Join Requests",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${requests.size} pending requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No pending join requests for this group")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(requests, key = { it.id }) { req ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = req.fullName?.take(2)?.uppercase() ?: "AP",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = req.fullName ?: "Applicant",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = req.employeeId ?: "EMP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.reviewGroupJoinRequest(req.id, false, groupId) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TaskzError)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reject")
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = { viewModel.reviewGroupJoinRequest(req.id, true, groupId) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TaskzSuccess)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
