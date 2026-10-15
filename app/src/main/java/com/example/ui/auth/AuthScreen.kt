package com.example.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.UserProfile
import com.example.ui.common.AccountInactiveDialog
import com.example.ui.common.ConfigureSupabaseDialog
import com.example.ui.common.TaskzOutlinedButton
import com.example.ui.common.TaskzPrimaryButton
import com.example.ui.common.TaskzTextField

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showContactAdminDialog by rememberSaveable { mutableStateOf(false) }
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showInactiveDialog by rememberSaveable { mutableStateOf(false) }

    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    // Handle Auth Success navigation
    LaunchedEffect(loginState) {
        if (loginState is AuthUiState.Success) {
            onAuthSuccess((loginState as AuthUiState.Success).profile)
        } else if (loginState is AuthUiState.AccountInactive) {
            showInactiveDialog = true
        }
    }

    LaunchedEffect(signUpState) {
        if (signUpState is AuthUiState.Success) {
            onAuthSuccess((signUpState as AuthUiState.Success).profile)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.testTag("backend_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Backend Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App Brand Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.taskz_launcher_icon_1791997779230),
                    contentDescription = "Taskz Logo",
                    modifier = Modifier.size(76.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Taskz",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Workforce Operations & Task Platform",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Tab Selector: Login vs Sign Up
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        viewModel.clearLoginError()
                        viewModel.clearSignUpError()
                    },
                    text = {
                        Text(
                            text = "Login",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_login")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.clearLoginError()
                        viewModel.clearSignUpError()
                    },
                    text = {
                        Text(
                            text = "Create Account",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("tab_signup")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content based on tab
            if (selectedTab == 0) {
                LoginForm(
                    uiState = loginState,
                    onLogin = { empId, pwd ->
                        focusManager.clearFocus()
                        viewModel.login(empId, pwd)
                    },
                    onContactAdmin = { showContactAdminDialog = true }
                )
            } else {
                SignUpForm(
                    uiState = signUpState,
                    onSignUp = { empId, name, mob, pwd, confirmPwd, uri ->
                        focusManager.clearFocus()
                        viewModel.signUp(context, empId, name, mob, pwd, confirmPwd, uri)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact Admin link at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showContactAdminDialog = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Contact Administrator / Forgot Password",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showContactAdminDialog) {
        ContactAdminModal(
            viewModel = viewModel,
            onDismiss = {
                showContactAdminDialog = false
                viewModel.resetResetRequestState()
            }
        )
    }

    if (showConfigDialog) {
        ConfigureSupabaseDialog(
            onDismiss = { showConfigDialog = false },
            onSave = { _, _ -> }
        )
    }

    if (showInactiveDialog) {
        AccountInactiveDialog(
            onDismiss = { showInactiveDialog = false }
        )
    }
}

@Composable
private fun LoginForm(
    uiState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onContactAdmin: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var employeeId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        TaskzTextField(
            value = employeeId,
            onValueChange = { employeeId = it.trim() },
            label = "Employee ID",
            placeholder = "e.g., EMP1024",
            leadingIcon = Icons.Default.Badge,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "login_employee_id"
        )

        Spacer(modifier = Modifier.height(16.dp))

        TaskzTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            placeholder = "Enter your password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onLogin(employeeId, password)
            }),
            testTag = "login_password"
        )

        AnimatedVisibility(visible = uiState is AuthUiState.Error) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = (uiState as? AuthUiState.Error)?.message.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TaskzPrimaryButton(
            text = "Login to Taskz",
            onClick = { onLogin(employeeId, password) },
            isLoading = uiState is AuthUiState.Loading,
            testTag = "login_submit_button"
        )
    }
}

@Composable
private fun SignUpForm(
    uiState: AuthUiState,
    onSignUp: (String, String, String, String, String, Uri?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var employeeId by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Optional Photo Picker
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
        ) {
            if (selectedPhotoUri != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "Profile Photo Preview",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { selectedPhotoUri = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove photo",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Upload Photo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = "Profile photo is optional",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(14.dp))

        TaskzTextField(
            value = employeeId,
            onValueChange = { employeeId = it.trim() },
            label = "Employee ID *",
            placeholder = "e.g., EMP1024",
            leadingIcon = Icons.Default.Badge,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_employee_id"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full Name *",
            placeholder = "e.g., Rahul Sharma",
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_full_name"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = "Mobile Number *",
            placeholder = "10-digit mobile number",
            leadingIcon = Icons.Default.Phone,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_mobile"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password *",
            placeholder = "Minimum 6 characters",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_password"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm Password *",
            placeholder = "Re-enter your password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSignUp(employeeId, fullName, mobile, password, confirmPassword, selectedPhotoUri)
            }),
            testTag = "signup_confirm_password"
        )

        AnimatedVisibility(visible = uiState is AuthUiState.Error) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = (uiState as? AuthUiState.Error)?.message.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TaskzPrimaryButton(
            text = "Create Account",
            onClick = {
                onSignUp(employeeId, fullName, mobile, password, confirmPassword, selectedPhotoUri)
            },
            isLoading = uiState is AuthUiState.Loading,
            testTag = "signup_submit_button"
        )
    }
}

@Composable
fun ContactAdminModal(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val resetState by viewModel.resetRequestState.collectAsStateWithLifecycle()
    var empId by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("I forgot my password.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Contact Administrator",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (resetState is ResetRequestUiState.Success) {
                    Text(
                        text = "Your request has been sent to the administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Submit a request to reset your password or update your employee credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TaskzTextField(
                        value = empId,
                        onValueChange = { empId = it.trim() },
                        label = "Employee ID",
                        placeholder = "e.g., EMP1024",
                        testTag = "reset_employee_id"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TaskzTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = "Registered Mobile Number",
                        placeholder = "10-digit number",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        testTag = "reset_mobile"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TaskzTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = "Message / Reason",
                        singleLine = false,
                        maxLines = 3,
                        testTag = "reset_message"
                    )

                    if (resetState is ResetRequestUiState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (resetState as ResetRequestUiState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (resetState is ResetRequestUiState.Success) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reset_success_done_button")
                ) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = { viewModel.submitResetRequest(empId, mobile, reason) },
                    enabled = resetState !is ResetRequestUiState.Loading,
                    modifier = Modifier.testTag("submit_reset_request_button")
                ) {
                    Text(if (resetState is ResetRequestUiState.Loading) "Submitting..." else "Submit Request")
                }
            }
        },
        dismissButton = {
            if (resetState !is ResetRequestUiState.Success) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
