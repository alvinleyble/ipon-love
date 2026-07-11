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
 * invisible in normal use (nobody types 50k chars). The paywall's S10 split feeds the
 * entitlement-resolved value (free 5,000 / premium 50,000, paywall doc §10.1 `maxNoteChars`, via
 * `PremiumGate.observeLimit`) into the same counting + trim mechanism — plus [effectiveLimit], the
 * one piece of *new* S10 behaviour, which keeps the split T1-freeze-safe (an existing over-cap note
 * is frozen, never truncated, when the free cap drops below it).
 */
object NoteCharLimit {

    /** Default ceiling = the premium max, used until the entitlement-resolved limit (S10) arrives. */
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

    /**
     * The ceiling the editor actually enforces: the entitlement-resolved [tierLimit], but never
     * below [existingLength] — the length the note already held when it opened. This applies the
     * T1 freeze rule (paywall §10.7: over-cap data stays intact, only new growth is blocked) to
     * note length: a note authored above the free cap (e.g. a 40k note written while premium, now
     * viewed on the free tier after enforcement flips) is frozen at its current length
     * (read-only-for-growth) rather than silently truncated on open. A note within the cap just
     * returns [tierLimit].
     */
    fun effectiveLimit(tierLimit: Int, existingLength: Int): Int = maxOf(tierLimit, existingLength)
}
