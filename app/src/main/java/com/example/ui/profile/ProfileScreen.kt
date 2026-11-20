package com.example.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.model.AssignedRoleInfo
import com.example.core.model.TaskzRole
import com.example.ui.common.NothingDivider
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingHairlineCard
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.NothingSegmentedTabs
import com.example.ui.common.NothingTagChip
import com.example.ui.common.TaskzOutlinedButton
import com.example.ui.common.TaskzPrimaryButton
import com.example.ui.common.TaskzTextField
import com.example.ui.home.DailyDutyCheckinDialog
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogoutSuccess: () -> Unit,
    onNavigateToAlarmSettings: () -> Unit = {},
    onNavigateToGroups: () -> Unit = {},
    onNavigateToAdmin: () -> Unit = {},
    canManageUsers: Boolean = false,
    canSelfJoinGroups: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themePrefs = remember(context) { UserPreferencesDataStore(context.applicationContext) }
    val currentThemeMode by themePrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)

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
            .background(NothingTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile Avatar & Identity Header
        NothingHairlineCard(contentPadding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "New Profile Photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!profile?.avatarSignedUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile!!.avatarSignedUrl,
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(1.dp, NothingTheme.colors.border, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(NothingTheme.colors.surfaceElevated)
                                .border(1.dp, NothingTheme.colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            NothingDotMatrixText(
                                text = profile?.fullName?.take(2)?.uppercase() ?: "TZ",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NothingTheme.colors.accentPrimary)
                            .border(1.dp, NothingTheme.colors.surface, CircleShape)
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
                            tint = NothingTheme.colors.onAccentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = profile?.fullName?.ifBlank { "Employee" } ?: "Employee",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile?.employeeId?.ifBlank { "EMP" } ?: "EMP",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                        color = NothingTheme.colors.textSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NothingTagChip(
                            text = if (profile?.isActive == true) "ACTIVE" else "INACTIVE",
                            containerColor = if (profile?.isActive == true) NothingGreen.copy(alpha = 0.14f) else NothingRed.copy(alpha = 0.14f),
                            contentColor = if (profile?.isActive == true) NothingGreen else NothingRed,
                            borderColor = if (profile?.isActive == true) NothingGreen.copy(alpha = 0.4f) else NothingRed.copy(alpha = 0.4f)
                        )
                        NothingTagChip(text = authContext.activeRole.displayName)
                        if (authContext.isOnLeave) {
                            NothingTagChip(
                                text = "ON LEAVE",
                                containerColor = NothingAmber.copy(alpha = 0.14f),
                                contentColor = NothingAmber,
                                borderColor = NothingAmber.copy(alpha = 0.45f),
                                modifier = Modifier.testTag("profile_on_leave_badge")
                            )
                        }
                    }
                }
            }
        }

        // On Leave Status Card
        NothingHairlineCard(
            containerColor = if (authContext.isOnLeave) NothingAmber.copy(alpha = 0.08f) else NothingTheme.colors.surface,
            borderColor = if (authContext.isOnLeave) NothingAmber.copy(alpha = 0.45f) else NothingTheme.colors.border,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.testTag("profile_on_leave_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        tint = if (authContext.isOnLeave) NothingAmber else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (authContext.isOnLeave) "On Leave" else "On Duty",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (authContext.isOnLeave) NothingAmber else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (authContext.isOnLeave) "No new tasks or alarms assigned" else "Available for task assignment",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTheme.colors.textSecondary
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

        // Persistent Theme Mode Selector (System / Dark / Light)
        NothingHairlineCard(contentPadding = PaddingValues(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp)
                )
                NothingSectionHeader(title = "APPEARANCE")
            }
            Spacer(modifier = Modifier.height(10.dp))
            val themeModes = listOf(AppThemeMode.SYSTEM, AppThemeMode.DARK, AppThemeMode.LIGHT)
            NothingSegmentedTabs(
                options = listOf("System", "Dark", "Light"),
                selectedIndex = themeModes.indexOf(currentThemeMode).coerceAtLeast(0),
                onSelect = { idx ->
                    val chosen = themeModes.getOrElse(idx) { AppThemeMode.SYSTEM }
                    coroutineScope.launch {
                        themePrefs.saveThemeMode(chosen)
                    }
                },
                tagPrefix = "profile_theme_tab"
            )
        }

        // Assigned Roles & Active Role Selector Card
        NothingHairlineCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.testTag("profile_roles_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NothingSectionHeader(title = "ACTIVE ROLE")
                }
                if (isSwitchingRole) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val rolesList = authContext.assignedRoles.ifEmpty {
                listOf(
                    AssignedRoleInfo(
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
                    Surface(
                        onClick = {
                            if (!isActive && !isSwitchingRole) {
                                viewModel.switchActiveRole(role.roleCode)
                            }
                        },
                        enabled = !isSwitchingRole,
                        shape = RoundedCornerShape(999.dp),
                        color = if (isActive) NothingTheme.colors.accentPrimary else NothingTheme.colors.surfaceElevated,
                        border = BorderStroke(1.dp, if (isActive) NothingTheme.colors.accentPrimary else NothingTheme.colors.border),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("profile_role_chip_${role.roleCode}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active role",
                                    tint = NothingTheme.colors.onAccentPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = TaskzRole.fromCode(role.roleCode).displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isActive) NothingTheme.colors.onAccentPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Organizational Scope & Device Registration Card
        NothingHairlineCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.testTag("profile_scope_device_card")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationCity,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                NothingSectionHeader(title = "SCOPE & DEVICE")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${authContext.scope.summaryLabel()} (${authContext.activeRole.defaultScope.label})",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            val scopeParts = listOfNotNull(
                authContext.scope.zoneName?.let { "Zone: $it" },
                authContext.scope.stateName?.let { "State: $it" },
                authContext.scope.cityName?.let { "City: $it" },
                authContext.scope.centreCode?.let { "Centre: $it" },
                authContext.scope.assignedLan?.takeIf { it.isNotBlank() }?.let { "LAN: $it" }
            )
            if (scopeParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scopeParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = NothingGreen,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DEVICE: ${deviceId?.take(14) ?: "..."}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
            val scopeBtnLabel = when (authContext.activeRole) {
                TaskzRole.ZOM -> "Update Zone"
                TaskzRole.ROM -> "Update State"
                TaskzRole.CITY_HEAD -> "Update State & City"
                TaskzRole.VCO -> "Update Centre"
                TaskzRole.IT_MANAGER -> "Update Centre & LAN"
                TaskzRole.COMMAND_CENTER,
                TaskzRole.SUPER_ADMIN -> null
            }
            if (scopeBtnLabel != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { showLocationDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("profile_update_location_btn")
                ) {
                    Text(
                        text = scopeBtnLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = TechMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Quick Shortcuts Card
        NothingHairlineCard(contentPadding = PaddingValues(0.dp)) {
            ProfileNavRow(
                icon = Icons.Default.Alarm,
                title = "Alarm Reliability",
                subtitle = "Full-screen alerts & exact timing",
                onClick = onNavigateToAlarmSettings,
                testTag = "profile_nav_alarm"
            )
            if (canSelfJoinGroups) {
                NothingDivider()
                ProfileNavRow(
                    icon = Icons.Default.Groups,
                    title = "Exam Groups & Shifts",
                    subtitle = "1-click group join & shift setup",
                    onClick = onNavigateToGroups,
                    testTag = "profile_nav_groups"
                )
            }
            if (canManageUsers) {
                NothingDivider()
                ProfileNavRow(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "Admin Console",
                    subtitle = "Users, roles & system access",
                    onClick = onNavigateToAdmin,
                    testTag = "profile_nav_admin"
                )
            }
        }

        // Employee Credentials Form
        NothingHairlineCard(contentPadding = PaddingValues(14.dp)) {
            NothingSectionHeader(title = "EMPLOYEE CREDENTIALS")
            Spacer(modifier = Modifier.height(12.dp))

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
                        tint = NothingTheme.colors.textSecondary
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = NothingTheme.colors.textSecondary
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = NothingTheme.colors.border,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = NothingTheme.colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_employee_id_readonly")
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full Name",
                leadingIcon = Icons.Default.Person,
                testTag = "profile_full_name_input"
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskzTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = "Mobile Number",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                testTag = "profile_mobile_input"
            )

            AnimatedVisibility(visible = updateState is ProfileUpdateState.Error) {
                Surface(
                    color = NothingRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = (updateState as? ProfileUpdateState.Error)?.message.orEmpty(),
                        color = NothingRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            AnimatedVisibility(visible = updateState is ProfileUpdateState.Success) {
                Surface(
                    color = NothingGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, NothingGreen.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NothingGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Profile updated successfully",
                            color = NothingGreen,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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

        Text(
            text = "TASKZ CLIENT · BUILD 1.0",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
            color = NothingTheme.colors.textSecondary
        )

        TaskzOutlinedButton(
            text = if (isLoggingOut) "Signing Out..." else "Logout",
            onClick = { showLogoutDialog = true },
            enabled = !isLoggingOut,
            leadingIcon = Icons.Default.ExitToApp,
            testTag = "logout_button"
        )

        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = NothingTheme.colors.surface,
            title = {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text("Are you sure you want to log out of Taskz? Your local cached session will be cleared.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogoutSuccess)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
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
        DailyDutyCheckinDialog(
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

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NothingTheme.colors.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NothingTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
