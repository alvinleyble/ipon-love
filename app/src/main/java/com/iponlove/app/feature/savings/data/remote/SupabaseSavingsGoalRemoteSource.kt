package com.iponlove.app.feature.savings.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [SavingsGoalRemoteSource]. Replaces the stub for real sync. */
class SupabaseSavingsGoalRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : SavingsGoalRemoteSource {

    override suspend fun push(rows: List<SavingsGoalDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("savings_goals").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<SavingsGoalDto> =
        client.from("savings_goals").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerSavingsGoalDto> =
        client.from("partner_savings_goals").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
