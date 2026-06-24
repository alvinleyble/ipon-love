package com.iponlove.app.feature.notes.data.remote

import javax.inject.Inject

/**
 * The Supabase side of notes sync. A port so the engine never depends on the Supabase SDK
 * directly; the real implementation lands with the backend slice.
 */
interface NoteRemoteSource {
    suspend fun push(rows: List<NoteDto>): List<String>
    suspend fun pull(cursor: Long, limit: Int): List<NoteDto>
}

/** No-op remote for offline development — rows stay `pending_sync` until the real backend. */
class StubNoteRemoteSource @Inject constructor() : NoteRemoteSource {
    override suspend fun push(rows: List<NoteDto>): List<String> = emptyList()
    override suspend fun pull(cursor: Long, limit: Int): List<NoteDto> = emptyList()
}
