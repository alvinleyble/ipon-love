package com.iponlove.app.core.analytics

/**
 * The push side of [Analytics], driven by the sync layer (via [AnalyticsSyncStep]). Split from the
 * [Analytics.log] write side so feature code that only records events never sees [flush].
 */
interface AnalyticsFlusher {
    /**
     * Best-effort: push the whole buffer to Supabase, then delete the rows that landed. Offline /
     * transient failure keeps the buffer for the next flush (idempotent upsert by id makes a retry
     * after an uncertain outcome safe).
     */
    suspend fun flush()
}
