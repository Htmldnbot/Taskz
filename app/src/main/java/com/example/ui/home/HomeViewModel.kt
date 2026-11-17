package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TaskzApplication
import com.example.core.model.AuthorizationContext
import com.example.core.model.DashboardStats
import com.example.core.model.ScopedDashboardSummary
import com.example.core.model.SyncMetadata
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import com.example.domain.repository.SyncRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.security.TaskzPermissionManager
import com.example.sync.TaskzSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val syncRepository: SyncRepository,
    private val taskRepository: TaskRepository,
    private val taskzSyncManager: TaskzSyncManager = TaskzApplication.instance.taskzSyncManager,
    val permissionManager: TaskzPermissionManager = TaskzApplication.instance.permissionManager
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

    val authContextFlow: StateFlow<AuthorizationContext> = permissionManager.contextFlow

    val orgHierarchyFlow: StateFlow<com.example.core.model.OrgHierarchyData> = taskzSyncManager.orgHierarchy

    val scopedSummaryFlow: StateFlow<ScopedDashboardSummary> = taskzSyncManager.scopedDashboardSummary

    val syncManagerSyncing: StateFlow<Boolean> = taskzSyncManager.isSyncing

    val unreadNotificationsCount: StateFlow<Int> = taskzSyncManager.unreadNotificationCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    private val _isAccountInactive = MutableStateFlow(false)
    val isAccountInactive: StateFlow<Boolean> = _isAccountInactive.asStateFlow()

    private val _isSavingLocation = MutableStateFlow(false)
    val isSavingLocation: StateFlow<Boolean> = _isSavingLocation.asStateFlow()

    private val _locationSaveError = MutableStateFlow<String?>(null)
    val locationSaveError: StateFlow<String?> = _locationSaveError.asStateFlow()

    init {
        // Automatic silent sync & authorization validation on entering home
        triggerAutomaticRefresh()
        loadDashboardStats()
    }

    fun ensureOrgHierarchyLoaded() {
        viewModelScope.launch {
            if (taskzSyncManager.orgHierarchy.value.cities.isEmpty()) {
                taskzSyncManager.refreshOrgHierarchy()
            }
        }
    }

    fun updateOperationalLocation(
        roleCode: String?,
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
                lanNumber = lanNumber
            )
            _isSavingLocation.value = false
            res.fold(
                onSuccess = { ctx ->
                    loadDashboardStats()
                    onSuccess(ctx)
                },
                onFailure = { err ->
                    _locationSaveError.value = err.message ?: "Failed to save duty station on server"
                }
            )
        }
    }

    fun clearLocationError() {
        _locationSaveError.value = null
    }

    fun loadDashboardStats() {
        viewModelScope.launch {
            _dashboardStats.value = taskRepository.fetchDashboardStats()
        }
    }

    fun triggerAutomaticRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncErrorMessage.value = null

            val result = taskzSyncManager.syncNow(forceFull = false)
            result.fold(
                onSuccess = {
                    _isRefreshing.value = false
                    loadDashboardStats()
                },
                onFailure = { error ->
                    _isRefreshing.value = false
                    val msg = error.message.orEmpty()
                    if (msg.contains("inactive", ignoreCase = true)) {
                        _isAccountInactive.value = true
                    } else if (msg.isNotBlank() && !msg.contains("Offline", ignoreCase = true)) {
                        _syncErrorMessage.value = msg
                    }
                }
            )
        }
    }

    fun triggerManualRefresh() = triggerAutomaticRefresh()

    fun retryPendingSync() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncErrorMessage.value = null
            val result = syncRepository.retryStuckAndFailedEvents()
            result.fold(
                onSuccess = {
                    taskzSyncManager.syncNow(forceFull = false)
                    _isRefreshing.value = false
                    loadDashboardStats()
                },
                onFailure = { error ->
                    _isRefreshing.value = false
                    val msg = error.message.orEmpty()
                    if (msg.contains("inactive", ignoreCase = true)) {
                        _isAccountInactive.value = true
                    } else {
                        _syncErrorMessage.value = msg.ifBlank { "Sync retry failed" }
                    }
                }
            )
        }
    }

    fun clearFailedSyncItems() {
        viewModelScope.launch {
            syncRepository.clearPermanentFailedEvents()
            _syncErrorMessage.value = null
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
