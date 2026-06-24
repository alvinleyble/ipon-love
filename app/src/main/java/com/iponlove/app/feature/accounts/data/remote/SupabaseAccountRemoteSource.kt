package com.iponlove.app.feature.accounts.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [AccountRemoteSource]. Replaces [StubAccountRemoteSource] for real sync. */
class SupabaseAccountRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : AccountRemoteSource {

    override suspend fun push(rows: List<AccountDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("accounts").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<AccountDto> =
        client.from("accounts").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerAccountDto> =
        client.from("partner_accounts").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
