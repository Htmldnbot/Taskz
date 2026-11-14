package com.example.ui.auth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import com.example.ui.theme.TaskzSuccess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import com.example.ui.common.AccountInactiveDialog
import com.example.ui.common.ConfigureSupabaseDialog
import com.example.ui.common.TaskzPrimaryButton
import com.example.ui.common.TaskzTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

enum class LoginAuthMethod {
    EMAIL_PASSWORD,
    EMPLOYEE_ID
}

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var loginMethod by rememberSaveable { mutableStateOf(LoginAuthMethod.EMAIL_PASSWORD) }

    var showContactAdminDialog by rememberSaveable { mutableStateOf(false) }
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showInactiveDialog by rememberSaveable { mutableStateOf(false) }

    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    var pendingGoogleProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showGoogleProfileModal by remember { mutableStateOf(false) }
    var isGoogleAuthAttempt by rememberSaveable { mutableStateOf(false) }

    // Handle Auth Success navigation
    LaunchedEffect(loginState) {
        if (loginState is AuthUiState.Success) {
            val prof = (loginState as AuthUiState.Success).profile
            if (prof.employeeId.isBlank()) {
                pendingGoogleProfile = prof
                showGoogleProfileModal = true
            } else {
                showGoogleProfileModal = false
                onAuthSuccess(prof)
            }
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

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(20.dp))

            // Content based on tab
            if (selectedTab == 0) {
                // Method selection chips for Login: Email vs Employee ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = loginMethod == LoginAuthMethod.EMAIL_PASSWORD,
                        onClick = {
                            loginMethod = LoginAuthMethod.EMAIL_PASSWORD
                            viewModel.clearLoginError()
                        },
                        label = { Text("Sign In with Email", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_email")
                    )
                    FilterChip(
                        selected = loginMethod == LoginAuthMethod.EMPLOYEE_ID,
                        onClick = {
                            loginMethod = LoginAuthMethod.EMPLOYEE_ID
                            viewModel.clearLoginError()
                        },
                        label = { Text("Employee ID", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_empid")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (loginMethod) {
                    LoginAuthMethod.EMAIL_PASSWORD -> {
                        EmailPasswordLoginForm(
                            uiState = if (isGoogleAuthAttempt) AuthUiState.Idle else loginState,
                            isLoadingGlobal = loginState is AuthUiState.Loading,
                            onLogin = { email, pwd ->
                                isGoogleAuthAttempt = false
                                focusManager.clearFocus()
                                viewModel.loginWithEmailPassword(email, pwd)
                            },
                            onForgotPassword = { showForgotPasswordDialog = true }
                        )
                    }
                    LoginAuthMethod.EMPLOYEE_ID -> {
                        EmployeeIdLoginForm(
                            uiState = if (isGoogleAuthAttempt) AuthUiState.Idle else loginState,
                            isLoadingGlobal = loginState is AuthUiState.Loading,
                            onLogin = { empId, pwd ->
                                isGoogleAuthAttempt = false
                                focusManager.clearFocus()
                                viewModel.login(empId, pwd)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Divider and Google Sign In
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  OR CONTINUE WITH  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        isGoogleAuthAttempt = true
                        viewModel.clearLoginError()
                        if (activity != null) {
                            viewModel.signInWithGoogle(activity)
                        } else {
                            Toast.makeText(context, "Activity context unavailable", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = loginState !is AuthUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_signin_button")
                ) {
                    if (loginState is AuthUiState.Loading && isGoogleAuthAttempt) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connecting to Google...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isGoogleAuthAttempt && loginState is AuthUiState.Error) {
                    val errText = (loginState as? AuthUiState.Error)?.message.orEmpty()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("google_auth_error_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
                // Unified first-time user registration form
                UnifiedSignUpForm(
                    uiState = signUpState,
                    onSignUp = { email, password, fullName, mobile, employeeId, photoUri ->
                        focusManager.clearFocus()
                        viewModel.signUpWithEmailPassword(
                            context = context,
                            email = email,
                            password = password,
                            fullName = fullName,
                            mobile = mobile,
                            employeeId = employeeId,
                            selectedPhotoUri = photoUri
                        )
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
                    text = "Contact Administrator / Request Help",
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

    if (showForgotPasswordDialog) {
        ForgotPasswordModal(
            viewModel = viewModel,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }

    if (showConfigDialog) {
        ConfigureSupabaseDialog(
            onDismiss = { showConfigDialog = false },
            onSave = { savedUrl, savedKey ->
                coroutineScope.launch {
                    runCatching {
                        com.example.TaskzApplication.instance.userPreferences.saveCustomConfig(savedUrl, savedKey)
                    }
                }
            }
        )
    }

    if (showInactiveDialog) {
        AccountInactiveDialog(
            onDismiss = { showInactiveDialog = false }
        )
    }

    if (showGoogleProfileModal && pendingGoogleProfile != null) {
        val googleUser = pendingGoogleProfile!!
        GoogleProfileCompletionDialog(
            initialName = googleUser.fullName,
            uiState = loginState,
            onDismiss = {
                showGoogleProfileModal = false
                viewModel.cancelPendingProfileCompletion()
            },
            onSubmit = { fullName, mobile, employeeId ->
                viewModel.completeGoogleProfile(
                    fullName = fullName,
                    mobile = mobile,
                    employeeId = employeeId
                )
            }
        )
    }
}

@Composable
private fun GoogleProfileCompletionDialog(
    initialName: String,
    uiState: AuthUiState,
    onDismiss: () -> Unit,
    onSubmit: (fullName: String, mobile: String, employeeId: String) -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf(initialName) }
    var mobile by rememberSaveable { mutableStateOf("") }
    var employeeId by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Complete Your Profile",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "First-time sign in: Please enter your workforce details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.filter { ch -> ch.isDigit() || ch == '+' }.take(14) },
                    label = { Text("Mobile Number (10 Digits or +CountryCode) *") },
                    placeholder = { Text("e.g., 9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = employeeId,
                    onValueChange = {
                        employeeId = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch in "._-" }.take(20)
                    },
                    label = { Text("Employee ID *") },
                    placeholder = { Text("e.g., 2780195") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                val displayedError = localError ?: (uiState as? AuthUiState.Error)?.message
                if (!displayedError.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayedError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    val cleanMobile = mobile.trim()
                    val cleanEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
                    if (cleanName.length < 2) {
                        localError = "Please enter your full name."
                        return@Button
                    }
                    if (cleanMobile.filter { it.isDigit() }.length < 10) {
                        localError = "Please enter a valid 10-digit mobile number."
                        return@Button
                    }
                    if (!cleanEmpId.matches(Regex("^[A-Z0-9._-]{2,40}$"))) {
                        localError = "Employee ID must be 2 to 40 letters or digits."
                        return@Button
                    }
                    localError = null
                    onSubmit(cleanName, cleanMobile, cleanEmpId)
                },
                enabled = uiState !is AuthUiState.Loading,
                shape = RoundedCornerShape(10.dp)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Complete & Continue")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Supabase Email & Password Login Form
 */
@Composable
private fun EmailPasswordLoginForm(
    uiState: AuthUiState,
    isLoadingGlobal: Boolean = false,
    onLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        TaskzTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = "Email Address *",
            placeholder = "you@example.com",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "email_input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        TaskzTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password *",
            placeholder = "Enter password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onLogin(email, password)
            }),
            testTag = "email_password_input"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.testTag("forgot_password_button")
            ) {
                Text("Forgot Password?", style = MaterialTheme.typography.bodySmall)
            }
        }

        AnimatedVisibility(visible = uiState is AuthUiState.Error) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = (uiState as? AuthUiState.Error)?.message.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TaskzPrimaryButton(
            text = "Sign In with Email",
            onClick = { onLogin(email, password) },
            isLoading = uiState is AuthUiState.Loading && !isLoadingGlobal || (isLoadingGlobal && uiState is AuthUiState.Loading),
            testTag = "email_login_button"
        )
    }
}

/**
 * Legacy Employee ID Login Form
 */
@Composable
private fun EmployeeIdLoginForm(
    uiState: AuthUiState,
    isLoadingGlobal: Boolean = false,
    onLogin: (String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var employeeId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        TaskzTextField(
            value = employeeId,
            onValueChange = {
                employeeId = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch in "._-" }.take(20)
            },
            label = "Employee ID",
            placeholder = "e.g., 2780195 or EMP1024",
            leadingIcon = Icons.Default.Badge,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "login_employee_id"
        )

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        TaskzPrimaryButton(
            text = "Login with Employee ID",
            onClick = { onLogin(employeeId, password) },
            isLoading = uiState is AuthUiState.Loading,
            testTag = "login_submit_button"
        )
    }
}

/**
 * Unified Registration Form for First-Time Users (Supabase Auth)
 */
@Composable
private fun UnifiedSignUpForm(
    uiState: AuthUiState,
    onSignUp: (email: String, password: String, fullName: String, mobile: String, employeeId: String, photoUri: Uri?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var employeeId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "New User Registration",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Enter your official details to create your Taskz account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // Optional Profile Photo Picker
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
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(76.dp)
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
                            modifier = Modifier.size(24.dp)
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

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = fullName,
            onValueChange = { fullName = it; localError = null },
            label = "Full Name *",
            placeholder = "e.g., Vikram Sharma",
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_fullname"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = email,
            onValueChange = { email = it.trim(); localError = null },
            label = "Email Address *",
            placeholder = "vikram@example.com",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_email"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = mobile,
            onValueChange = { input ->
                mobile = input.filter { it.isDigit() || it == '+' }.take(14)
                localError = null
            },
            label = "10-Digit Mobile Number *",
            placeholder = "9876543210",
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
            value = employeeId,
            onValueChange = { input ->
                employeeId = input.uppercase().filter { it.isLetterOrDigit() || it in "._-" }.take(20)
                localError = null
            },
            label = "Employee ID *",
            placeholder = "e.g., 2780195 or EMP1024",
            leadingIcon = Icons.Default.Badge,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_employee_id"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = password,
            onValueChange = { password = it; localError = null },
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
            onValueChange = { confirmPassword = it; localError = null },
            label = "Confirm Password *",
            placeholder = "Re-enter password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
            }),
            testTag = "signup_confirm_password"
        )

        val displayError = localError ?: (uiState as? AuthUiState.Error)?.message

        AnimatedVisibility(visible = !displayError.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = displayError.orEmpty(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TaskzPrimaryButton(
            text = "Create Account",
            onClick = {
                focusManager.clearFocus()
                val trimmedName = fullName.trim()
                val trimmedEmail = email.trim()
                val normalizedMob = SupabaseConfig.normalizeMobile(mobile)
                val trimmedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)

                when {
                    trimmedName.length < 2 -> {
                        localError = "Please enter your full name."
                    }
                    trimmedEmail.isBlank() || !trimmedEmail.contains("@") -> {
                        localError = "Please enter a valid email address."
                    }
                    !normalizedMob.matches(Regex("^\\+[1-9][0-9]{7,14}$")) -> {
                        localError = "Please enter a valid 10-digit mobile number."
                    }
                    !trimmedEmpId.matches(Regex("^[A-Z0-9._-]{2,40}$")) -> {
                        localError = "Employee ID must be 2 to 40 letters or digits."
                    }
                    password.length < 6 -> {
                        localError = "Password must be at least 6 characters."
                    }
                    password != confirmPassword -> {
                        localError = "Passwords do not match."
                    }
                    else -> {
                        localError = null
                        onSignUp(trimmedEmail, password, trimmedName, normalizedMob, trimmedEmpId, selectedPhotoUri)
                    }
                }
            },
            isLoading = uiState is AuthUiState.Loading,
            testTag = "signup_submit_button"
        )
    }
}

@Composable
fun ForgotPasswordModal(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isSuccess) {
                    Text(
                        text = statusMessage ?: "A password reset link has been sent to your email address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Enter your registered email address. We will send a password reset link if the account supports email recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TaskzTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        label = "Email Address",
                        placeholder = "you@example.com",
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        testTag = "forgot_pwd_email_input"
                    )

                    if (!statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isSuccess) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        isSending = true
                        statusMessage = null
                        viewModel.sendPasswordReset(email) { result ->
                            isSending = false
                            result.fold(
                                onSuccess = {
                                    isSuccess = true
                                    statusMessage = "Password reset email sent to $email. Please check your inbox."
                                },
                                onFailure = { error ->
                                    statusMessage = error.message ?: "Failed to send reset email."
                                }
                            )
                        }
                    },
                    enabled = !isSending,
                    modifier = Modifier.testTag("send_reset_email_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send Reset Link")
                    }
                }
            }
        },
        dismissButton = {
            if (!isSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
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

