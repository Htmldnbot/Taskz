package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.UserProfile
import com.example.core.network.SupabaseConfig
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.GoogleSignInTokenResult
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

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val googleAuthManager: GoogleAuthManager = GoogleAuthManager()
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val signUpState: StateFlow<AuthUiState> = _signUpState.asStateFlow()

    private val _resetRequestState = MutableStateFlow<ResetRequestUiState>(ResetRequestUiState.Idle)
    val resetRequestState: StateFlow<ResetRequestUiState> = _resetRequestState.asStateFlow()

    fun login(employeeId: String, password: String) {
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
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
                        _loginState.value = AuthUiState.Error(
                            msg.ifBlank { "Login failed. Check your Employee ID and password." }
                        )
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
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val trimmedName = fullName.trim()
        val normalizedMobile = normalizeMobile(mobile)

        if (!normalizedEmpId.matches(Regex("^[A-Z0-9._-]{2,40}$"))) {
            _signUpState.value = AuthUiState.Error(
                "Employee ID must be 2 to 40 letters, numbers, dots, underscores, or hyphens."
            )
            return
        }
        if (trimmedName.length < 2) {
            _signUpState.value = AuthUiState.Error("Please enter your full name (at least 2 characters).")
            return
        }
        if (!normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _signUpState.value = AuthUiState.Error(
                "Please enter a valid mobile number (e.g., +919876543210 or 9876543210)."
            )
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

    companion object {
        fun normalizeMobile(input: String): String = SupabaseConfig.normalizeMobile(input)
    }

    fun submitResetRequest(employeeId: String, mobile: String, message: String?) {
        val trimmedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)
        val normalizedMobile = normalizeMobile(mobile)

        if (trimmedEmpId.isBlank()) {
            _resetRequestState.value = ResetRequestUiState.Error("Please enter your Employee ID.")
            return
        }
        if (!normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _resetRequestState.value = ResetRequestUiState.Error("Please enter a valid mobile number.")
            return
        }

        viewModelScope.launch {
            _resetRequestState.value = ResetRequestUiState.Loading
            val result = authRepository.submitPasswordResetRequest(
                employeeId = trimmedEmpId,
                mobile = normalizedMobile,
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
    // Email & Employee ID Authentication (Native Supabase Auth)
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

        if (!trimmed.contains("@")) {
            login(trimmed, password)
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = authRepository.loginWithEmail(trimmed, password)
            result.fold(
                onSuccess = { profile ->
                    _loginState.value = AuthUiState.Success(profile)
                },
                onFailure = { error ->
                    val msg = error.message.orEmpty()
                    if (msg.contains("inactive", ignoreCase = true)) {
                        _loginState.value = AuthUiState.AccountInactive
                    } else {
                        _loginState.value = AuthUiState.Error(
                            msg.ifBlank { "Sign-in failed. Please check your email and password." }
                        )
                    }
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
            val result = authRepository.sendPasswordResetEmail(trimmedEmail)
            onResult(result)
        }
    }

    fun signUpWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        fullName: String,
        mobile: String,
        employeeId: String,
        selectedPhotoUri: Uri? = null
    ) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = fullName.trim()
        val normalizedMobile = normalizeMobile(mobile)
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)

        if (trimmedName.length < 2) {
            _signUpState.value = AuthUiState.Error("Please enter your full name (at least 2 characters).")
            return
        }
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            _signUpState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (!normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _signUpState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
            return
        }
        if (!normalizedEmpId.matches(Regex("^[A-Z0-9._-]{2,40}$"))) {
            _signUpState.value = AuthUiState.Error("Employee ID must be 2 to 40 letters or digits.")
            return
        }
        if (password.length < 6) {
            _signUpState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _signUpState.value = AuthUiState.Loading
            var photoBytes: ByteArray? = null
            var photoFilename: String? = null
            if (selectedPhotoUri != null) {
                photoBytes = compressImageUri(context, selectedPhotoUri)
                photoFilename = "avatar_${System.currentTimeMillis()}.jpg"
            }

            val result = authRepository.signUpWithEmail(
                email = trimmedEmail,
                password = password,
                employeeId = normalizedEmpId,
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
                    _signUpState.value = AuthUiState.Error(
                        error.message?.ifBlank { "Registration failed. Please try again." }
                            ?: "Registration failed. Please try again."
                    )
                }
            )
        }
    }

    // ----------------------------------------------------
    // Google Sign-In via Credential Manager -> Supabase Auth ID Token
    // ----------------------------------------------------

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            when (val tokenRes = googleAuthManager.requestGoogleIdToken(activity)) {
                is GoogleSignInTokenResult.Cancelled -> {
                    _loginState.value = AuthUiState.Idle
                }
                is GoogleSignInTokenResult.Failure -> {
                    _loginState.value = AuthUiState.Error(tokenRes.message)
                }
                is GoogleSignInTokenResult.Success -> {
                    val authResult = authRepository.signInWithGoogleIdToken(
                        idToken = tokenRes.idToken,
                        rawNonce = tokenRes.rawNonce,
                        displayNameHint = tokenRes.displayName,
                        emailHint = tokenRes.email
                    )
                    authResult.fold(
                        onSuccess = { profile ->
                            _loginState.value = AuthUiState.Success(profile)
                        },
                        onFailure = { error ->
                            val msg = error.message.orEmpty()
                            if (msg.contains("inactive", ignoreCase = true)) {
                                _loginState.value = AuthUiState.AccountInactive
                            } else {
                                _loginState.value = AuthUiState.Error(
                                    msg.ifBlank { "Google authentication failed." }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    fun completeGoogleProfile(
        fullName: String,
        mobile: String,
        employeeId: String
    ) {
        val trimmedName = fullName.trim()
        val normalizedMobile = normalizeMobile(mobile)
        val normalizedEmpId = SupabaseConfig.normalizeEmployeeId(employeeId)

        if (trimmedName.length < 2) {
            _loginState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        if (!normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _loginState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
            return
        }
        if (!normalizedEmpId.matches(Regex("^[A-Z0-9._-]{2,40}$"))) {
            _loginState.value = AuthUiState.Error("Employee ID must be 2 to 40 letters or digits.")
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = authRepository.completeGoogleProfile(
                employeeId = normalizedEmpId,
                fullName = trimmedName,
                mobile = normalizedMobile
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

    fun cancelPendingProfileCompletion() {
        viewModelScope.launch {
            authRepository.cancelPendingProfileCompletion()
            _loginState.value = AuthUiState.Idle
        }
    }

    private suspend fun compressImageUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return@withContext null

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
