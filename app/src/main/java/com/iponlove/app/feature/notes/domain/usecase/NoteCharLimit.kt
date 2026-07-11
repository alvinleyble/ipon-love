package com.iponlove.app.feature.notes.domain.usecase

/**
 * Character-limit math for the note editor. Pure (no Android imports) so the boundary logic stays
 * JVM-unit-testable; the editor wiring that consumes it (live count via the rich-text state,
 * counter UI, overflow trim) is verified by running.
 *
 * The count is the reader-visible body length — what the editor's `annotatedString.text` reports,
 * i.e. exactly what a "characters" counter should show the user (WYSIWYG), not the serialized HTML.
 *
 * Ships dormant: [DEFAULT_MAX] is the *premium* ceiling, so the base behaviour is effectively
 * invisible in normal use (nobody types 50k chars). The paywall's S10 split swaps this constant for
 * the entitlement-resolved value (free 5,000 / premium 50,000, paywall doc §10.1 `maxNoteChars`) —
 * the counting + trim mechanism here is unchanged; only the limit fed in changes.
 */
object NoteCharLimit {

    /** Base ceiling = the premium max. S10 replaces this per-tier via the entitlement infra. */
    const val DEFAULT_MAX = 50_000

    /** Show the live counter only once the body is within this many chars of the cap. */
    const val COUNTER_THRESHOLD = 500

    /** True once the counter should be surfaced (approaching or past [limit]). */
    fun shouldShowCounter(length: Int, limit: Int): Boolean = length >= limit - COUNTER_THRESHOLD

    /** True when the body has exceeded [limit] (drives the error styling on the counter). */
    fun isOver(length: Int, limit: Int): Boolean = length > limit

    /**
     * How many characters an over-cap edit overshot [limit] by — the count to strip from the just-
     * inserted text at the cursor to snap the body back to exactly [limit]. 0 when within the cap.
     */
    fun overflow(length: Int, limit: Int): Int = (length - limit).coerceAtLeast(0)
}
