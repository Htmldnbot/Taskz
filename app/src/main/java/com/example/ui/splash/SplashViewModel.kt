package com.example.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.UserProfile
import com.example.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SplashNavigationState {
    object Loading : SplashNavigationState
    object NavigateToMain : SplashNavigationState
    object NavigateToAuth : SplashNavigationState
}

class SplashViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navigationState = MutableStateFlow<SplashNavigationState>(SplashNavigationState.Loading)
    val navigationState: StateFlow<SplashNavigationState> = _navigationState.asStateFlow()

    fun checkSession() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val sessionResult = authRepository.restoreSession()

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 500) {
                delay(500 - elapsed)
            }

            sessionResult.fold(
                onSuccess = { profile ->
                    if (profile != null && profile.isActive) {
                        _navigationState.value = SplashNavigationState.NavigateToMain
                    } else {
                        _navigationState.value = SplashNavigationState.NavigateToAuth
                    }
                },
                onFailure = {
                    _navigationState.value = SplashNavigationState.NavigateToAuth
                }
            )
        }
    }
}
