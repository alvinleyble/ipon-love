package com.iponlove.app.feature.notes.data.remote

import java.time.Instant

/**
 * Wire shape of a `notes` row for Supabase. Omits `pendingSync` (local-only, ADR-0002).
 * Serialization annotations arrive with the Supabase slice.
 */
data class NoteDto(
    val id: String,
    val userId: String,
    val title: String?,
    val content: String?,
    val isShared: Boolean,
    val coupleId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDeleted: Boolean,
    val serverRev: Long?,
)
