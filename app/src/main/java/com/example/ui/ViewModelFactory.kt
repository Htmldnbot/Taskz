package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.TaskzApplication
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.WorkRolesViewModel
import com.example.ui.auth.AuthViewModel
import com.example.ui.groups.GroupsViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.incidents.SystemIncidentViewModel
import com.example.ui.profile.ProfileViewModel
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.splash.SplashViewModel
import com.example.ui.tasks.TasksViewModel

@Suppress("UNCHECKED_CAST")
class TaskzViewModelFactory(
    private val app: TaskzApplication = TaskzApplication.instance
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SplashViewModel::class.java) -> {
                SplashViewModel(app.authRepository) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(app.authRepository, app.googleAuthManager) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    authRepository = app.authRepository,
                    profileRepository = app.profileRepository,
                    syncRepository = app.syncRepository,
                    taskRepository = app.taskRepository,
                    taskzSyncManager = app.taskzSyncManager,
                    permissionManager = app.permissionManager
                ) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(
                    profileRepository = app.profileRepository,
                    authRepository = app.authRepository,
                    taskzSyncManager = app.taskzSyncManager,
                    permissionManager = app.permissionManager,
                    deviceTokenManager = app.deviceTokenManager
                ) as T
            }
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> {
                AdminViewModel(
                    adminRepository = app.adminRepository,
                    taskzSyncManager = app.taskzSyncManager
                ) as T
            }
            modelClass.isAssignableFrom(TasksViewModel::class.java) -> {
                TasksViewModel(
                    taskRepository = app.taskRepository,
                    adminRepository = app.adminRepository,
                    permissionManager = app.permissionManager,
                    taskzSyncManager = app.taskzSyncManager
                ) as T
            }
            modelClass.isAssignableFrom(GroupsViewModel::class.java) -> {
                GroupsViewModel(app.taskRepository, app.adminRepository, app.permissionManager) as T
            }
            modelClass.isAssignableFrom(ShiftsViewModel::class.java) -> {
                ShiftsViewModel(app.taskRepository, app.permissionManager) as T
            }
            modelClass.isAssignableFrom(WorkRolesViewModel::class.java) -> {
                WorkRolesViewModel(app.taskRepository) as T
            }
            modelClass.isAssignableFrom(SystemIncidentViewModel::class.java) -> {
                SystemIncidentViewModel(
                    repository = app.systemIncidentRepository,
                    permissionManager = app.permissionManager
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
