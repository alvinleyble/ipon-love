package com.iponlove.app.feature.drafts.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class DraftAgeLabelTest {

    private fun ph(iso: String): Instant = Instant.parse(iso)

    @Test
    fun sameCalendarDayReadsAsToday() {
        assertThat(draftAgeLabel(ph("2026-08-06T01:00:00Z"), ph("2026-08-06T13:00:00Z")))
            .isEqualTo("parked today")
    }

    @Test
    fun thePreviousCalendarDayReadsAsYesterday() {
        assertThat(draftAgeLabel(ph("2026-08-04T20:00:00Z"), ph("2026-08-05T20:00:00Z")))
            .isEqualTo("parked yesterday")
    }

    @Test
    fun olderDraftsCountWholeDays() {
        assertThat(draftAgeLabel(ph("2026-07-25T02:00:00Z"), ph("2026-08-06T02:00:00Z")))
            .isEqualTo("parked 12 days ago")
    }

    /**
     * Calendar days on the PH clock, not elapsed hours (contract §4). 23:30 Manila on the 5th is
     * 15:30 UTC — an hour later is still the 5th in UTC but already the 6th in Manila, and the
     * label must agree with the calendar the rest of the app sorts by.
     */
    @Test
    fun daysAreJudgedOnThePhCalendarNotUtc() {
        assertThat(draftAgeLabel(ph("2026-08-05T15:30:00Z"), ph("2026-08-05T16:30:00Z")))
            .isEqualTo("parked yesterday")
    }

    /** A clock-skewed or future-stamped row reads as just parked, never as a negative count. */
    @Test
    fun aFutureTimestampDegradesToToday() {
        assertThat(draftAgeLabel(ph("2026-08-09T02:00:00Z"), ph("2026-08-06T02:00:00Z")))
            .isEqualTo("parked today")
    }
}
