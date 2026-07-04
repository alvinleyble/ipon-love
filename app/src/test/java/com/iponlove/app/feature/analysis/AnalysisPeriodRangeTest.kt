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

    @Test
    fun quarter_snapsToQuarterStart_andSpansThreeMonths() {
        // 2026-08-10 is in Q3 (Jul-Sep); quarter starts 2026-07-01, ends 2026-10-01.
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 8, 10), AnalysisPeriod.QUARTER, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Test
    fun quarter_handlesYearRollover_q4_into_nextYear() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 11, 1), AnalysisPeriod.QUARTER, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
    }

    @Test
    fun semiAnnual_snapsToJanOrJul_andSpansSixMonths() {
        val h1 = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 3, 10), AnalysisPeriod.SEMI_ANNUAL, zone)
        assertThat(h1.startInclusive).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(h1.endExclusive).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"))

        val h2 = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 9, 10), AnalysisPeriod.SEMI_ANNUAL, zone)
        assertThat(h2.startInclusive).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"))
        assertThat(h2.endExclusive).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
    }

    @Test
    fun annual_snapsToJan1_throughJan1NextYear() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 8, 10), AnalysisPeriod.ANNUAL, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
    }

    @Test
    fun allTime_ignoresAnchor_usesFixedStartAndUnboundedEnd() {
        val window = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 8, 10), AnalysisPeriod.ALL_TIME, zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2000-01-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.MAX)
    }

    @Test
    fun step_quarterAndSemiAnnualAndAnnualMoveByTheirUnit() {
        val anchor = LocalDate.of(2026, 7, 1)

        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.QUARTER, forward = true))
            .isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.SEMI_ANNUAL, forward = false))
            .isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.ANNUAL, forward = true))
            .isEqualTo(LocalDate.of(2027, 7, 1))
    }

    @Test
    fun step_allTime_isANoOp() {
        val anchor = LocalDate.of(2026, 7, 1)

        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.ALL_TIME, forward = true)).isEqualTo(anchor)
        assertThat(AnalysisPeriodRange.step(anchor, AnalysisPeriod.ALL_TIME, forward = false)).isEqualTo(anchor)
    }
}
