package com.example.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AuthorizationContext
import com.example.core.model.SyncMetadata
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import com.example.domain.repository.SyncRepository
import com.example.domain.security.TaskzPermissionManager
import com.example.notifications.DeviceTokenManager
import com.example.sync.TaskzSyncManager
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
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository = TaskzApplication.instance.syncRepository,
    private val taskzSyncManager: TaskzSyncManager = TaskzApplication.instance.taskzSyncManager,
    private val permissionManager: TaskzPermissionManager = TaskzApplication.instance.permissionManager,
    private val deviceTokenManager: DeviceTokenManager = TaskzApplication.instance.deviceTokenManager
) : ViewModel() {

    val profileFlow: StateFlow<UserProfile?> = profileRepository.profileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val syncMetadataFlow: StateFlow<SyncMetadata> = syncRepository.syncMetadataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncMetadata()
        )

    val syncManagerSyncing: StateFlow<Boolean> = taskzSyncManager.isSyncing

    private val _isRefreshingSync = MutableStateFlow(false)
    val isRefreshingSync: StateFlow<Boolean> = _isRefreshingSync.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    val authContextFlow: StateFlow<AuthorizationContext> = permissionManager.contextFlow

    val orgHierarchyFlow: StateFlow<com.example.core.model.OrgHierarchyData> = taskzSyncManager.orgHierarchy

    private val _updateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val updateState: StateFlow<ProfileUpdateState> = _updateState.asStateFlow()

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    private val _isSwitchingRole = MutableStateFlow(false)
    val isSwitchingRole: StateFlow<Boolean> = _isSwitchingRole.asStateFlow()

    private val _isSavingLocation = MutableStateFlow(false)
    val isSavingLocation: StateFlow<Boolean> = _isSavingLocation.asStateFlow()

    private val _locationSaveError = MutableStateFlow<String?>(null)
    val locationSaveError: StateFlow<String?> = _locationSaveError.asStateFlow()

    private val _isTogglingLeave = MutableStateFlow(false)
    val isTogglingLeave: StateFlow<Boolean> = _isTogglingLeave.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceId: StateFlow<String?> = _deviceId.asStateFlow()

    init {
        viewModelScope.launch {
            _deviceId.value = runCatching { deviceTokenManager.getDeviceId() }.getOrNull()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            profileRepository.refreshProfile()
            taskzSyncManager.syncNow(forceFull = false)
            _deviceId.value = runCatching { deviceTokenManager.getDeviceId() }.getOrNull()
        }
    }

    fun triggerManualSync() {
        if (_isRefreshingSync.value) return
        viewModelScope.launch {
            _isRefreshingSync.value = true
            _syncErrorMessage.value = null
            val res = taskzSyncManager.syncNow(forceFull = false)
            _isRefreshingSync.value = false
            res.onFailure { err ->
                val msg = err.message.orEmpty()
                if (msg.isNotBlank() && !msg.contains("Offline", ignoreCase = true)) {
                    _syncErrorMessage.value = msg
                }
            }
        }
    }

    fun clearFailedSyncItems() {
        viewModelScope.launch {
            syncRepository.clearPermanentFailedEvents()
            _syncErrorMessage.value = null
        }
    }

    fun switchActiveRole(roleCode: String) {
        if (_isSwitchingRole.value) return
        viewModelScope.launch {
            _isSwitchingRole.value = true
            val res = taskzSyncManager.switchActiveRole(roleCode)
            _isSwitchingRole.value = false
            res.onFailure { err ->
                _updateState.value = ProfileUpdateState.Error(err.message ?: "Failed to switch active role")
            }
        }
    }

    fun toggleLeaveStatus(isOnLeave: Boolean) {
        if (_isTogglingLeave.value) return
        viewModelScope.launch {
            _isTogglingLeave.value = true
            val res = taskzSyncManager.updateLeaveStatus(isOnLeave)
            _isTogglingLeave.value = false
            res.onFailure { err ->
                _updateState.value = ProfileUpdateState.Error(err.message ?: "Failed to update leave status")
            }
        }
    }

    fun updateOperationalLocation(
        roleCode: String?,
        zoneId: String? = null,
        stateId: String?,
        cityId: String?,
        centreId: String?,
        centreCode: String?,
        lanNumber: String?,
        onSuccess: (AuthorizationContext) -> Unit
    ) {
        if (_isSavingLocation.value) return
        viewModelScope.launch {
            _isSavingLocation.value = true
            _locationSaveError.value = null
            val res = taskzSyncManager.updateOperationalLocation(
                roleCode = roleCode,
                stateId = stateId,
                cityId = cityId,
                centreId = centreId,
                centreCode = centreCode,
                lanNumber = lanNumber,
                zoneId = zoneId
            )
            _isSavingLocation.value = false
            res.fold(
                onSuccess = { ctx -> onSuccess(ctx) },
                onFailure = { err ->
                    _locationSaveError.value = err.message ?: "Failed to update location scope"
                }
            )
        }
    }

    fun clearLocationError() {
        _locationSaveError.value = null
    }

    fun updateProfile(
        context: Context,
        fullName: String,
        mobile: String,
        newPhotoUri: Uri?
    ) {
        val trimmedName = fullName.trim()
        val normalizedMobile = com.example.ui.auth.AuthViewModel.normalizeMobile(mobile)
        val mobileDigits = normalizedMobile.filter { it.isDigit() }

        if (trimmedName.length < 2) {
            _updateState.value = ProfileUpdateState.Error("Full Name must be at least 2 characters.")
            return
        }
        if (mobileDigits.length < 10 || !normalizedMobile.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
            _updateState.value = ProfileUpdateState.Error("Please enter a valid 10-digit mobile number (e.g., +919876543210 or 9876543210).")
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
                mobile = normalizedMobile,
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
