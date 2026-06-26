package com.iponlove.app.feature.notes.domain.model

import java.time.Instant

/**
 * A rich-text note. Pure domain model — no `user_id` and no sync columns; those are
 * data-layer concerns owned by the repository.
 *
 * [contentHtml] is the body serialized as HTML by the Compose Rich Editor (the schema's
 * `content` jsonb column; the wire wrapping is settled with the backend slice). Both
 * [title] and [contentHtml] are empty (never null) at the domain boundary — the mapper
 * normalizes the nullable columns to "".
 *
 * [isShared] and [updatedAt] are read-only here: the editor never sets them, and the
 * repository owns the value it writes (sharing lands with the Couples slice; `updated_at`
 * is stamped on every write — ADR-0001). They carry through so the list can show "edited"
 * times and a future shared badge.
 */
data class Note(
    val id: String,
    val title: String,
    val contentHtml: String,
    val isShared: Boolean = false,
    val isConflictCopy: Boolean = false,
    val updatedAt: Instant = Instant.EPOCH,
    /** True when this note belongs to the partner (replicated via partner_notes view). */
    val isPartnerNote: Boolean = false,
)
