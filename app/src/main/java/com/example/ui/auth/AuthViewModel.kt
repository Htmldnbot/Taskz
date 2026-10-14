package com.example.ui.auth

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

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val loginState: StateFlow<AuthUiState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val signUpState: StateFlow<AuthUiState> = _signUpState.asStateFlow()

    private val _resetRequestState = MutableStateFlow<ResetRequestUiState>(ResetRequestUiState.Idle)
    val resetRequestState: StateFlow<ResetRequestUiState> = _resetRequestState.asStateFlow()

    fun login(employeeId: String, password: String) {
        val trimmedEmpId = employeeId.trim()
        if (trimmedEmpId.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your Employee ID.")
            return
        }
        if (password.isBlank()) {
            _loginState.value = AuthUiState.Error("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _loginState.value = AuthUiState.Loading
            val result = authRepository.login(trimmedEmpId, password)
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
        val trimmedEmpId = employeeId.trim()
        val trimmedName = fullName.trim()
        val trimmedMobile = mobile.trim()

        // Validation
        if (trimmedEmpId.length < 3) {
            _signUpState.value = AuthUiState.Error("Employee ID must be at least 3 characters.")
            return
        }
        if (trimmedName.isBlank()) {
            _signUpState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        val cleanMobile = trimmedMobile.replace(Regex("[^0-9]"), "")
        if (cleanMobile.length < 10) {
            _signUpState.value = AuthUiState.Error("Please enter a valid 10-digit mobile number.")
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
                employeeId = trimmedEmpId,
                password = password,
                fullName = trimmedName,
                mobile = cleanMobile,
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
