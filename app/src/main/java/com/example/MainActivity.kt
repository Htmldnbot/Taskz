package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.UserPreferencesDataStore
import com.example.notifications.NotificationDeepLinkRouter
import com.example.ui.TaskzViewModelFactory
import com.example.ui.admin.AdminViewModel
import com.example.ui.admin.WorkRolesViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.groups.GroupsViewModel
import com.example.ui.home.HomeViewModel
import com.example.ui.incidents.SystemIncidentViewModel
import com.example.ui.main.MainShell
import com.example.ui.profile.ProfileViewModel
import com.example.ui.shifts.ShiftsViewModel
import com.example.ui.splash.SplashScreen
import com.example.ui.tasks.TasksViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.resolveIsDarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationDeepLinkRouter.handleIntent(intent)

        setContent {
            val prefs = remember { UserPreferencesDataStore(applicationContext) }
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val darkTheme = resolveIsDarkTheme(themeMode, isSystemInDarkTheme())

            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskzAppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationDeepLinkRouter.handleIntent(intent)
    }
}

@Composable
fun TaskzAppNavigation() {
    val navController = rememberNavController()
    val viewModelFactory = TaskzViewModelFactory()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            val splashViewModel: com.example.ui.splash.SplashViewModel = viewModel(factory = viewModelFactory)
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToMain = {
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate("auth") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("auth") {
            val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
            val adminViewModel: AdminViewModel = viewModel(factory = viewModelFactory)
            val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory)
            val groupsViewModel: GroupsViewModel = viewModel(factory = viewModelFactory)
            val shiftsViewModel: ShiftsViewModel = viewModel(factory = viewModelFactory)
            val workRolesViewModel: WorkRolesViewModel = viewModel(factory = viewModelFactory)
            val systemIncidentViewModel: SystemIncidentViewModel = viewModel(factory = viewModelFactory)

            MainShell(
                homeViewModel = homeViewModel,
                profileViewModel = profileViewModel,
                adminViewModel = adminViewModel,
                tasksViewModel = tasksViewModel,
                groupsViewModel = groupsViewModel,
                shiftsViewModel = shiftsViewModel,
                workRolesViewModel = workRolesViewModel,
                systemIncidentViewModel = systemIncidentViewModel,
                onLogoutSuccess = {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}
