package com.iponlove.app.feature.savings.data.remote

import javax.inject.Inject

/** The Supabase side of savings-goal sync. A port so the engine never depends on the SDK. */
interface SavingsGoalRemoteSource {
    suspend fun push(rows: List<SavingsGoalDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<SavingsGoalDto>

    /** Pull the partner's shared goals from the `partner_savings_goals` redacting view (ADR-0005). */
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerSavingsGoalDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubSavingsGoalRemoteSource @Inject constructor() : SavingsGoalRemoteSource {
    override suspend fun push(rows: List<SavingsGoalDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<SavingsGoalDto> = emptyList()
    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerSavingsGoalDto> =
        emptyList()
}
