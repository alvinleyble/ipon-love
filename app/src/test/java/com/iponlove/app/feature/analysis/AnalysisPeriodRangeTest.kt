package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Window math: anchor + period -> half-open [start, end); plus stepping. */
class AnalysisPeriodRangeTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun day_windowIsTheAnchorDayMidnightToNextMidnight() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 24), AnalysisPeriod.DAY, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-06-24T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2026-06-25T00:00:00Z"))
    }

    @Test
    fun week_snapsBackToMonday_andSpansSevenDays() {
        // 2026-06-24 is a Wednesday; the week starts Monday 2026-06-22.
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 24), AnalysisPeriod.WEEK, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-06-22T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2026-06-29T00:00:00Z"))
    }

    @Test
    fun week_anchoredOnMonday_staysOnThatMonday() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 22), AnalysisPeriod.WEEK, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-06-22T00:00:00Z"))
    }

    @Test
    fun month_snapsToFirstOfMonth_throughFirstOfNextMonth() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 24), AnalysisPeriod.MONTH, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"))
    }

    @Test
    fun month_handlesYearRollover_dec_to_jan() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 12, 10), AnalysisPeriod.MONTH, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
    }

    @Test
    fun step_movesByTheRightUnitInEachDirection() {
        val anchor = LocalDate.of(2026, 6, 15)

        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.DAY, forward = true))
            .isEqualTo(LocalDate.of(2026, 6, 16))
        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.WEEK, forward = false))
            .isEqualTo(LocalDate.of(2026, 6, 8))
        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.MONTH, forward = true))
            .isEqualTo(LocalDate.of(2026, 7, 15))
    }
}
