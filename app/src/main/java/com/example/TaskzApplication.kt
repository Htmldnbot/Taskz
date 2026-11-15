package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.network.NetworkMonitor
import com.example.core.network.SupabaseConfig
import com.example.data.local.TaskzDatabase
import com.example.data.remote.GoogleAuthManager
import com.example.data.remote.SupabaseClient
import com.example.data.remote.TaskzRealtimeManager
import com.example.domain.repository.AdminRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import com.example.domain.repository.SyncRepository
import com.example.domain.repository.SystemIncidentRepository
import com.example.domain.repository.TaskRepository
import com.example.domain.security.TaskzPermissionManager
import com.example.notifications.DeviceTokenManager
import com.example.notifications.PushNotificationManager
import com.example.sync.TaskzSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TaskzApplication : Application() {

    lateinit var database: TaskzDatabase
        private set

    lateinit var userPreferences: UserPreferencesDataStore
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var supabaseClient: SupabaseClient
        private set

    lateinit var googleAuthManager: GoogleAuthManager
        private set

    lateinit var permissionManager: TaskzPermissionManager
        private set

    lateinit var realtimeManager: TaskzRealtimeManager
        private set

    lateinit var deviceTokenManager: DeviceTokenManager
        private set

    lateinit var pushNotificationManager: PushNotificationManager
        private set

    lateinit var taskzSyncManager: TaskzSyncManager
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var syncRepository: SyncRepository
        private set

    lateinit var adminRepository: AdminRepository
        private set

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var systemIncidentRepository: SystemIncidentRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize local storage, networking & security core
        database = TaskzDatabase.getInstance(this)
        userPreferences = UserPreferencesDataStore(this)
        networkMonitor = NetworkMonitor(this)
        supabaseClient = SupabaseClient()
        googleAuthManager = GoogleAuthManager()
        permissionManager = TaskzPermissionManager()
        realtimeManager = TaskzRealtimeManager()
        deviceTokenManager = DeviceTokenManager(this, userPreferences, supabaseClient)
        pushNotificationManager = PushNotificationManager(this, database.authorizationDao())

        // 2. Initialize Repositories
        authRepository = AuthRepository(
            supabaseClient = supabaseClient,
            profileDao = database.profileDao(),
            syncMetadataDao = database.syncMetadataDao(),
            userPrefs = userPreferences
        )

        profileRepository = ProfileRepository(
            supabaseClient = supabaseClient,
            profileDao = database.profileDao(),
            userPrefs = userPreferences
        )

        syncRepository = SyncRepository(
            supabaseClient = supabaseClient,
            syncMetadataDao = database.syncMetadataDao(),
            outboxDao = database.outboxDao(),
            profileDao = database.profileDao(),
            userPrefs = userPreferences,
            networkMonitor = networkMonitor
        )

        adminRepository = AdminRepository(
            supabaseClient = supabaseClient,
            userPrefs = userPreferences
        )

        // Initialize notification channels for alarms, advance reminders & role-scoped push alerts
        com.example.alarm.TaskAlarmReceiver.createNotificationChannels(this)
        pushNotificationManager.ensureChannelsCreated()

        taskRepository = TaskRepository(
            taskDao = database.taskDao(),
            taskGroupDao = database.taskGroupDao(),
            shiftDao = database.shiftDao(),
            workRoleDao = database.workRoleDao(),
            profileDao = database.profileDao(),
            taskResponseDao = database.taskResponseDao(),
            scheduledAlarmDao = database.scheduledAlarmDao(),
            supabaseClient = supabaseClient,
            userPrefs = userPreferences,
            context = this
        )

        systemIncidentRepository = SystemIncidentRepository(
            systemIncidentDao = database.systemIncidentDao(),
            taskGroupDao = database.taskGroupDao(),
            shiftDao = database.shiftDao(),
            outboxDao = database.outboxDao(),
            supabaseClient = supabaseClient,
            userPrefs = userPreferences,
            networkMonitor = networkMonitor,
            context = this
        )

        taskzSyncManager = TaskzSyncManager(
            context = this,
            database = database,
            userPrefs = userPreferences,
            networkMonitor = networkMonitor,
            supabaseClient = supabaseClient,
            authRepository = authRepository,
            taskRepository = taskRepository,
            systemIncidentRepository = systemIncidentRepository,
            permissionManager = permissionManager,
            realtimeManager = realtimeManager,
            deviceTokenManager = deviceTokenManager,
            pushNotificationManager = pushNotificationManager
        )

        // 3. Track foreground / background transitions cleanly without polling
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                val wasBackground = startedActivityCount == 0
                startedActivityCount += 1
                if (wasBackground) {
                    taskzSyncManager.onAppForegrounded()
                }
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    taskzSyncManager.onAppBackgrounded()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 4. Load custom config & start unified event-driven sync coordinator
        applicationScope.launch {
            val custom = userPreferences.customConfigFlow.first()
            if (!custom.first.isNullOrBlank()) {
                SupabaseConfig.runtimeUrl = custom.first
            }
            if (!custom.second.isNullOrBlank()) {
                SupabaseConfig.runtimeAnonKey = custom.second
            }
            syncRepository.initializeSyncState()
            taskzSyncManager.start(applicationScope)
        }

        // Schedule low-frequency WorkManager periodic fallback sync
        try {
            com.example.sync.SyncWorker.schedulePeriodicSync(this)
        } catch (_: Exception) {}
    }

    companion object {
        lateinit var instance: TaskzApplication
            private set
    }
}
