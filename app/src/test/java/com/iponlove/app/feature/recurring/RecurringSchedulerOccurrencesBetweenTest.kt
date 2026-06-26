package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringScheduler
import org.junit.Test
import java.time.LocalDate

class RecurringSchedulerOccurrencesBetweenTest {

    // ─── DAILY ──────────────────────────────────────────────────────────────

    @Test
    fun daily_interval1_allDaysInWindow() {
        val r = rule("r", frequency = RecurringFrequency.DAILY, interval = 1,
            nextDate = LocalDate.of(2026, 7, 1))
        val from = LocalDate.of(2026, 7, 1)
        val to   = LocalDate.of(2026, 7, 7)

        val result = RecurringScheduler.occurrencesBetween(r, from, to)

        assertThat(result).containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 2),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 5),
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 7),
        ).inOrder()
    }

    @Test
    fun daily_interval3_skipsNonMatchingDays() {
        val r = rule("r", frequency = RecurringFrequency.DAILY, interval = 3,
            nextDate = LocalDate.of(2026, 7, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 7, 13),
        ).inOrder()
    }

    // ─── WEEKLY ─────────────────────────────────────────────────────────────

    @Test
    fun weekly_interval1_fourWeeksInWindow() {
        val r = rule("r", frequency = RecurringFrequency.WEEKLY, interval = 1,
            nextDate = LocalDate.of(2026, 7, 6))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 27),
        ).inOrder()
    }

    @Test
    fun weekly_interval2_fortnightly() {
        val r = rule("r", frequency = RecurringFrequency.WEEKLY, interval = 2,
            nextDate = LocalDate.of(2026, 7, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 29),
        ).inOrder()
    }

    // ─── MONTHLY ────────────────────────────────────────────────────────────

    @Test
    fun monthly_interval1_oneOccurrencePerMonth() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 6, 15))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 8, 15),
        ).inOrder()
    }

    @Test
    fun monthly_interval3_quarterly() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 3,
            nextDate = LocalDate.of(2026, 1, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 10, 1),
        ).inOrder()
    }

    // ─── endDate boundary ───────────────────────────────────────────────────

    @Test
    fun endDate_inclusive_stopsGeneration() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 8, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 1),
        ).inOrder()
    }

    @Test
    fun endDate_beforeWindowStart_returnsEmpty() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 3, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result).isEmpty()
    }

    @Test
    fun endDate_midWindow_stopsAtEndDate() {
        val r = rule("r", frequency = RecurringFrequency.WEEKLY, interval = 1,
            nextDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 15))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

        assertThat(result).containsExactly(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 15),
        ).inOrder()
    }

    // ─── open-ended rule ────────────────────────────────────────────────────

    @Test
    fun openEnded_nextDateAfterWindow_returnsEmpty() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 8, 1))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertThat(result).isEmpty()
    }

    @Test
    fun openEnded_nextDateBeforeWindow_findsOccurrenceInWindow() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 4, 15))

        // nextDate is before the window; the scheduler should advance to find July 15
        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

        assertThat(result).containsExactly(LocalDate.of(2026, 7, 15))
    }

    @Test
    fun fromEqualsTo_singleDayWindow_matchingRule_returnsOne() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 7, 10))
        val day = LocalDate.of(2026, 7, 10)

        val result = RecurringScheduler.occurrencesBetween(r, day, day)

        assertThat(result).containsExactly(day)
    }

    @Test
    fun fromEqualsTo_singleDayWindow_nonMatchingDay_returnsEmpty() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, interval = 1,
            nextDate = LocalDate.of(2026, 7, 10))

        val result = RecurringScheduler.occurrencesBetween(
            r, LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 11))

        assertThat(result).isEmpty()
    }
}
