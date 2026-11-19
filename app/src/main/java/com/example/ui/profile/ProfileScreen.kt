package com.example.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.common.TaskzOutlinedButton
import com.example.ui.common.TaskzPrimaryButton
import com.example.ui.common.TaskzTextField
import com.example.ui.theme.TaskzError
import com.example.ui.theme.TaskzSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogoutSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.profileFlow.collectAsStateWithLifecycle()
    val authContext by viewModel.authContextFlow.collectAsStateWithLifecycle()
    val orgHierarchy by viewModel.orgHierarchyFlow.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val isLoggingOut by viewModel.isLoggingOut.collectAsStateWithLifecycle()
    val isSwitchingRole by viewModel.isSwitchingRole.collectAsStateWithLifecycle()
    val isSavingLocation by viewModel.isSavingLocation.collectAsStateWithLifecycle()
    val locationSaveError by viewModel.locationSaveError.collectAsStateWithLifecycle()
    val isTogglingLeave by viewModel.isTogglingLeave.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    var showLocationDialog by remember { mutableStateOf(false) }

    var fullName by rememberSaveable(profile) { mutableStateOf(profile?.fullName.orEmpty()) }
    var mobile by rememberSaveable(profile) { mutableStateOf(profile?.mobile.orEmpty()) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Profile Avatar with edit button
        Box(contentAlignment = Alignment.BottomEnd) {
            if (selectedPhotoUri != null) {
                AsyncImage(
                    model = selectedPhotoUri,
                    contentDescription = "New Profile Photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (!profile?.avatarSignedUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile!!.avatarSignedUrl,
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile?.fullName?.take(2)?.uppercase() ?: "TZ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("change_photo_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (profile?.isActive == true) TaskzSuccess.copy(alpha = 0.15f) else TaskzError.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (profile?.isActive == true) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (profile?.isActive == true) TaskzSuccess else TaskzError
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = authContext.activeRole.displayName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (authContext.isOnLeave) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = com.example.ui.theme.TaskzWarning.copy(alpha = 0.18f),
                    modifier = Modifier.testTag("profile_on_leave_badge")
                ) {
                    Text(
                        text = "On Leave",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.TaskzWarning
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // On Leave Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (authContext.isOnLeave) {
                    com.example.ui.theme.TaskzWarning.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_on_leave_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        tint = if (authContext.isOnLeave) com.example.ui.theme.TaskzWarning else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (authContext.isOnLeave) "On Leave" else "On Duty",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (authContext.isOnLeave) "No new tasks or alarms assigned" else "Available for task assignment",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = authContext.isOnLeave,
                    enabled = !isTogglingLeave,
                    onCheckedChange = { viewModel.toggleLeaveStatus(it) },
                    modifier = Modifier.testTag("profile_on_leave_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Assigned Roles & Active Role Selector Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_roles_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assigned Roles & Active Role",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    if (isSwitchingRole) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                    Spacer(modifier = Modifier.height(8.dp))
                val rolesList = authContext.assignedRoles.ifEmpty {
                    listOf(
                        com.example.core.model.AssignedRoleInfo(
                            roleId = "",
                            roleCode = authContext.activeRole.code,
                            roleName = authContext.activeRole.displayName,
                            isPrimary = true
                        )
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rolesList.forEach { role ->
                        val isActive = role.roleCode.equals(authContext.activeRole.code, ignoreCase = true)
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                if (!isActive && !isSwitchingRole) {
                                    viewModel.switchActiveRole(role.roleCode)
                                }
                            },
                            enabled = !isSwitchingRole,
                            label = {
                                Text(com.example.core.model.TaskzRole.fromCode(role.roleCode).displayName)
                            },
                            leadingIcon = if (isActive) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active role",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("profile_role_chip_${role.roleCode}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Organizational Scope & Device Registration Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_scope_device_card")
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationCity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scope & Device",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "${authContext.scope.summaryLabel()} (${authContext.activeRole.defaultScope.label})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                val scopeParts = listOfNotNull(
                    authContext.scope.zoneName?.let { "Zone: $it" },
                    authContext.scope.stateName?.let { "State: $it" },
                    authContext.scope.cityName?.let { "City: $it" },
                    authContext.scope.centreCode?.let { "Centre: $it" },
                    authContext.scope.assignedLan?.takeIf { it.isNotBlank() }?.let { "LAN: $it" }
                )
                if (scopeParts.isNotEmpty()) {
                    Text(
                        text = scopeParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = TaskzSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Device: ${deviceId?.take(14) ?: "..."}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val scopeBtnLabel = when (authContext.activeRole) {
                    com.example.core.model.TaskzRole.ZOM -> "Update Zone"
                    com.example.core.model.TaskzRole.ROM -> "Update State"
                    com.example.core.model.TaskzRole.CITY_HEAD -> "Update State & City"
                    com.example.core.model.TaskzRole.VCO -> "Update Centre"
                    com.example.core.model.TaskzRole.IT_MANAGER -> "Update Centre & LAN"
                    com.example.core.model.TaskzRole.COMMAND_CENTER,
                    com.example.core.model.TaskzRole.SUPER_ADMIN -> null
                }
                if (scopeBtnLabel != null) {
                    TextButton(
                        onClick = { showLocationDialog = true },
                        modifier = Modifier.testTag("profile_update_location_btn")
                    ) {
                        Text(scopeBtnLabel)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Profile Form
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Employee Credentials",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = profile?.employeeId.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Employee ID") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_employee_id_readonly")
                )

                Spacer(modifier = Modifier.height(14.dp))

                TaskzTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Full Name",
                    leadingIcon = Icons.Default.Person,
                    testTag = "profile_full_name_input"
                )

                Spacer(modifier = Modifier.height(14.dp))

                TaskzTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = "Mobile Number",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    testTag = "profile_mobile_input"
                )

                AnimatedVisibility(visible = updateState is ProfileUpdateState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = (updateState as? ProfileUpdateState.Error)?.message.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = updateState is ProfileUpdateState.Success) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TaskzSuccess.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = TaskzSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Profile updated successfully!",
                                color = TaskzSuccess,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                TaskzPrimaryButton(
                    text = "Save Profile Changes",
                    onClick = {
                        viewModel.updateProfile(context, fullName, mobile, selectedPhotoUri)
                    },
                    isLoading = updateState is ProfileUpdateState.Loading,
                    leadingIcon = Icons.Default.Save,
                    testTag = "save_profile_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // App Version & Diagnostics
        Text(
            text = "Taskz Workforce Client • v1.0.0 (Phase 1)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Logout Button
        TaskzOutlinedButton(
            text = "Logout",
            onClick = { showLogoutDialog = true },
            leadingIcon = Icons.Default.ExitToApp,
            testTag = "logout_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text("Are you sure you want to log out of Taskz? Your local cached session will be securely cleared.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogoutSuccess)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Logout", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLocationDialog) {
        val incidentRepo = remember { com.example.TaskzApplication.instance.systemIncidentRepository }
        val scopeCoroutines = rememberCoroutineScope()
        com.example.ui.home.DailyDutyCheckinDialog(
            orgHierarchy = orgHierarchy,
            currentScope = authContext.scope,
            currentActiveRole = authContext.activeRole,
            currentCheckin = null,
            isSaving = isSavingLocation,
            errorMessage = locationSaveError,
            onDismiss = {
                viewModel.clearLocationError()
                showLocationDialog = false
            },
            onConfirm = { zoneId, stateId, cityId, centreId, centreCode, centreName, role, lan ->
                viewModel.updateOperationalLocation(
                    roleCode = role,
                    zoneId = zoneId,
                    stateId = stateId,
                    cityId = cityId,
                    centreId = centreId,
                    centreCode = centreCode,
                    lanNumber = lan
                ) {
                    val user = profile
                    if (user != null) {
                        scopeCoroutines.launch {
                            incidentRepo.saveDailyDutyCheckin(
                                userId = user.id,
                                userName = user.fullName,
                                employeeId = user.employeeId,
                                centreCode = centreCode,
                                centreName = centreName,
                                role = role,
                                lan = lan
                            )
                        }
                    }
                    showLocationDialog = false
                }
            }
        )
    }
}
