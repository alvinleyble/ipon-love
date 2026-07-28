package com.iponlove.app.feature.recurring.presentation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DUE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/**
 * Relative-day copy for a "Coming up" row (Item 19) — pure date math, no Compose dependency, so
 * it's unit-testable per CLAUDE.md's recurring-rule date-math policy.
 *
 * [UpcomingOccurrence.date][com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence]
 * is strictly after today by construction, so `daysAway <= 0` shouldn't occur in practice — kept
 * as a defensive floor rather than a real case.
 */
fun comingUpDueLabel(date: LocalDate, today: LocalDate): String {
    val daysAway = ChronoUnit.DAYS.between(today, date)
    return when {
        daysAway <= 0L -> "today"
        daysAway == 1L -> "tomorrow"
        else -> "${date.format(DUE_DATE_FORMAT)} · in $daysAway days"
    }
}
