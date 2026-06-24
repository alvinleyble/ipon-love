package com.iponlove.app.feature.recurring.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [RecurringRuleRemoteSource]. Replaces [StubRecurringRuleRemoteSource]. */
class SupabaseRecurringRuleRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : RecurringRuleRemoteSource {

    override suspend fun push(rows: List<RecurringRuleDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("recurring_rules").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<RecurringRuleDto> =
        client.from("recurring_rules").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
