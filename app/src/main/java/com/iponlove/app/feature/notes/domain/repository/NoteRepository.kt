package com.iponlove.app.feature.notes.domain.repository

import com.iponlove.app.feature.notes.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Notes source of truth (Room-backed). Every write funnels through here so `updated_at`
 * stamping (ADR-0001), `pending_sync` (ADR-0002) and soft delete (ADR-0010) are applied
 * in one place.
 */
interface NoteRepository {

    /** Active (non-deleted) notes, most-recently-edited first. */
    fun observeNotes(): Flow<List<Note>>

    suspend fun getNote(id: String): Note?

    /** Create or edit a note; the repository owns `updated_at`, ownership and provenance. */
    suspend fun upsertNote(note: Note)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteNote(id: String)
}
