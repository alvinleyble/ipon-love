package com.iponlove.app.feature.recurring.presentation

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class ComingUpDueLabelTest {

    private val today = LocalDate.of(2026, 7, 28)

    @Test
    fun tomorrow_readsAsTomorrow() {
        assertThat(comingUpDueLabel(today.plusDays(1), today)).isEqualTo("tomorrow")
    }

    @Test
    fun twoDaysAway_readsAsDatePlusDayCount() {
        assertThat(comingUpDueLabel(today.plusDays(2), today)).isEqualTo("Jul 30 · in 2 days")
    }

    @Test
    fun sevenDaysAway_readsAsDatePlusDayCount() {
        assertThat(comingUpDueLabel(today.plusDays(7), today)).isEqualTo("Aug 4 · in 7 days")
    }

    @Test
    fun today_defensivelyReadsAsToday() {
        // UpcomingOccurrence.date is strictly after today by construction, so this shouldn't
        // occur in practice — this pins the defensive floor rather than a real case.
        assertThat(comingUpDueLabel(today, today)).isEqualTo("today")
    }

    @Test
    fun past_defensivelyReadsAsToday() {
        assertThat(comingUpDueLabel(today.minusDays(1), today)).isEqualTo("today")
    }
}
