package com.iponlove.app.feature.notifications.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [NotificationRemoteSource]. */
class SupabaseNotificationRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : NotificationRemoteSource {

    override suspend fun push(rows: List<NotificationDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from(TABLE).upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<NotificationDto> =
        client.from(TABLE).select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun delete(ids: List<String>) {
        if (ids.isEmpty()) return
        client.from(TABLE).delete { filter { isIn("id", ids) } }
    }

    private companion object {
        const val TABLE = "notifications"
    }
}
