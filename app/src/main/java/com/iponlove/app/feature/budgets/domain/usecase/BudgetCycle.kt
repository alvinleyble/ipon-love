package com.iponlove.app.feature.budgets.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Reinterprets a budget's opaque `"2026-07"` cycle key (a.k.a. [com.iponlove.app.feature.budgets.domain.model.Budget.yearMonth])
 * as a payday-aligned window that starts on a configurable day of the month (ADR-0046).
 *
 * A cycle labelled `"2026-07"` at start-day `N` covers **[Jul N, Aug N − 1 day]** — it is named for
 * the calendar month its window *starts* in. Consecutive labels map to consecutive [YearMonth]s, so
 * the rollover/cap/duplicate/alert machinery that steps labels by `minusMonths(1)`/`plusMonths(1)`
 * keeps working untouched; only the transaction-to-cycle bucketing and the displayed date range change.
 *
 * `startDay = 1` is byte-identical to the old calendar-month behaviour ([yearMonthKey]): every function
 * here reduces to the calendar month when `startDay = 1`.
 *
 * Boundaries are computed **statelessly per label** via [clampedStart] — never by iterating
 * `plusMonths` from an anchor, which drifts (Jan 31 → Feb 28 → Mar 28 …). Short months clamp the start
 * down to the last day (start-day 30 → February starts on the 28th/29th), which keeps windows
 * contiguous: no gaps, no overlaps. Start-day 31 therefore means "last day of the month".
 */
object BudgetCycle {

    /** Allowed range for the start-day setting; 1 is the default (calendar months). */
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 31

    /**
     * The cycle-key label (`"2026-07"`) that [instant] falls into at [startDay]. At `startDay = 1`
     * this equals [yearMonthKey]. A date on or after this calendar month's clamped start belongs to
     * this month's cycle; an earlier date belongs to the previous month's cycle.
     */
    fun cycleKey(instant: Instant, startDay: Int, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = instant.atZone(zone).toLocalDate()
        val month = YearMonth.from(date)
        return if (!date.isBefore(month.atDay(clampedStart(month, startDay)))) {
            month.toString()
        } else {
            month.minusMonths(1).toString()
        }
    }

    /** The [firstDay, lastDay] inclusive window for [cycleKey] at [startDay]. */
    fun window(cycleKey: String, startDay: Int): CycleWindow {
        val month = YearMonth.parse(cycleKey)
        val next = month.plusMonths(1)
        val start = month.atDay(clampedStart(month, startDay))
        val end = next.atDay(clampedStart(next, startDay)).minusDays(1)
        return CycleWindow(start, end)
    }

    /** [startDay] clamped to a month that is too short to contain it (e.g. day 31 in April → 30). */
    private fun clampedStart(month: YearMonth, startDay: Int): Int =
        minOf(startDay.coerceIn(MIN_START_DAY, MAX_START_DAY), month.lengthOfMonth())
}

/** Inclusive date window of a budget cycle. */
data class CycleWindow(val firstDay: LocalDate, val lastDay: LocalDate)
