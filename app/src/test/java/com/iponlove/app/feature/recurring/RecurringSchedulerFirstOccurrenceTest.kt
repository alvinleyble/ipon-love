package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringScheduler
import org.junit.Test
import java.time.LocalDate

/**
 * [RecurringScheduler.firstOccurrenceOnOrAfter] — the forward projection used for the confirm
 * window floor and the recurring list's "Next:" label (so a parked confirm cursor still reads
 * as an upcoming date). Pure date math.
 */
class RecurringSchedulerFirstOccurrenceTest {

    @Test
    fun cursorAlreadyAtOrAfterFloor_isReturnedUnchanged() {
        val rule = rule("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 8, 1))
        val result = RecurringScheduler.firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 7, 15))
        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun cursorBeforeFloor_isSteppedForwardToFirstOccurrenceInWindow() {
        // Monthly cursor parked at Jan 1; floor Jul 15 → Aug 1 is the first occurrence on/after it.
        val rule = rule("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 1, 1))
        val result = RecurringScheduler.firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 7, 15))
        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 1))
    }

    @Test
    fun floorExactlyOnAnOccurrence_isInclusive() {
        val rule = rule("r", frequency = RecurringFrequency.MONTHLY, nextDate = LocalDate.of(2026, 1, 1))
        val result = RecurringScheduler.firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 7, 1))
        assertThat(result).isEqualTo(LocalDate.of(2026, 7, 1))
    }

    @Test
    fun daily_landsOnTheFloorDay() {
        val rule = rule("r", frequency = RecurringFrequency.DAILY, nextDate = LocalDate.of(2026, 6, 1))
        val result = RecurringScheduler.firstOccurrenceOnOrAfter(rule, LocalDate.of(2026, 6, 20))
        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 20))
    }
}
