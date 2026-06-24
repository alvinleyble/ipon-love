package com.iponlove.app.feature.couple.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Postgrest-backed [CoupleRemoteSource]. Push is a no-op in practice (couples are RPC-only
 * writes), but kept for the uniform syncer contract. Pull returns the caller's couple via
 * the `couples_select` policy. The four pairing mutations call the matching SECURITY
 * DEFINER RPCs, each returning its scalar result decoded straight off the wire.
 */
class SupabaseCoupleRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : CoupleRemoteSource {

    override suspend fun push(rows: List<CoupleDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("couples").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<CoupleDto> =
        client.from("couples").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun createCouple(name: String): String =
        client.postgrest.rpc("create_couple", buildJsonObject { put("p_name", name) })
            .decodeAs()

    override suspend fun redeemInvite(code: String): String =
        client.postgrest.rpc("redeem_invite", buildJsonObject { put("p_code", code) })
            .decodeAs()

    override suspend fun rotateInviteCode(): String =
        client.postgrest.rpc("rotate_invite_code").decodeAs()

    override suspend fun unpair() {
        client.postgrest.rpc("unpair")
    }
}
