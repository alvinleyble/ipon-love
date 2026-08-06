package com.iponlove.app.feature.drafts.presentation

import com.iponlove.app.core.date.PH_ZONE
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * "parked 12 days ago" — the per-row age label that supplies the mild pressure keeping the parking
 * area from becoming a graveyard (ADR-0066 decision 10). Deliberately the *only* pressure: there
 * is no notification, and no draft is ever auto-deleted.
 *
 * Whole calendar days on the PH-local calendar (contract §4), not elapsed hours — "parked
 * yesterday" must mean the previous calendar day, the same judgement `comingUpDueLabel` makes.
 * Pure date math, no Compose dependency, so it's unit-testable on the JVM.
 */
fun draftAgeLabel(parkedAt: Instant, now: Instant, zone: ZoneId = PH_ZONE): String {
    val days = ChronoUnit.DAYS.between(
        parkedAt.atZone(zone).toLocalDate(),
        now.atZone(zone).toLocalDate(),
    )
    return when {
        // A clock-skew or future-stamped row reads as "just parked" rather than a negative count.
        days <= 0L -> "parked today"
        days == 1L -> "parked yesterday"
        else -> "parked $days days ago"
    }
}
