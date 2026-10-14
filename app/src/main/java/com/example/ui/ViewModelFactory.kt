package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.TaskzApplication
import com.example.ui.admin.AdminViewModel
import com.example.ui.auth.AuthViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.profile.ProfileViewModel

@Suppress("UNCHECKED_CAST")
class TaskzViewModelFactory(
    private val app: TaskzApplication = TaskzApplication.instance
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(app.authRepository) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(app.authRepository, app.profileRepository, app.syncRepository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(app.profileRepository, app.authRepository) as T
            }
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> {
                AdminViewModel(app.adminRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
