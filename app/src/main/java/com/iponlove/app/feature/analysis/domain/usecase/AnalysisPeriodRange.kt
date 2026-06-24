package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Turns an anchor calendar date + [AnalysisPeriod] into the concrete half-open
 * [AnalysisWindow] the calculator filters on, and steps the anchor one period at a time.
 *
 * The window is timezone-dependent (a transaction's "day" depends on the zone), so [zone]
 * is explicit for deterministic tests; V1 is PH-only, so the app passes the system zone.
 * Weeks start on Monday.
 */
object AnalysisPeriodRange {

    fun windowFor(anchor: LocalDate, period: AnalysisPeriod, zone: ZoneId): AnalysisWindow {
        val startDate = when (period) {
            AnalysisPeriod.DAY -> anchor
            AnalysisPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            AnalysisPeriod.MONTH -> anchor.withDayOfMonth(1)
        }
        val endDate = when (period) {
            AnalysisPeriod.DAY -> startDate.plusDays(1)
            AnalysisPeriod.WEEK -> startDate.plusWeeks(1)
            AnalysisPeriod.MONTH -> startDate.plusMonths(1)
        }
        return AnalysisWindow(
            period = period,
            startInclusive = startDate.atStartOfDay(zone).toInstant(),
            endExclusive = endDate.atStartOfDay(zone).toInstant(),
        )
    }

    /** Moves [anchor] one [period] unit; [forward] = true is later, false is earlier. */
    fun step(anchor: LocalDate, period: AnalysisPeriod, forward: Boolean): LocalDate {
        val n = if (forward) 1L else -1L
        return when (period) {
            AnalysisPeriod.DAY -> anchor.plusDays(n)
            AnalysisPeriod.WEEK -> anchor.plusWeeks(n)
            AnalysisPeriod.MONTH -> anchor.plusMonths(n)
        }
    }
}
