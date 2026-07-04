package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringScheduler
import org.junit.Test
import java.time.LocalDate

/**
 * Pause/resume/skip math for recurring rules (ADR-0035, V1.6.1 Item 18).
 * Pure Kotlin, unit-tested per CLAUDE.md's "always test recurring-rule date math" policy.
 *
 * "Skip next" is implemented by advancing [nextDate] one interval forward
 * (see SkipNextRecurringOccurrenceUseCase), so these assert that stepping math.
 */
class PauseSkipRecurringTest {

    @Test
    fun resume_advancesNextDateToNextOccurrenceFromToday() {
        val today = LocalDate.of(2026, 7, 5)
        val paused = rule(
            "r",
            frequency = RecurringFrequency.DAILY,
            nextDate = LocalDate.of(2026, 7, 1), // Already past
            isPaused = true,
        )

        // Resume jumps nextDate to the first occurrence after today.
        var nextDate = paused.nextDate
        while (!nextDate.isAfter(today)) {
            nextDate = RecurringScheduler.advance(nextDate, paused.frequency, paused.interval)
        }

        // Today is 7/5, so next daily occurrence is 7/6.
        assertThat(nextDate).isEqualTo(LocalDate.of(2026, 7, 6))
    }

    @Test
    fun resume_withMonthlyRule_jumpsToNextMonth() {
        val today = LocalDate.of(2026, 7, 5)
        val paused = rule(
            "r",
            frequency = RecurringFrequency.MONTHLY,
            nextDate = LocalDate.of(2026, 7, 1), // July 1 is before today
            isPaused = true,
        )

        var nextDate = paused.nextDate
        while (!nextDate.isAfter(today)) {
            nextDate = RecurringScheduler.advance(nextDate, paused.frequency, paused.interval)
        }

        // July 1 + 1 month = August 1 > today (July 5).
        assertThat(nextDate).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun skipNext_monthly_advancesOneMonth() {
        // A monthly rule due Aug 1: skip next → Sep 1.
        val next = RecurringScheduler.advance(
            LocalDate.of(2026, 8, 1),
            RecurringFrequency.MONTHLY,
            interval = 1,
        )
        assertThat(next).isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun skipNext_weekly_advancesOneWeek() {
        val next = RecurringScheduler.advance(
            LocalDate.of(2026, 7, 8),
            RecurringFrequency.WEEKLY,
            interval = 1,
        )
        assertThat(next).isEqualTo(LocalDate.of(2026, 7, 15))
    }

    @Test
    fun skipNext_respectsInterval() {
        // A fortnightly rule (weekly, interval 2): skip next steps a full 2 weeks.
        val next = RecurringScheduler.advance(
            LocalDate.of(2026, 7, 1),
            RecurringFrequency.WEEKLY,
            interval = 2,
        )
        assertThat(next).isEqualTo(LocalDate.of(2026, 7, 15))
    }

    @Test
    fun skipNext_skippedOccurrenceNeverMaterializes() {
        // After skipping, the old nextDate (7/1) is behind the cursor, so run() starting
        // from the advanced cursor never yields it.
        val skipped = LocalDate.of(2026, 7, 1)
        val advanced = RecurringScheduler.advance(skipped, RecurringFrequency.WEEKLY, interval = 1)

        val ruleAfterSkip = rule(
            "r",
            frequency = RecurringFrequency.WEEKLY,
            nextDate = advanced,
        )
        val run = RecurringScheduler.run(ruleAfterSkip, asOf = LocalDate.of(2026, 7, 31))

        assertThat(run.occurrences).doesNotContain(skipped)
        assertThat(run.occurrences).contains(LocalDate.of(2026, 7, 8))
    }
}
