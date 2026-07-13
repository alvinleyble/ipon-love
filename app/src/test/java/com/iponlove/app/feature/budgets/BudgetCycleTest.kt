package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.BudgetCycle
import com.iponlove.app.feature.budgets.domain.usecase.yearMonthKey
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** ADR-0046 cycle math: a payday-aligned budget month, reinterpreting the opaque `yearMonth` key. */
class BudgetCycleTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String) = Instant.parse("${date}T12:00:00Z")

    // --- cycleKey ---

    @Test
    fun cycleKey_startDay1_equalsCalendarMonthKey() {
        // Day 1 must be byte-identical to the old calendar-month bucketing across a range of dates.
        for (day in listOf("2026-01-01", "2026-02-28", "2026-06-15", "2026-12-31")) {
            val instant = at(day)
            assertThat(BudgetCycle.cycleKey(instant, startDay = 1, zone = zone))
                .isEqualTo(yearMonthKey(instant, zone))
        }
    }

    @Test
    fun cycleKey_day15_bucketsBeforeAndAfterTheStart() {
        // Cycle "2026-07" @ day 15 covers Jul 15 – Aug 14.
        assertThat(BudgetCycle.cycleKey(at("2026-07-14"), 15, zone)).isEqualTo("2026-06")
        assertThat(BudgetCycle.cycleKey(at("2026-07-15"), 15, zone)).isEqualTo("2026-07")
        assertThat(BudgetCycle.cycleKey(at("2026-08-14"), 15, zone)).isEqualTo("2026-07")
        assertThat(BudgetCycle.cycleKey(at("2026-08-15"), 15, zone)).isEqualTo("2026-08")
    }

    @Test
    fun cycleKey_day31_meansLastDayOfShortMonths() {
        // Day 31 clamps to each month's last day: April (30 days) starts its cycle on the 30th.
        assertThat(BudgetCycle.cycleKey(at("2026-04-29"), 31, zone)).isEqualTo("2026-03")
        assertThat(BudgetCycle.cycleKey(at("2026-04-30"), 31, zone)).isEqualTo("2026-04")
    }

    // --- window ---

    @Test
    fun window_day15_isFifteenthToDayBeforeNextFifteenth() {
        val w = BudgetCycle.window("2026-07", 15)
        assertThat(w.firstDay).isEqualTo(LocalDate.of(2026, 7, 15))
        assertThat(w.lastDay).isEqualTo(LocalDate.of(2026, 8, 14))
    }

    @Test
    fun window_day1_isTheWholeCalendarMonth() {
        val w = BudgetCycle.window("2026-02", 1)
        assertThat(w.firstDay).isEqualTo(LocalDate.of(2026, 2, 1))
        assertThat(w.lastDay).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun windows_day30_stayContiguousAcrossFebruary_noGapNoOverlap() {
        // Day 30 across the short month: consecutive cycles must abut exactly (last + 1 == next first).
        val jan = BudgetCycle.window("2026-01", 30) // Jan 30 -> Feb 27 (Feb clamps to 28th start)
        val feb = BudgetCycle.window("2026-02", 30) // Feb 28 -> Mar 29
        val mar = BudgetCycle.window("2026-03", 30) // Mar 30 -> Apr 29

        assertThat(jan.firstDay).isEqualTo(LocalDate.of(2026, 1, 30))
        assertThat(jan.lastDay).isEqualTo(LocalDate.of(2026, 2, 27))
        assertThat(feb.firstDay).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(feb.lastDay).isEqualTo(LocalDate.of(2026, 3, 29))
        assertThat(mar.firstDay).isEqualTo(LocalDate.of(2026, 3, 30))

        // Contiguity: each cycle's last day is exactly the day before the next cycle's first day.
        assertThat(jan.lastDay.plusDays(1)).isEqualTo(feb.firstDay)
        assertThat(feb.lastDay.plusDays(1)).isEqualTo(mar.firstDay)
    }

    @Test
    fun everyDayOfAYearBelongsToExactlyOneCycle_day30() {
        // Stronger contiguity check: sweep a whole year and assert each date's cycleKey window
        // actually contains that date — proves no gaps and no double-assignment.
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        while (!date.isAfter(end)) {
            val instant = date.atStartOfDay(zone).toInstant().plusSeconds(43_200) // noon
            val key = BudgetCycle.cycleKey(instant, 30, zone)
            val w = BudgetCycle.window(key, 30)
            assertThat(!date.isBefore(w.firstDay) && !date.isAfter(w.lastDay)).isTrue()
            date = date.plusDays(1)
        }
    }
}
