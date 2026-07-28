package com.iponlove.app.feature.calculator

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.calculator.domain.CalculatorDisplayFormat.grouped
import org.junit.Test

/** Pill grouping (ADR-0058 decision 3): the number has to read at a glance, and still be a number. */
class CalculatorDisplayFormatTest {

    @Test
    fun groupsThousands() {
        assertThat(grouped("1590")).isEqualTo("1,590")
        assertThat(grouped("1234567")).isEqualTo("1,234,567")
    }

    @Test
    fun leavesShortNumbersAlone() {
        assertThat(grouped("0")).isEqualTo("0")
        assertThat(grouped("999")).isEqualTo("999")
    }

    @Test
    fun keepsTheFractionVerbatim() {
        assertThat(grouped("1590.25")).isEqualTo("1,590.25")
        // Mid-entry, the engine holds a trailing dot — dropping it would make the pill jump.
        assertThat(grouped("1590.")).isEqualTo("1,590.")
        assertThat(grouped("0.5")).isEqualTo("0.5")
    }

    @Test
    fun keepsTheSign() {
        assertThat(grouped("-1590")).isEqualTo("-1,590")
        assertThat(grouped("-999")).isEqualTo("-999")
    }

    @Test
    fun passesNonNumbersThrough() {
        // The engine surfaces divide-by-zero as a display string, not an exception.
        assertThat(grouped("Error")).isEqualTo("Error")
        assertThat(grouped("-")).isEqualTo("-")
        assertThat(grouped("")).isEmpty()
    }
}
