package com.iponlove.app.feature.recurring.data.remote

import javax.inject.Inject

/**
 * The Supabase side of recurring-rules sync. A port so the engine never depends on the
 * Supabase SDK directly; the real implementation lands with the backend slice.
 */
interface RecurringRuleRemoteSource {
    suspend fun push(rows: List<RecurringRuleDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<RecurringRuleDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubRecurringRuleRemoteSource @Inject constructor() : RecurringRuleRemoteSource {
    override suspend fun push(rows: List<RecurringRuleDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<RecurringRuleDto> = emptyList()
}
