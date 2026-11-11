package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val profile: UserProfile) : AuthUiState
    data class Error(val message: String) : AuthUiState
    object AccountInactive : AuthUiState
}

sealed interface ResetRequestUiState {
    object Idle : ResetRequestUiState
    object Loading : ResetRequestUiState
    object Success : ResetRequestUiState
    data class Error(val message: String) : ResetRequestUiState
}

sealed interface PhoneVerificationState {
    object Idle : PhoneVerificationState
    object SendingCode : PhoneVerificationState
    data class CodeSent(val verificationId: String, val phoneNumber: String, val dynamicCode: String = "") : PhoneVerificationState
    object VerifyingCode : PhoneVerificationState
    data class Success(val profile: UserProfile) : PhoneVerificationState
    data class Error(val message: String) : PhoneVerificationState
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val firebaseAuthService: com.example.data.remote.FirebaseAuthService = com.example.TaskzApplication.instance.firebaseAuthService
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val signUpState: StateFlow<AuthUiState> = _signUpState.asStateFlow()

    private val _resetRequestState = MutableStateFlow<ResetRequestUiState>(ResetRequestUiState.Idle)
    val resetRequestState: StateFlow<ResetRequestUiState> = _resetRequestState.asStateFlow()

    private val _phoneVerificationState = MutableStateFlow<PhoneVerificationState>(PhoneVerificationState.Idle)
    val phoneVerificationState: StateFlow<PhoneVerificationState> = _phoneVerificationState.asStateFlow()

    fun login(employeeId: String, password: String) {
        val normalizedEmpId = com.example.core.network.SupabaseConfig.normalizeEmployeeId(employeeId)
        if (normalizedEmpId.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your Employee ID.")
            return
        }
        if (password.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = authRepository.login(normalizedEmpId, password)
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    val msg = error.message.orEmpty()
                    if (msg.contains("inactive", ignoreCase = true)) {
                        _loginState.value = AuthUiState.AccountInactive
                    } else {
                        _loginState.value = AuthUiState.Error(msg.ifBlank { "Login failed. Check your Employee ID and password." })
                    }
                }
            )
        }
    }

    fun signUp(
        context: Context,
        employeeId: String,
        fullName: String,
        mobile: String,
        password: String,
        confirmPassword: String,
        selectedPhotoUri: Uri?
    ) {
        val normalizedEmpId = com.example.core.network.SupabaseConfig.normalizeEmployeeId(employeeId)
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()

        // Validation
        if (normalizedEmpId.length < 2 || normalizedEmpId.length > 40) {
            _signUpState.value = AuthUiState.Error("Employee ID must be between 2 and 40 characters.")
            return
        }
        if (!normalizedEmpId.matches(Regex("^[A-Z0-9._-]+$"))) {
            _signUpState.value = AuthUiState.Error("Employee ID may only contain letters, numbers, dots, underscores, or hyphens.")
            return
        }
        if (trimmedName.length < 2) {
            _signUpState.value = AuthUiState.Error("Please enter your full name (at least 2 characters).")
            return
        }
        val normalizedMobile = normalizeMobile(trimmedMobile)
        val mobileDigits = normalizedMobile.filter { it.isDigit() }
        if (mobileDigits.length < 8 || mobileDigits.length > 15 || !normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _signUpState.value = AuthUiState.Error("Please enter a valid mobile number (e.g., +91 9876543210 or 9876543210).")
            return
        }
        if (password.length < 6) {
            _signUpState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }
        if (password != confirmPassword) {
            _signUpState.value = AuthUiState.Error("Passwords do not match.")
            return
        }

        viewModelScope.launch {
            _signUpState.value = AuthUiState.Loading

            // Process image if selected
            var photoBytes: ByteArray? = null
            var photoFilename: String? = null
            if (selectedPhotoUri != null) {
                photoBytes = compressImageUri(context, selectedPhotoUri)
                photoFilename = "avatar_${System.currentTimeMillis()}.jpg"
            }

            val result = authRepository.signUp(
                employeeId = normalizedEmpId,
                password = password,
                fullName = trimmedName,
                mobile = normalizedMobile,
                photoBytes = photoBytes,
                photoFilename = photoFilename
            )

            result.fold(
                onSuccess = { profile ->
                    _signUpState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _signUpState.value = AuthUiState.Error(error.message ?: "Sign up failed. Please try again.")
                }
            )
        }
    }

    /**
     * Continue in Offline / Local Demo mode when backend auth is unavailable or rate limited.
     */
    fun registerOffline(
        employeeId: String,
        fullName: String,
        mobile: String
    ) {
        val trimmedEmpId = employeeId.trim()
        val trimmedName = fullName.trim()
        val normalizedMobile = normalizeMobile(mobile.trim())

        if (trimmedEmpId.length < 3) {
            _signUpState.value = AuthUiState.Error("Employee ID must be at least 3 characters.")
            return
        }
        if (trimmedName.isBlank()) {
            _signUpState.value = AuthUiState.Error("Please enter your full name.")
            return
        }

        viewModelScope.launch {
            _signUpState.value = AuthUiState.Loading
            val result = authRepository.registerOfflineDemo(
                employeeId = trimmedEmpId,
                fullName = trimmedName,
                mobile = normalizedMobile
            )
            result.fold(
                onSuccess = { profile ->
                    _signUpState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _signUpState.value = AuthUiState.Error(error.message ?: "Offline registration failed.")
                }
            )
        }
    }

    companion object {
        fun normalizeMobile(input: String): String {
            val trimmed = input.trim()
            val cleaned = trimmed.replace(Regex("[^0-9+]"), "")
            if (cleaned.startsWith("+")) {
                return cleaned
            }
            val digits = cleaned.replace("+", "")
            return when {
                digits.length == 10 && digits[0] in '6'..'9' -> "+91$digits"
                digits.length == 10 -> "+1$digits"
                digits.length == 11 && digits.startsWith("1") -> "+$digits"
                digits.length == 12 && digits.startsWith("91") -> "+$digits"
                else -> "+$digits"
            }
        }
    }

    fun submitResetRequest(employeeId: String, mobile: String, message: String?) {
        val trimmedEmpId = employeeId.trim()
        val trimmedMobile = mobile.trim().replace(Regex("[^0-9]"), "")

        if (trimmedEmpId.isBlank()) {
            _resetRequestState.value = ResetRequestUiState.Error("Please enter your Employee ID.")
            return
        }
        if (trimmedMobile.length < 10) {
            _resetRequestState.value = ResetRequestUiState.Error("Please enter a valid mobile number.")
            return
        }

        viewModelScope.launch {
            _resetRequestState.value = ResetRequestUiState.Loading
            val result = authRepository.submitPasswordResetRequest(
                employeeId = trimmedEmpId,
                mobile = trimmedMobile,
                message = message?.takeIf { it.isNotBlank() } ?: "I forgot my password."
            )
            result.fold(
                onSuccess = {
                    _resetRequestState.value = ResetRequestUiState.Success
                },
                onFailure = { err ->
                    _resetRequestState.value = ResetRequestUiState.Error(err.message ?: "Failed to submit request.")
                }
            )
        }
    }

    fun resetResetRequestState() {
        _resetRequestState.value = ResetRequestUiState.Idle
    }

    fun clearLoginError() {
        if (_loginState.value is AuthUiState.Error) {
            _loginState.value = AuthUiState.Idle
        }
    }

    fun clearSignUpError() {
        if (_signUpState.value is AuthUiState.Error) {
            _signUpState.value = AuthUiState.Idle
        }
    }

    // ----------------------------------------------------
    // Firebase Phone Authentication (Phone No. + OTP)
    // ----------------------------------------------------

    fun sendPhoneOtp(activity: Activity? = null, rawPhoneNumber: String) {
        val trimmed = rawPhoneNumber.trim()
        val digits = trimmed.filter { it.isDigit() }
        val tenDigits = if (digits.length == 12 && digits.startsWith("91")) digits.takeLast(10) else digits

        if (tenDigits.length != 10 || tenDigits[0] !in '6'..'9') {
            _phoneVerificationState.value = PhoneVerificationState.Error("Please enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9.")
            return
        }

        val fullIndianNumber = "+91$tenDigits"
        _phoneVerificationState.value = PhoneVerificationState.SendingCode
        firebaseAuthService.sendPhoneOtp(
            activity = activity,
            phoneNumber = fullIndianNumber,
            onCodeSent = { verificationId, dynamicCode ->
                _phoneVerificationState.value = PhoneVerificationState.CodeSent(
                    verificationId = verificationId,
                    phoneNumber = tenDigits,
                    dynamicCode = dynamicCode
                )
            },
            onAutoVerified = { profile ->
                _phoneVerificationState.value = PhoneVerificationState.Success(profile)
                _loginState.value = AuthUiState.Success(profile)
            },
            onError = { errMsg ->
                _phoneVerificationState.value = PhoneVerificationState.Error(errMsg)
            }
        )
    }

    fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        fullName: String? = null,
        phoneNumber: String? = null
    ) {
        val trimmedOtp = otpCode.trim()
        if (trimmedOtp.length != 6) {
            _phoneVerificationState.value = PhoneVerificationState.Error("Please enter the 6-digit verification code.")
            return
        }

        viewModelScope.launch {
            _phoneVerificationState.value = PhoneVerificationState.VerifyingCode
            val result = firebaseAuthService.verifyOtpAndSignIn(verificationId, trimmedOtp, fullName, phoneNumber)
            result.fold(
                onSuccess = { profile ->
                    _phoneVerificationState.value = PhoneVerificationState.Success(profile)
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _phoneVerificationState.value = PhoneVerificationState.Error(error.message ?: "Verification failed. Please retry.")
                }
            )
        }
    }

    fun resetPhoneVerification() {
        _phoneVerificationState.value = PhoneVerificationState.Idle
    }

    /**
     * Fallback login when live carrier SMS is restricted by Firebase SMS Region Policy.
     */
    fun signInWithTestPhone(phoneNumber: String, fullName: String? = null) {
        val trimmed = phoneNumber.trim()
        if (trimmed.length < 8) {
            _phoneVerificationState.value = PhoneVerificationState.Error("Please enter a valid phone number.")
            return
        }
        viewModelScope.launch {
            _phoneVerificationState.value = PhoneVerificationState.VerifyingCode
            val result = firebaseAuthService.signInWithTestPhone(trimmed, fullName)
            result.fold(
                onSuccess = { profile ->
                    _phoneVerificationState.value = PhoneVerificationState.Success(profile)
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _phoneVerificationState.value = PhoneVerificationState.Error(error.message ?: "Test login failed.")
                }
            )
        }
    }

    // ----------------------------------------------------
    // Firebase Email & Password Authentication
    // ----------------------------------------------------

    fun loginWithEmailPassword(emailOrEmpId: String, password: String) {
        val trimmed = emailOrEmpId.trim()
        if (trimmed.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your registered Email or Employee ID.")
            return
        }
        if (password.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = firebaseAuthService.signInWithEmailPassword(trimmed, password)
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _loginState.value = AuthUiState.Error(error.message ?: "Sign-in failed. Please check your credentials.")
                }
            )
        }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            onResult(Result.failure(IllegalArgumentException("Please enter a valid email address.")))
            return
        }
        viewModelScope.launch {
            val result = firebaseAuthService.sendPasswordReset(trimmedEmail)
            onResult(result)
        }
    }

    fun signUpWithEmailPassword(
        email: String,
        password: String,
        fullName: String,
        mobile: String,
        employeeId: String = ""
    ) {
        val trimmedEmail = email.trim()
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()
        val trimmedEmpId = employeeId.trim()

        if (trimmedName.length < 2) {
            _signUpState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            _signUpState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (trimmedMobile.filter { it.isDigit() }.length < 10) {
            _signUpState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
            return
        }
        if (trimmedEmpId.isBlank()) {
            _signUpState.value = AuthUiState.Error("Please enter your Employee ID (up to 7 digits).")
            return
        }
        if (password.length < 6) {
            _signUpState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _signUpState.value = AuthUiState.Loading
            val result = firebaseAuthService.signUpWithEmailPassword(
                email = trimmedEmail,
                password = password,
                fullName = trimmedName,
                mobile = trimmedMobile,
                employeeId = trimmedEmpId
            )
            result.fold(
                onSuccess = { profile ->
                    _signUpState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _signUpState.value = AuthUiState.Error(error.message ?: "Registration failed.")
                }
            )
        }
    }

    // ----------------------------------------------------
    // Google Sign-In via Credential Manager
    // ----------------------------------------------------

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = firebaseAuthService.signInWithGoogle(activity)
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _loginState.value = AuthUiState.Error(error.message ?: "Google Sign-In failed.")
                }
            )
        }
    }

    fun signInWithGoogleToken(idToken: String, displayName: String? = null, email: String? = null) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = firebaseAuthService.signInWithGoogleIdToken(idToken, displayName, email)
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _loginState.value = AuthUiState.Error(error.message ?: "Google Sign-In failed.")
                }
            )
        }
    }

    fun completeGoogleProfile(
        uid: String,
        fullName: String,
        mobile: String,
        employeeId: String,
        role: String
    ) {
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()
        val trimmedEmpId = employeeId.trim()

        if (trimmedName.length < 2) {
            _loginState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        if (trimmedMobile.filter { it.isDigit() }.length < 10) {
            _loginState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
            return
        }
        if (trimmedEmpId.isBlank() || trimmedEmpId.length > 7 || !trimmedEmpId.all { it.isDigit() }) {
            _loginState.value = AuthUiState.Error("Employee ID must be numeric and up to 7 digits.")
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = firebaseAuthService.completeGoogleProfile(
                uid = uid,
                fullName = trimmedName,
                mobile = trimmedMobile,
                employeeId = trimmedEmpId,
                appRole = role
            )
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    _loginState.value = AuthUiState.Error(error.message ?: "Failed to save profile details.")
                }
            )
        }
    }

    private suspend fun compressImageUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return@withContext null

            // Resize if exceeds 800px
            val maxDimension = 800
            val width = original.width
            val height = original.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    val targetW = maxDimension
                    val targetH = (maxDimension / ratio).toInt()
                    Bitmap.createScaledBitmap(original, targetW, targetH, true)
                } else {
                    val targetH = maxDimension
                    val targetW = (maxDimension * ratio).toInt()
                    Bitmap.createScaledBitmap(original, targetW, targetH, true)
                }
            } else {
                original
            }

            val outStream = ByteArrayOutputStream()
            scale.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
            outStream.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
