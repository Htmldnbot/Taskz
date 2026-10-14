package com.example.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface ProfileUpdateState {
    object Idle : ProfileUpdateState
    object Loading : ProfileUpdateState
    object Success : ProfileUpdateState
    data class Error(val message: String) : ProfileUpdateState
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val profileFlow: StateFlow<UserProfile?> = profileRepository.profileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val updateState: StateFlow<ProfileUpdateState> = _updateState.asStateFlow()

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            profileRepository.refreshProfile()
        }
    }

    fun updateProfile(
        context: Context,
        fullName: String,
        mobile: String,
        newPhotoUri: Uri?
    ) {
        val trimmedName = fullName.trim()
        val cleanMobile = mobile.trim().replace(Regex("[^0-9]"), "")

        if (trimmedName.isBlank()) {
            _updateState.value = ProfileUpdateState.Error("Full Name cannot be empty.")
            return
        }
        if (cleanMobile.length < 10) {
            _updateState.value = ProfileUpdateState.Error("Please enter a valid 10-digit mobile number.")
            return
        }

        viewModelScope.launch {
            _updateState.value = ProfileUpdateState.Loading

            var photoBytes: ByteArray? = null
            var photoFilename: String? = null
            if (newPhotoUri != null) {
                photoBytes = compressImageUri(context, newPhotoUri)
                photoFilename = "avatar_${System.currentTimeMillis()}.jpg"
            }

            val result = profileRepository.updateProfile(
                fullName = trimmedName,
                mobile = cleanMobile,
                newPhotoBytes = photoBytes,
                newPhotoFilename = photoFilename
            )

            result.fold(
                onSuccess = {
                    _updateState.value = ProfileUpdateState.Success
                },
                onFailure = { error ->
                    _updateState.value = ProfileUpdateState.Error(error.message ?: "Failed to update profile.")
                }
            )
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoggingOut.value = true
            authRepository.logout()
            _isLoggingOut.value = false
            onComplete()
        }
    }

    fun resetUpdateState() {
        _updateState.value = ProfileUpdateState.Idle
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
