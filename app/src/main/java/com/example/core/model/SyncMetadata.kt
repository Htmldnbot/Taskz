package com.example.core.model

enum class SyncStatus {
    SYNCED,
    OFFLINE,
    SYNCING,
    PENDING,
    PENDING_VERIFICATION,
    ERROR
}

data class SyncMetadata(
    val lastServerFetch: Long? = null,
    val lastSuccessfulSync: Long? = null,
    val pendingSyncCount: Int = 0,
    val status: SyncStatus = SyncStatus.SYNCED,
    val statusMessage: String? = null
)
