package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringScheduler
import org.junit.Test
import java.time.LocalDate

class RecurringSchedulerTest {

    @Test
    fun notDueYet_yieldsNothing_andLeavesCursor() {
        val r = rule("r", nextDate = LocalDate.of(2026, 7, 1))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 15))

        assertThat(run.occurrences).isEmpty()
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun dueToday_yieldsOne_andAdvancesOneInterval() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 6, 1))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 1))

        assertThat(run.occurrences).containsExactly(LocalDate.of(2026, 6, 1))
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun catchUp_generatesEveryMissedOccurrence_inOrder() {
        val r = rule("r", frequency = RecurringFrequency.WEEKLY, nextDate = LocalDate.of(2026, 6, 1))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 25))

        assertThat(run.occurrences).containsExactly(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 22),
        ).inOrder()
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2026, 6, 29))
    }

    @Test
    fun interval_stepsByMultipleUnits() {
        val r = rule(
            "r",
            frequency = RecurringFrequency.WEEKLY,
            interval = 2,
            nextDate = LocalDate.of(2026, 6, 1),
        )

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 30))

        assertThat(run.occurrences).containsExactly(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 29),
        ).inOrder()
    }

    @Test
    fun daily_stepsByDays() {
        val r = rule("r", frequency = RecurringFrequency.DAILY, nextDate = LocalDate.of(2026, 6, 1))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 3))

        assertThat(run.occurrences).hasSize(3)
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2026, 6, 4))
    }

    @Test
    fun endDate_isInclusive_andStopsGeneration() {
        val r = rule(
            "r",
            frequency = RecurringFrequency.MONTHLY,
            nextDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 8, 1),
        )

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 12, 31))

        assertThat(run.occurrences).containsExactly(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 1),
        ).inOrder()
        // Cursor moves past the end; a later pass yields nothing.
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(RecurringScheduler.run(r.copy(nextDate = run.nextDate), asOf = LocalDate.of(2027, 1, 1)).occurrences)
            .isEmpty()
    }

    @Test
    fun monthly_clampsMonthEnd() {
        val r = rule("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 1, 31))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 3, 31))

        // Jan 31 → Feb 28 (clamped) → Mar 28 (steps from the clamped date).
        assertThat(run.occurrences).containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 28),
        ).inOrder()
    }

    @Test
    fun runawayGuard_capsOccurrencesPerPass() {
        val r = rule("r", frequency = RecurringFrequency.DAILY, nextDate = LocalDate.of(2000, 1, 1))

        val run = RecurringScheduler.run(r, asOf = LocalDate.of(2026, 6, 1))

        assertThat(run.occurrences).hasSize(RecurringScheduler.MAX_OCCURRENCES_PER_PASS)
        // Cursor sits at the first un-generated date, so the next pass continues the catch-up.
        assertThat(run.nextDate).isEqualTo(LocalDate.of(2000, 1, 1).plusDays(RecurringScheduler.MAX_OCCURRENCES_PER_PASS.toLong()))
    }
}
