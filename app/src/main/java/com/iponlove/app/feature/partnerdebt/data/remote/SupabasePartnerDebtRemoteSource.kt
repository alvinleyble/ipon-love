package com.iponlove.app.feature.partnerdebt.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [PartnerDebtRemoteSource]. */
class SupabasePartnerDebtRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : PartnerDebtRemoteSource {

    override suspend fun push(rows: List<PartnerDebtDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("partner_debts").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<PartnerDebtDto> =
        client.from("partner_debts").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
