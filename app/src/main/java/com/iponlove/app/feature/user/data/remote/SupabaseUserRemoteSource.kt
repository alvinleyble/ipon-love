package com.iponlove.app.feature.user.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [UserRemoteSource]. Push is self-only (RLS enforces it). Pull returns
 *  own row + partner row when in a couple (users_select policy gates both). */
class SupabaseUserRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : UserRemoteSource {

    override suspend fun push(rows: List<UserDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("users").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<UserDto> =
        client.from("users").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
