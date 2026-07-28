package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.recurring.domain.usecase.RecurringReminderDeliveryWindow.isWithinDeliveryWindow
import org.junit.Test
import java.time.LocalTime

/**
 * The only tier-1 surface in v1.7.1 Item 12 (ADR-0056 decision 10) — everything else in the slice
 * is orchestration. Boundaries matter here because the window is half-open: getting 21:00 wrong
 * would let a reminder buzz at nine in the evening, and getting 08:00 wrong would silently push
 * the first delivery of the day back by six hours.
 */
class RecurringReminderDeliveryWindowTest {

    @Test
    fun justBeforeOpening_isSuppressed() {
        assertThat(isWithinDeliveryWindow(LocalTime.of(7, 59))).isFalse()
    }

    @Test
    fun exactlyAtOpening_fires() {
        assertThat(isWithinDeliveryWindow(LocalTime.of(8, 0))).isTrue()
    }

    @Test
    fun midday_fires() {
        assertThat(isWithinDeliveryWindow(LocalTime.of(13, 30))).isTrue()
    }

    @Test
    fun lastMinuteBeforeClosing_fires() {
        assertThat(isWithinDeliveryWindow(LocalTime.of(20, 59))).isTrue()
    }

    @Test
    fun exactlyAtClosing_isSuppressed() {
        assertThat(isWithinDeliveryWindow(LocalTime.of(21, 0))).isFalse()
    }

    @Test
    fun deepOvernight_isSuppressed() {
        // The failure this gate exists for: a Doze maintenance window flushing the sweep at 03:40.
        assertThat(isWithinDeliveryWindow(LocalTime.of(3, 40))).isFalse()
    }
}
