package com.iponlove.app.feature.notes.data.remote

/** Supabase side of `note_images` sync — Postgrest row metadata only (no Storage calls). */
interface NoteAttachmentRemoteSource {
    suspend fun push(rows: List<NoteAttachmentDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<NoteAttachmentDto>
    suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerNoteAttachmentDto>
}
