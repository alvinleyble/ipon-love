package com.iponlove.app.core.analytics

import com.iponlove.app.core.sync.FullSyncStep
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flushes the buffered telemetry on the foreground / reconnect / WorkManager full sync (G10). A
 * [FullSyncStep] (not a [core.sync.PreSyncStep][com.iponlove.app.core.sync.PreSyncStep]) so the
 * extra Supabase round-trip batches on a real sync instead of firing on every keystroke-debounced
 * micro-push. The engine swallows a failure here so it can never abort the data sync that follows.
 */
@Singleton
class AnalyticsSyncStep @Inject constructor(
    private val flusher: AnalyticsFlusher,
) : FullSyncStep {
    override suspend fun run() = flusher.flush()
}
