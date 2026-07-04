package com.iponlove.app.core.date

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The calendar month containing [monthStart], as the half-open [startInclusive, endExclusive)
 * instant range date-range queries filter on. Shared month-snap/step math used by both
 * Analysis's MONTH period (via `AnalysisPeriodRange`) and the Records/Combined transaction
 * lists (ADR-0032) — one source of truth so neither feature imports the other's usecase.
 */
data class MonthWindow(
    val monthStart: LocalDate,
    val startInclusive: Instant,
    val endExclusive: Instant,
) {
    companion object {
        fun windowFor(anchor: LocalDate, zone: ZoneId): MonthWindow {
            val start = anchor.withDayOfMonth(1)
            val end = start.plusMonths(1)
            return MonthWindow(
                monthStart = start,
                startInclusive = start.atStartOfDay(zone).toInstant(),
                endExclusive = end.atStartOfDay(zone).toInstant(),
            )
        }

        /** Moves [monthStart] one whole month; [forward] = true is later, false is earlier. */
        fun step(monthStart: LocalDate, forward: Boolean): LocalDate =
            monthStart.plusMonths(if (forward) 1L else -1L)
    }
}
