package com.iponlove.app.feature.categories.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [CategoryRemoteSource]. Replaces [StubCategoryRemoteSource] for real sync. */
class SupabaseCategoryRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : CategoryRemoteSource {

    override suspend fun push(rows: List<CategoryDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("categories").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<CategoryDto> =
        client.from("categories").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerCategoryDto> =
        client.from("partner_categories").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
