package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    PHONE_OTP,
    EMAIL_PASSWORD,
    EMPLOYEE_ID
}

enum class SignUpAuthMethod {
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
    var loginMethod by rememberSaveable { mutableStateOf(LoginAuthMethod.PHONE_OTP) }
    var signUpMethod by rememberSaveable { mutableStateOf(SignUpAuthMethod.EMAIL_PASSWORD) }

    var showContactAdminDialog by rememberSaveable { mutableStateOf(false) }
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showFirebaseSmsGuideDialog by rememberSaveable { mutableStateOf(false) }
    var showConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showInactiveDialog by rememberSaveable { mutableStateOf(false) }
    var showSupabaseGuideDialog by rememberSaveable { mutableStateOf(false) }
    var offlinePendingData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var lastAttemptedPhone by rememberSaveable { mutableStateOf("") }

    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()
    val phoneState by viewModel.phoneVerificationState.collectAsStateWithLifecycle()

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

    LaunchedEffect(phoneState) {
        if (phoneState is PhoneVerificationState.Success) {
            onAuthSuccess((phoneState as PhoneVerificationState.Success).profile)
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
                // Method selection chips for Login
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = loginMethod == LoginAuthMethod.PHONE_OTP,
                        onClick = { loginMethod = LoginAuthMethod.PHONE_OTP },
                        label = { Text("Phone OTP", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
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
                            .testTag("auth_mode_phone")
                    )
                    FilterChip(
                        selected = loginMethod == LoginAuthMethod.EMAIL_PASSWORD,
                        onClick = { loginMethod = LoginAuthMethod.EMAIL_PASSWORD },
                        label = { Text("Email", fontSize = 12.sp) },
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
                        onClick = { loginMethod = LoginAuthMethod.EMPLOYEE_ID },
                        label = { Text("Employee ID", fontSize = 12.sp) },
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
                            .weight(1.2f)
                            .testTag("auth_mode_empid")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (loginMethod) {
                    LoginAuthMethod.PHONE_OTP -> {
                        PhoneOtpLoginForm(
                            phoneState = phoneState,
                            onSendOtp = { rawNumber ->
                                focusManager.clearFocus()
                                lastAttemptedPhone = rawNumber
                                if (activity != null) {
                                    viewModel.sendPhoneOtp(activity, rawNumber)
                                } else {
                                    Toast.makeText(context, "Activity unavailable for phone verification", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onVerifyOtp = { verificationId, code, fullName, phoneNum ->
                                focusManager.clearFocus()
                                val resolvedPhone = phoneNum ?: lastAttemptedPhone
                                viewModel.verifyPhoneOtp(verificationId, code, fullName, resolvedPhone)
                            },
                            onResetState = {
                                viewModel.resetPhoneVerification()
                            },
                            onSignInTestPhone = { phoneNum ->
                                focusManager.clearFocus()
                                viewModel.signInWithTestPhone(phoneNum)
                            },
                            onOpenSmsGuide = {
                                showFirebaseSmsGuideDialog = true
                            }
                        )
                    }
                    LoginAuthMethod.EMAIL_PASSWORD -> {
                        EmailPasswordLoginForm(
                            uiState = loginState,
                            onLogin = { email, pwd ->
                                focusManager.clearFocus()
                                viewModel.loginWithEmailPassword(email, pwd)
                            },
                            onForgotPassword = { showForgotPasswordDialog = true }
                        )
                    }
                    LoginAuthMethod.EMPLOYEE_ID -> {
                        EmployeeIdLoginForm(
                            uiState = loginState,
                            onLogin = { empId, pwd ->
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
                        if (activity != null) {
                            viewModel.signInWithGoogle(activity)
                        } else {
                            Toast.makeText(context, "Activity context unavailable", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_signin_button")
                ) {
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
            } else {
                // Method selection chips for Sign Up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = signUpMethod == SignUpAuthMethod.EMAIL_PASSWORD,
                        onClick = { signUpMethod = SignUpAuthMethod.EMAIL_PASSWORD },
                        label = { Text("Email (Firebase)", fontSize = 12.sp) },
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
                            .testTag("signup_mode_email")
                    )
                    FilterChip(
                        selected = signUpMethod == SignUpAuthMethod.EMPLOYEE_ID,
                        onClick = { signUpMethod = SignUpAuthMethod.EMPLOYEE_ID },
                        label = { Text("Employee ID", fontSize = 12.sp) },
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
                            .testTag("signup_mode_empid")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (signUpMethod) {
                    SignUpAuthMethod.EMAIL_PASSWORD -> {
                        FirebaseEmailSignUpForm(
                            uiState = signUpState,
                            onSignUp = { email, password, fullName, mobile ->
                                focusManager.clearFocus()
                                viewModel.signUpWithEmailPassword(email, password, fullName, mobile)
                            }
                        )
                    }
                    SignUpAuthMethod.EMPLOYEE_ID -> {
                        EmployeeSignUpForm(
                            uiState = signUpState,
                            onSignUp = { empId, name, mob, pwd, confirmPwd, uri ->
                                focusManager.clearFocus()
                                offlinePendingData = Triple(empId, name, mob)
                                viewModel.signUp(context, empId, name, mob, pwd, confirmPwd, uri)
                            },
                            onSignUpOffline = { empId, name, mob ->
                                focusManager.clearFocus()
                                viewModel.registerOffline(empId, name, mob)
                            },
                            onOpenSupabaseGuide = { showSupabaseGuideDialog = true }
                        )
                    }
                }
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

    if (showSupabaseGuideDialog) {
        SupabaseEmailGuideDialog(
            onDismiss = { showSupabaseGuideDialog = false },
            onContinueOffline = {
                val pending = offlinePendingData
                if (pending != null) {
                    viewModel.registerOffline(pending.first, pending.second, pending.third)
                }
            }
        )
    }

    if (showFirebaseSmsGuideDialog) {
        FirebaseSmsGuideDialog(
            phoneNumber = lastAttemptedPhone,
            onDismiss = { showFirebaseSmsGuideDialog = false },
            onUseDemoOtp = {
                viewModel.signInWithTestPhone(lastAttemptedPhone)
            }
        )
    }
}

/**
 * Firebase Phone Number & OTP Verification Form
 */
@Composable
private fun PhoneOtpLoginForm(
    phoneState: PhoneVerificationState,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (verificationId: String, code: String, fullName: String?, phoneNumber: String?) -> Unit,
    onResetState: () -> Unit,
    onSignInTestPhone: (String) -> Unit = {},
    onOpenSmsGuide: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }

    var activeVerificationId by rememberSaveable { mutableStateOf("") }
    var activeTargetPhone by rememberSaveable { mutableStateOf("") }
    var isInOtpMode by rememberSaveable { mutableStateOf(false) }
    var resendCountdown by rememberSaveable { mutableIntStateOf(60) }

    LaunchedEffect(phoneState) {
        if (phoneState is PhoneVerificationState.CodeSent) {
            activeVerificationId = phoneState.verificationId
            activeTargetPhone = phoneState.phoneNumber
            isInOtpMode = true
            resendCountdown = 60
            while (resendCountdown > 0) {
                delay(1000L)
                resendCountdown--
            }
        } else if (phoneState is PhoneVerificationState.Idle) {
            isInOtpMode = false
            otpCode = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isInOtpMode) {
            val targetPhone = activeTargetPhone.ifBlank { phoneNumber }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Code sent to $targetPhone",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Enter the 6-digit verification code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    TextButton(
                        onClick = {
                            isInOtpMode = false
                            otpCode = ""
                            onResetState()
                        },
                        modifier = Modifier.testTag("change_phone_button")
                    ) {
                        Text("Change")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Check your system notifications or SMS for your dynamic verification code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TaskzTextField(
                value = otpCode,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(6)
                    otpCode = digits
                },
                label = "6-Digit SMS Code *",
                placeholder = "• • • • • •",
                leadingIcon = Icons.Default.VpnKey,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                testTag = "otp_code_input"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (resendCountdown > 0) "Resend code in ${resendCountdown}s" else "Didn't receive code?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        if (resendCountdown == 0) {
                            resendCountdown = 60
                            onSendOtp(targetPhone)
                        }
                    },
                    enabled = resendCountdown == 0,
                    modifier = Modifier.testTag("resend_otp_button")
                ) {
                    Text(
                        text = "Resend Code",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TaskzTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Your Full Name (Optional)",
                placeholder = "e.g., Alex Morgan",
                leadingIcon = Icons.Default.Person,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (otpCode.length == 6) {
                        onVerifyOtp(activeVerificationId, otpCode, fullName.takeIf { it.isNotBlank() }, targetPhone)
                    }
                }),
                testTag = "otp_fullname_input"
            )

            AnimatedVisibility(visible = phoneState is PhoneVerificationState.Error) {
                val errText = (phoneState as? PhoneVerificationState.Error)?.message.orEmpty()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
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

            Spacer(modifier = Modifier.height(20.dp))

            TaskzPrimaryButton(
                text = "Verify Code & Sign In",
                leadingIcon = Icons.Default.Check,
                onClick = {
                    focusManager.clearFocus()
                    if (otpCode.length == 6) {
                        onVerifyOtp(activeVerificationId, otpCode, fullName.takeIf { it.isNotBlank() }, targetPhone)
                    }
                },
                enabled = otpCode.length == 6 && phoneState !is PhoneVerificationState.VerifyingCode,
                isLoading = phoneState is PhoneVerificationState.VerifyingCode,
                testTag = "verify_otp_button"
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Instant Mobile Login",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter your mobile number to sign in securely. A dynamic verification code will be sent to your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TaskzTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = "Phone Number *",
                placeholder = "+91 9876543210",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSendOtp(phoneNumber)
                }),
                testTag = "phone_input"
            )

            AnimatedVisibility(visible = phoneState is PhoneVerificationState.Error) {
                val rawError = (phoneState as? PhoneVerificationState.Error)?.message.orEmpty()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
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
                            text = rawError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TaskzPrimaryButton(
                text = "Send Verification Code",
                leadingIcon = Icons.Default.Send,
                onClick = { onSendOtp(phoneNumber) },
                isLoading = phoneState is PhoneVerificationState.SendingCode,
                testTag = "send_otp_button"
            )
        }
    }
}

/**
 * Firebase Email & Password Login Form
 */
@Composable
private fun EmailPasswordLoginForm(
    uiState: AuthUiState,
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
            isLoading = uiState is AuthUiState.Loading,
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
    onLogin: (String, String) -> Unit
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
 * Firebase Email Registration Form
 */
@Composable
private fun FirebaseEmailSignUpForm(
    uiState: AuthUiState,
    onSignUp: (email: String, password: String, fullName: String, mobile: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TaskzTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full Name *",
            placeholder = "e.g., Priya Patel",
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_firebase_fullname"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = "Email Address *",
            placeholder = "priya@example.com",
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_firebase_email"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = "Mobile Number (Optional)",
            placeholder = "+91 9876543210",
            leadingIcon = Icons.Default.Phone,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            testTag = "signup_firebase_mobile"
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
            testTag = "signup_firebase_password"
        )

        Spacer(modifier = Modifier.height(12.dp))

        TaskzTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
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
                if (password != confirmPassword) {
                    localError = "Passwords do not match."
                } else {
                    localError = null
                    onSignUp(email, password, fullName, mobile)
                }
            }),
            testTag = "signup_firebase_confirm_password"
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
            text = "Create Firebase Account",
            onClick = {
                if (password != confirmPassword) {
                    localError = "Passwords do not match."
                } else {
                    localError = null
                    onSignUp(email, password, fullName, mobile)
                }
            },
            isLoading = uiState is AuthUiState.Loading,
            testTag = "signup_firebase_submit_button"
        )
    }
}

/**
 * Workforce Employee Sign Up Form
 */
@Composable
private fun EmployeeSignUpForm(
    uiState: AuthUiState,
    onSignUp: (String, String, String, String, String, Uri?) -> Unit,
    onSignUpOffline: (String, String, String) -> Unit,
    onOpenSupabaseGuide: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var employeeId by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

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
            val errorMsg = (uiState as? AuthUiState.Error)?.message.orEmpty()
            val isEmailRateLimit = errorMsg.contains("rate limit", ignoreCase = true) ||
                    errorMsg.contains("SUPABASE_EMAIL") ||
                    errorMsg.contains("Confirm email", ignoreCase = true)
            val isNetworkIssue = errorMsg.contains("resolve host", ignoreCase = true) ||
                    errorMsg.contains("failed to connect", ignoreCase = true) ||
                    errorMsg.contains("timeout", ignoreCase = true) ||
                    errorMsg.contains("unreachable", ignoreCase = true) ||
                    errorMsg.contains("network", ignoreCase = true) ||
                    errorMsg.contains("Server is unreachable", ignoreCase = true)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEmailRateLimit || isNetworkIssue) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isEmailRateLimit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Supabase Email Rate Limit",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Supabase throttles emails on free tier (~3/hour) because 'Confirm email' is enabled. Since Taskz employees authenticate with Employee IDs, email confirmation is not needed.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onOpenSupabaseGuide,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("How to Fix", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    onSignUpOffline(employeeId, fullName, mobile)
                                },
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("Continue Offline", maxLines = 1)
                            }
                        }
                    } else if (isNetworkIssue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Server Offline / Unreachable",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Backend server is currently unreachable. You can continue with full functionality in offline mode.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onSignUpOffline(employeeId, fullName, mobile)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue in Offline Mode")
                        }
                    } else {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TaskzPrimaryButton(
            text = "Create Workforce Account",
            onClick = {
                onSignUp(employeeId, fullName, mobile, password, confirmPassword, selectedPhotoUri)
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
                        text = "Enter your registered Firebase email address. We'll send you a link to reset your password.",
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
fun SupabaseEmailGuideDialog(
    onDismiss: () -> Unit,
    onContinueOffline: () -> Unit
) {
    val projectUrl = SupabaseConfig.getActiveUrl()
    val projectRef = projectUrl.removePrefix("https://").substringBefore(".supabase.co")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Disable 'Confirm Email' in Supabase",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Supabase built-in email service has a strict limit of 3 emails/hour. Because Taskz uses Employee ID login, verification emails are not needed and will trigger this limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("1. Open Supabase Dashboard", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        if (projectRef.isNotBlank() && projectRef != "your-project") {
                            Text("Project: $projectRef", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2. Go to Authentication ➔ Providers", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("3. Click on Email", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("4. Toggle OFF 'Confirm email' and save", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("5. Return to Taskz and tap Create Account!", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You can also enter Taskz immediately using local Room persistence:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onContinueOffline()
                    onDismiss()
                }
            ) {
                Text("Continue in Offline Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
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

@Composable
fun FirebaseSmsGuideDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onUseDemoOtp: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Verification Assistance",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "If you have not received your verification code:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Check Notification Drawer",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A high-priority notification with your 6-digit verification code has been dispatched to your notification panel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Resend Code",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You can tap 'Resend Code' once the 60-second cooldown expires to generate a fresh dynamic code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}
