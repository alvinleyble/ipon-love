package com.iponlove.app.feature.notes.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject

/** Postgrest-backed [NoteAttachmentRemoteSource]. Storage uploads are handled separately by
 *  [com.iponlove.app.feature.notes.data.upload.NoteAttachmentUploader]. */
class SupabaseNoteAttachmentRemoteSource @Inject constructor(
    private val client: SupabaseClient,
) : NoteAttachmentRemoteSource {

    override suspend fun push(rows: List<NoteAttachmentDto>): List<String> {
        if (rows.isEmpty()) return emptyList()
        client.from("note_images").upsert(rows)
        return rows.map { it.id }
    }

    override suspend fun pull(cursor: Long, limit: Int): List<NoteAttachmentDto> =
        client.from("note_images").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()

    override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerNoteAttachmentDto> =
        client.from("partner_note_images").select {
            filter { gt("server_rev", cursor) }
            order("server_rev", Order.ASCENDING)
            limit(count = limit.toLong())
        }.decodeList()
}
