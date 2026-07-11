package com.iponlove.app.core.date

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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

        /**
         * True when [monthStart]'s month is strictly before [today]'s — i.e. paging forward is
         * still allowed. The Records and Combined ledgers cap forward stepping at the current
         * month so they can't page into empty future months. Budgets/recurring keep future
         * navigation. (The backward "deeper past" gate is [canStepBack].)
         */
        fun canStepForward(monthStart: LocalDate, today: LocalDate): Boolean =
            YearMonth.from(monthStart) < YearMonth.from(today)

        /** How many months of history a free user can page back over (DEEP_HISTORY, paywall
         *  §10.1): the −12 floor. −13 and beyond is Premium. Remote-overridable is out of scope
         *  for this gate — it's a hardcoded product boundary. */
        const val FREE_HISTORY_MONTHS = 12L

        /**
         * True when paging one month *earlier* than [monthStart] is still allowed — the
         * DEEP_HISTORY back-wall (paywall §10.1). [locked] folds in enforcement + entitlement
         * (from `PremiumGate.observeLocked`): while dormant or for a premium user it is false, so
         * history is unlimited. When locked, a free user can page back only to the month
         * [FREE_HISTORY_MONTHS] before [today]'s (the −12 floor); at that floor this returns false
         * so the step can't cross into the −13th month. The Records + Combined month steppers and
         * the Analysis free ranges all enforce this on their shared anchor.
         */
        fun canStepBack(monthStart: LocalDate, today: LocalDate, locked: Boolean): Boolean {
            if (!locked) return true
            val floor = YearMonth.from(today).minusMonths(FREE_HISTORY_MONTHS)
            return YearMonth.from(monthStart) > floor
        }
    }
}
