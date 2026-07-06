package com.iponlove.app.feature.transactions.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [TransactionImageRemoteSource]. Storage uploads are handled separately by
 *  [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader]. */
class SupabaseTransactionImageRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : TransactionImageRemoteSource {

    override suspend fun push(rows: List<TransactionImageDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("transaction_images").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<TransactionImageDto> =
        client.from("transaction_images").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerTransactionImageDto> =
        client.from("partner_transaction_images").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
