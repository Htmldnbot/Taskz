package com.example.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Shared process-wide mutex preventing concurrent outbox dispatch across
 * SyncWorker, TaskzSyncManager, TaskRepository, and SystemIncidentRepository.
 */
object OutboxSyncCoordinator {
    val mutex = Mutex()
}
