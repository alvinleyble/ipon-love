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

    /** Mark note as shared with the couple; partner will see it after next sync. */
    suspend fun shareNote(id: String, coupleId: String)

    /**
     * Pin/unpin a note so it hoists into the "Pinned" section (ADR-0040). Synced by LWW
     * like any other column; pinning a shared note propagates the pin to the partner's copy.
     */
    suspend fun setPinned(id: String, isPinned: Boolean)

    /**
     * Unshare a note. Sets `is_shared = false` but RETAINS `couple_id` so the un-share
     * transition still reaches the partner's redacting view (ADR-0005).
     */
    suspend fun unshareNote(id: String)

    /** Hard-delete all replicated partner notes on unpair (ADR-0008). */
    suspend fun purgePartnerData()
}
