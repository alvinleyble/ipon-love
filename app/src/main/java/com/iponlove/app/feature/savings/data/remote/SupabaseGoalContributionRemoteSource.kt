package com.iponlove.app.feature.savings.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [GoalContributionRemoteSource]. Replaces the stub for real sync. */
class SupabaseGoalContributionRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : GoalContributionRemoteSource {

    override suspend fun push(rows: List<GoalContributionDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("goal_contributions").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<GoalContributionDto> =
        client.from("goal_contributions").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerGoalContributionDto> =
        client.from("partner_goal_contributions").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
