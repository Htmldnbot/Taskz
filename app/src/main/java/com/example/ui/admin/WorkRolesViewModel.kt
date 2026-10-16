package com.example.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.WorkRole
import com.example.domain.repository.TaskRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkRolesViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val workRolesFlow: StateFlow<List<WorkRole>> = taskRepository.getWorkRolesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionEvent = MutableSharedFlow<String>()
    val actionEvent: SharedFlow<String> = _actionEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            taskRepository.fetchWorkRoles()
        }
    }

    fun createRole(name: String, code: String?, description: String?, sortOrder: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.createWorkRole(name, code, description, sortOrder)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Work Role Created: ${it.name}")
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to create role")
                }
            )
            _isLoading.value = false
        }
    }

    fun updateRole(id: String, name: String, code: String?, description: String?, isActive: Boolean, sortOrder: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = taskRepository.updateWorkRole(id, name, code, description, isActive, sortOrder)
            res.fold(
                onSuccess = {
                    _actionEvent.emit("Work Role Updated: ${it.name}")
                },
                onFailure = { error ->
                    _actionEvent.emit(error.message ?: "Failed to update role")
                }
            )
            _isLoading.value = false
        }
    }

    fun toggleRoleActive(role: WorkRole) {
        updateRole(
            id = role.id,
            name = role.name,
            code = role.code,
            description = role.description,
            isActive = !role.isActive,
            sortOrder = role.sortOrder
        )
    }
}
