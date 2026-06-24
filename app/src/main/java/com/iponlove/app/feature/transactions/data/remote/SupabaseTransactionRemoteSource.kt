package com.iponlove.app.feature.transactions.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [TransactionRemoteSource]. Replaces [StubTransactionRemoteSource]. */
class SupabaseTransactionRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : TransactionRemoteSource {

    override suspend fun push(rows: List<TransactionDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("transactions").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<TransactionDto> =
        client.from("transactions").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
