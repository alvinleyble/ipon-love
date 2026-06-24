package com.iponlove.app.feature.budgets.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [BudgetRemoteSource]. Replaces [StubBudgetRemoteSource] for real sync. */
class SupabaseBudgetRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : BudgetRemoteSource {

    override suspend fun push(rows: List<BudgetDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("budgets").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<BudgetDto> =
        client.from("budgets").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
