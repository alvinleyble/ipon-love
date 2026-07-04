package com.iponlove.app.core.date

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Day-header labeling + bucketing shared by Records and Combined (ADR-0032). */
class DayGroupingTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 4)

    @Test
    fun dayHeaderLabel_today_isRelative_onlyWhenViewingCurrentMonth() {
        assertThat(DayGrouping.dayHeaderLabel(today, today, isCurrentMonth = true)).isEqualTo("Today")
        assertThat(DayGrouping.dayHeaderLabel(today, today, isCurrentMonth = false)).isNotEqualTo("Today")
    }

    @Test
    fun dayHeaderLabel_yesterday_isRelative_onlyWhenViewingCurrentMonth() {
        val yesterday = today.minusDays(1)

        assertThat(DayGrouping.dayHeaderLabel(yesterday, today, isCurrentMonth = true)).isEqualTo("Yesterday")
        assertThat(DayGrouping.dayHeaderLabel(yesterday, today, isCurrentMonth = false)).isNotEqualTo("Yesterday")
    }

    @Test
    fun dayHeaderLabel_otherDays_getPlainDateFormat_evenWithinCurrentMonth() {
        val otherDay = today.minusDays(3) // 2026-07-01, still in the current month

        assertThat(DayGrouping.dayHeaderLabel(otherDay, today, isCurrentMonth = true)).isEqualTo("Wed, Jul 1")
    }

    @Test
    fun groupByDay_bucketsByCalendarDay_preservingEncounterOrder() {
        val items = listOf(
            Instant.parse("2026-07-04T10:00:00Z"),
            Instant.parse("2026-07-04T08:00:00Z"),
            Instant.parse("2026-07-03T09:00:00Z"),
        )

        val groups = DayGrouping.groupByDay(
            items = items,
            dateOf = { it },
            zone = zone,
            today = today,
            isCurrentMonth = true,
        )

        assertThat(groups.map { it.label }).containsExactly("Today", "Yesterday").inOrder()
        assertThat(groups[0].items).hasSize(2)
        assertThat(groups[1].items).hasSize(1)
    }
}
