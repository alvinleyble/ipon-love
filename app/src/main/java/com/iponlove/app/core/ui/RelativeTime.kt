package com.iponlove.app.core.ui

import java.time.Duration
import java.time.Instant

/**
 * Compact "X ago" label for the Settings sync card (v1.6.5 Item 9). Coarse on purpose —
 * sync freshness only needs minute resolution, and the label is recomputed on each
 * recomposition, not ticked live.
 */
fun relativeTimeLabel(at: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(at, now).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
