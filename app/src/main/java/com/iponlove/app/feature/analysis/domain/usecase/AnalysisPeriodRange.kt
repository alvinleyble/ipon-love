package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Turns an anchor calendar date + [AnalysisPeriod] into the concrete half-open
 * [AnalysisWindow] the calculator filters on, and steps the anchor one period at a time.
 *
 * The window is timezone-dependent (a transaction's "day" depends on the zone), so [zone]
 * is explicit for deterministic tests; V1 is PH-only, so the app passes the system zone.
 * Weeks start on Monday; quarters start Jan/Apr/Jul/Oct; halves start Jan/Jul. MONTH delegates
 * to [MonthWindow], shared with the Records/Combined month-windowed views (ADR-0032).
 */
object AnalysisPeriodRange {

    /** Fixed lower bound for ALL_TIME (ADR-0030) — not a query for the earliest transaction. */
    private val ALL_TIME_START: LocalDate = LocalDate.of(2000, 1, 1)

    fun windowFor(anchor: LocalDate, period: AnalysisPeriod, zone: ZoneId): AnalysisWindow {
        if (period == AnalysisPeriod.ALL_TIME) {
            return AnalysisWindow(
                period = period,
                startInclusive = ALL_TIME_START.atStartOfDay(zone).toInstant(),
                endExclusive = Instant.MAX,
            )
        }
        if (period == AnalysisPeriod.MONTH) {
            val month = MonthWindow.windowFor(anchor, zone)
            return AnalysisWindow(period = period, startInclusive = month.startInclusive, endExclusive = month.endExclusive)
        }
        val startDate = when (period) {
            AnalysisPeriod.DAY -> anchor
            AnalysisPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            AnalysisPeriod.QUARTER -> anchor.withMonth(quarterStartMonth(anchor)).withDayOfMonth(1)
            AnalysisPeriod.SEMI_ANNUAL -> anchor.withMonth(if (anchor.monthValue <= 6) 1 else 7).withDayOfMonth(1)
            AnalysisPeriod.ANNUAL -> anchor.withDayOfYear(1)
            AnalysisPeriod.MONTH, AnalysisPeriod.ALL_TIME -> error("handled above")
        }
        val endDate = when (period) {
            AnalysisPeriod.DAY -> startDate.plusDays(1)
            AnalysisPeriod.WEEK -> startDate.plusWeeks(1)
            AnalysisPeriod.QUARTER -> startDate.plusMonths(3)
            AnalysisPeriod.SEMI_ANNUAL -> startDate.plusMonths(6)
            AnalysisPeriod.ANNUAL -> startDate.plusYears(1)
            AnalysisPeriod.MONTH, AnalysisPeriod.ALL_TIME -> error("handled above")
        }
        return AnalysisWindow(
            period = period,
            startInclusive = startDate.atStartOfDay(zone).toInstant(),
            endExclusive = endDate.atStartOfDay(zone).toInstant(),
        )
    }

    /**
     * True when paging forward from [anchor]'s period is still allowed (Item 3B, 2026-07-09).
     *
     * The three short "free" ranges (DAY/WEEK/MONTH) are forward-capped at the period containing
     * [today] — their "current period" is well-defined, so you can't page into empty future days/
     * weeks/months (this refines Item 2, which left Analysis steppers uncapped). The longer ranges
     * (QUARTER/SEMI_ANNUAL/ANNUAL) stay forward-uncapped by design; ALL_TIME never steps.
     * The −12-month back floor is a separate paywall concern (DEEP_HISTORY), wired later.
     */
    fun canStepForward(anchor: LocalDate, period: AnalysisPeriod, today: LocalDate, zone: ZoneId): Boolean =
        when (period) {
            AnalysisPeriod.ALL_TIME -> false
            AnalysisPeriod.DAY,
            AnalysisPeriod.WEEK,
            AnalysisPeriod.MONTH ->
                windowFor(anchor, period, zone).startInclusive < windowFor(today, period, zone).startInclusive
            AnalysisPeriod.QUARTER,
            AnalysisPeriod.SEMI_ANNUAL,
            AnalysisPeriod.ANNUAL -> true
        }

    /**
     * Moves [anchor] one [period] unit; [forward] = true is later, false is earlier.
     * ALL_TIME has nothing to step to — it's a no-op.
     */
    fun step(anchor: LocalDate, period: AnalysisPeriod, forward: Boolean): LocalDate {
        if (period == AnalysisPeriod.MONTH) return MonthWindow.step(anchor, forward)
        val n = if (forward) 1L else -1L
        return when (period) {
            AnalysisPeriod.DAY -> anchor.plusDays(n)
            AnalysisPeriod.WEEK -> anchor.plusWeeks(n)
            AnalysisPeriod.QUARTER -> anchor.plusMonths(n * 3)
            AnalysisPeriod.SEMI_ANNUAL -> anchor.plusMonths(n * 6)
            AnalysisPeriod.ANNUAL -> anchor.plusYears(n)
            AnalysisPeriod.ALL_TIME -> anchor
            AnalysisPeriod.MONTH -> error("handled above")
        }
    }

    private fun quarterStartMonth(anchor: LocalDate): Int = ((anchor.monthValue - 1) / 3) * 3 + 1
}
