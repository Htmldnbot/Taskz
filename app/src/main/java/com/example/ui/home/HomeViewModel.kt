package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.SyncMetadata
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import com.example.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val syncRepository: SyncRepository
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    private val _isAccountInactive = MutableStateFlow(false)
    val isAccountInactive: StateFlow<Boolean> = _isAccountInactive.asStateFlow()

    init {
        // Initial silent validation on entering home
        triggerManualRefresh()
    }

    fun triggerManualRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncErrorMessage.value = null

            val result = syncRepository.manualSync()
            result.fold(
                onSuccess = {
                    _isRefreshing.value = false
                },
                onFailure = { error ->
                    _isRefreshing.value = false
                    val msg = error.message.orEmpty()
                    if (msg.contains("inactive", ignoreCase = true)) {
                        _isAccountInactive.value = true
                    } else {
                        _syncErrorMessage.value = msg.ifBlank { "Sync failed" }
                    }
                }
            )
        }
    }

    fun clearSyncError() {
        _syncErrorMessage.value = null
    }

    fun handleAccountInactiveDismissed(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            _isAccountInactive.value = false
            onSignedOut()
        }
    }
}
