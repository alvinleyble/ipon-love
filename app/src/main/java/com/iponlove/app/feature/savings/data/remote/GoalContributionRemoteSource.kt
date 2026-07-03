package com.iponlove.app.feature.savings.data.remote

import javax.inject.Inject

/** The Supabase side of goal-contribution sync. A port so the engine never depends on the SDK. */
interface GoalContributionRemoteSource {
    suspend fun push(rows: List<GoalContributionDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<GoalContributionDto>

    /** Pull the partner's contributions from the `partner_goal_contributions` view (ADR-0005). */
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerGoalContributionDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubGoalContributionRemoteSource @Inject constructor() : GoalContributionRemoteSource {
    override suspend fun push(rows: List<GoalContributionDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<GoalContributionDto> = emptyList()
    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerGoalContributionDto> =
        emptyList()
}
