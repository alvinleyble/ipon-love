package com.iponlove.app.feature.notes.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [NoteRemoteSource]. Replaces [StubNoteRemoteSource] for real sync. */
class SupabaseNoteRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : NoteRemoteSource {

    override suspend fun push(rows: List<NoteDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("notes").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<NoteDto> =
        client.from("notes").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
