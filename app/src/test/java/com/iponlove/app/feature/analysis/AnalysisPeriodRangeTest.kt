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

    // ─── canStepForward: free-range forward cap (Item 3B) ────────────────────

    private val today = LocalDate.of(2026, 6, 15)

    @Test
    fun canStepForward_day_pastIsTrue_currentAndFutureFalse() {
        fun day(anchor: LocalDate) = AnalysisPeriodRange.canStepForward(anchor, AnalysisPeriod.DAY, today, zone)
        assertThat(day(LocalDate.of(2026, 6, 14))).isTrue()  // yesterday
        assertThat(day(today)).isFalse()                     // today
        assertThat(day(LocalDate.of(2026, 6, 16))).isFalse() // tomorrow
    }

    @Test
    fun canStepForward_week_pastWeekTrue_currentWeekFalse() {
        fun week(anchor: LocalDate) = AnalysisPeriodRange.canStepForward(anchor, AnalysisPeriod.WEEK, today, zone)
        assertThat(week(LocalDate.of(2026, 6, 8))).isTrue()  // previous week
        assertThat(week(today)).isFalse()                    // current week (Mon 6/15)
    }

    @Test
    fun canStepForward_month_pastMonthTrue_currentMonthFalse() {
        fun month(anchor: LocalDate) = AnalysisPeriodRange.canStepForward(anchor, AnalysisPeriod.MONTH, today, zone)
        assertThat(month(LocalDate.of(2026, 5, 20))).isTrue() // last month
        assertThat(month(LocalDate.of(2026, 6, 1))).isFalse() // current month
        assertThat(month(LocalDate.of(2026, 7, 1))).isFalse() // future month
    }

    @Test
    fun canStepForward_longRanges_alwaysTrue() {
        // The premium 3M/6M/12M ranges stay forward-uncapped by design, even for a future anchor.
        val future = LocalDate.of(2027, 1, 1)
        assertThat(AnalysisPeriodRange.canStepForward(future, AnalysisPeriod.QUARTER, today, zone)).isTrue()
        assertThat(AnalysisPeriodRange.canStepForward(future, AnalysisPeriod.SEMI_ANNUAL, today, zone)).isTrue()
        assertThat(AnalysisPeriodRange.canStepForward(future, AnalysisPeriod.ANNUAL, today, zone)).isTrue()
    }

    @Test
    fun canStepForward_allTime_isFalse() {
        assertThat(AnalysisPeriodRange.canStepForward(today, AnalysisPeriod.ALL_TIME, today, zone)).isFalse()
    }

    // ─── canStepBack: DEEP_HISTORY back-wall on the free ranges (S10) ─────────

    // today = 2026-06-15 (declared above) ⇒ −12 floor month = Jun 2025.

    @Test
    fun canStepBack_unlockedIsAlwaysAllowed_onEveryRange() {
        // Dormant / premium (locked = false) never caps history.
        val old = LocalDate.of(2020, 1, 1)
        for (p in AnalysisPeriod.entries) {
            assertThat(AnalysisPeriodRange.canStepBack(old, p, today, locked = false)).isTrue()
        }
    }

    @Test
    fun canStepBack_month_lockedBlocksBelowTheFloor() {
        fun month(anchor: LocalDate) = AnalysisPeriodRange.canStepBack(anchor, AnalysisPeriod.MONTH, today, locked = true)
        assertThat(month(LocalDate.of(2025, 8, 10))).isTrue()  // above floor → can step to Jul 2025
        assertThat(month(LocalDate.of(2025, 7, 10))).isTrue()  // one above floor → can step to floor
        assertThat(month(LocalDate.of(2025, 6, 10))).isFalse() // at the floor → can't cross to May 2025
    }

    @Test
    fun canStepBack_day_cannotBackDoorTheMonthFloor() {
        // A 1D anchor can page within the floor month but not step into the month before it.
        fun day(anchor: LocalDate) = AnalysisPeriodRange.canStepBack(anchor, AnalysisPeriod.DAY, today, locked = true)
        assertThat(day(LocalDate.of(2025, 6, 15))).isTrue()  // mid-floor-month → back to 6/14 (same month)
        assertThat(day(LocalDate.of(2025, 6, 1))).isFalse()  // first of floor month → 5/31 is below floor
    }

    @Test
    fun canStepBack_week_cannotBackDoorTheMonthFloor() {
        fun week(anchor: LocalDate) = AnalysisPeriodRange.canStepBack(anchor, AnalysisPeriod.WEEK, today, locked = true)
        assertThat(week(LocalDate.of(2025, 6, 20))).isTrue()  // stepping back a week stays in Jun 2025
        assertThat(week(LocalDate.of(2025, 6, 3))).isFalse()  // back a week → May 2025, below the floor
    }

    @Test
    fun canStepBack_extendedRangesAreNotBackCappedHere() {
        // 3M/6M/12M/ALL are gated by ANALYSIS_EXTENDED_RANGES; DEEP_HISTORY leaves them uncapped.
        val old = LocalDate.of(2020, 1, 1)
        assertThat(AnalysisPeriodRange.canStepBack(old, AnalysisPeriod.QUARTER, today, locked = true)).isTrue()
        assertThat(AnalysisPeriodRange.canStepBack(old, AnalysisPeriod.SEMI_ANNUAL, today, locked = true)).isTrue()
        assertThat(AnalysisPeriodRange.canStepBack(old, AnalysisPeriod.ANNUAL, today, locked = true)).isTrue()
        assertThat(AnalysisPeriodRange.canStepBack(old, AnalysisPeriod.ALL_TIME, today, locked = true)).isTrue()
    }
}
