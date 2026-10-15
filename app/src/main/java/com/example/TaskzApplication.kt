package com.example

import android.app.Application
import com.example.core.datastore.UserPreferencesDataStore
import com.example.core.network.NetworkMonitor
import com.example.core.network.SupabaseConfig
import com.example.data.local.TaskzDatabase
import com.example.data.remote.SupabaseClient
import com.example.domain.repository.AdminRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ProfileRepository
import com.example.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    lateinit var authRepository: AuthRepository
        private set

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var syncRepository: SyncRepository
        private set

    lateinit var adminRepository: AdminRepository
        private set

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize local storage & network monitor
        database = TaskzDatabase.getInstance(this)
        userPreferences = UserPreferencesDataStore(this)
        networkMonitor = NetworkMonitor(this)
        supabaseClient = SupabaseClient()

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

        // 3. Load custom config & initialize sync state in background
        applicationScope.launch {
            val custom = userPreferences.customConfigFlow.first()
            if (!custom.first.isNullOrBlank()) {
                SupabaseConfig.runtimeUrl = custom.first
            }
            if (!custom.second.isNullOrBlank()) {
                SupabaseConfig.runtimeAnonKey = custom.second
            }
            // Initialize sync metadata table
            syncRepository.initializeSyncState()
        }
    }

    companion object {
        lateinit var instance: TaskzApplication
            private set
    }
}
