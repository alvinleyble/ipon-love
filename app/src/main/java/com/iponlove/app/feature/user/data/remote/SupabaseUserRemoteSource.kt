package com.iponlove.app.feature.user.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** Postgrest-backed [UserRemoteSource]. Push is self-only (RLS enforces it). Pull returns
 *  own row + partner row when in a couple (users_select policy gates both). */
class SupabaseUserRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : UserRemoteSource {

    override suspend fun push(rows: List<UserPushDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("users").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun writeEntitlement(write: UserEntitlementWrite) {
        client.postgrest.rpc(
            "set_self_entitlement",
            buildJsonObject {
                put("p_is_premium", write.isPremium)
                put("p_premium_until", write.premiumUntil?.toString())
                put("p_source", write.source)
                put("p_checked_at", write.checkedAt?.toString())
                put("p_updated_at", write.updatedAt.toString())
            },
        )
    }

    override suspend fun pull(cursor: Long, limit: Int): List<UserDto> =
        client.from("users").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun fetchSelf(userId: String): UserDto? =
        client.from("users").select {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<UserDto>().firstOrNull()
}
