package com.iponlove.app.feature.drafts.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [TransactionDraftRemoteSource]. */
class SupabaseTransactionDraftRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : TransactionDraftRemoteSource {

    override suspend fun push(rows: List<TransactionDraftDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from(TABLE).upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<TransactionDraftDto> =
        client.from(TABLE).select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    private companion object {
        const val TABLE = "transaction_drafts"
    }
}
