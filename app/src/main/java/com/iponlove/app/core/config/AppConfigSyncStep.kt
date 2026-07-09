package com.iponlove.app.core.config

import com.iponlove.app.core.sync.FullSyncStep
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the remote paywall config (kill-switch + cap overrides) on the foreground / reconnect
 * / WorkManager full sync — the wiring S1 deferred to "the S4 reconcile loop". A [FullSyncStep]
 * (not [core.sync.PreSyncStep][com.iponlove.app.core.sync.PreSyncStep]) so it doesn't re-fetch on
 * every debounced micro-push. [AppConfigRepository.refresh] is itself best-effort (offline leaves
 * the last-known-good cache, or the dormant fallback on a fresh install).
 */
@Singleton
class AppConfigSyncStep @Inject constructor(
    private val repository: AppConfigRepository,
) : FullSyncStep {
    override suspend fun run() = repository.refresh()
}
