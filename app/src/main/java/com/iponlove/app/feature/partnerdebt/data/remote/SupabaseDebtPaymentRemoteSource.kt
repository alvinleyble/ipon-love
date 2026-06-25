package com.iponlove.app.feature.partnerdebt.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [DebtPaymentRemoteSource]. */
class SupabaseDebtPaymentRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : DebtPaymentRemoteSource {

    override suspend fun push(rows: List<DebtPaymentDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("partner_debt_payments").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<DebtPaymentDto> =
        client.from("partner_debt_payments").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
