package com.iponlove.app.core.date

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Month snap/step math shared by Analysis's MONTH period and Records/Combined (ADR-0032). */
class MonthWindowTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun windowFor_snapsToFirstOfMonth_throughFirstOfNextMonth() {
        val window = MonthWindow.windowFor(LocalDate.of(2026, 6, 24), zone)

        assertThat(window.monthStart).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"))
    }

    @Test
    fun windowFor_handlesYearRollover_dec_to_jan() {
        val window = MonthWindow.windowFor(LocalDate.of(2026, 12, 10), zone)

        assertThat(window.startInclusive).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"))
        assertThat(window.endExclusive).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"))
    }

    @Test
    fun step_forwardMovesToNextMonth_backwardToPreviousMonth() {
        val month = LocalDate.of(2026, 6, 1)

        assertThat(MonthWindow.step(month, forward = true)).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(MonthWindow.step(month, forward = false)).isEqualTo(LocalDate.of(2026, 5, 1))
    }

    @Test
    fun step_handlesYearRollover() {
        assertThat(MonthWindow.step(LocalDate.of(2026, 12, 1), forward = true))
            .isEqualTo(LocalDate.of(2027, 1, 1))
        assertThat(MonthWindow.step(LocalDate.of(2026, 1, 1), forward = false))
            .isEqualTo(LocalDate.of(2025, 12, 1))
    }
}
