package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.PasswordResetRequest
import com.example.core.model.UserProfile
import com.example.domain.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminActionState {
    object Idle : AdminActionState
    object Loading : AdminActionState
    data class Success(val message: String) : AdminActionState
    data class Error(val message: String) : AdminActionState
}

class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users: StateFlow<List<UserProfile>> = _users.asStateFlow()

    private val _resetRequests = MutableStateFlow<List<PasswordResetRequest>>(emptyList())
    val resetRequests: StateFlow<List<PasswordResetRequest>> = _resetRequests.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(false)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()

    private val _isLoadingRequests = MutableStateFlow(false)
    val isLoadingRequests: StateFlow<Boolean> = _isLoadingRequests.asStateFlow()

    private val _actionState = MutableStateFlow<AdminActionState>(AdminActionState.Idle)
    val actionState: StateFlow<AdminActionState> = _actionState.asStateFlow()

    init {
        loadAllUsers()
        loadResetRequests()
    }

    fun loadAllUsers() {
        viewModelScope.launch {
            _isLoadingUsers.value = true
            val res = adminRepository.getAllUsers()
            _isLoadingUsers.value = false
            res.onSuccess { _users.value = it }
        }
    }

    fun loadResetRequests() {
        viewModelScope.launch {
            _isLoadingRequests.value = true
            val res = adminRepository.getPasswordResetRequests()
            _isLoadingRequests.value = false
            res.onSuccess { _resetRequests.value = it }
        }
    }

    fun toggleUserStatus(profile: UserProfile) {
        val newStatus = !profile.isActive
        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.toggleUserActiveStatus(profile.id, newStatus)
            res.fold(
                onSuccess = {
                    _actionState.value = AdminActionState.Success(
                        "Employee ${profile.employeeId} is now ${if (newStatus) "Active" else "Inactive"}"
                    )
                    loadAllUsers()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Failed to update status")
                }
            )
        }
    }

    fun executeResetPassword(
        requestId: String?,
        employeeId: String,
        temporaryPassword: String
    ) {
        if (temporaryPassword.length < 6) {
            _actionState.value = AdminActionState.Error("Temporary password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _actionState.value = AdminActionState.Loading
            val res = adminRepository.resetEmployeePassword(
                requestId = requestId,
                employeeId = employeeId,
                newTemporaryPassword = temporaryPassword
            )
            res.fold(
                onSuccess = { msg ->
                    _actionState.value = AdminActionState.Success(msg)
                    loadResetRequests()
                    loadAllUsers()
                },
                onFailure = { err ->
                    _actionState.value = AdminActionState.Error(err.message ?: "Password reset failed")
                }
            )
        }
    }

    fun clearActionState() {
        _actionState.value = AdminActionState.Idle
    }
}
