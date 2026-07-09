package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.usecase.FlowComparisonCalculator
import org.junit.Test
import java.math.BigDecimal

class FlowComparisonCalculatorTest {

    private fun calc(current: String, previous: String) =
        FlowComparisonCalculator.calculate(BigDecimal(current), BigDecimal(previous))

    @Test
    fun spentMore_isPositivePercentAndUpSign() {
        val r = calc(current = "1200", previous = "1000")!!
        assertThat(r.percentChange).isEqualTo(20)
        assertThat(r.deltaSign).isEqualTo(1)
    }

    @Test
    fun spentLess_isNegativePercentAndDownSign() {
        val r = calc(current = "800", previous = "1000")!!
        assertThat(r.percentChange).isEqualTo(-20)
        assertThat(r.deltaSign).isEqualTo(-1)
    }

    @Test
    fun unchanged_isZeroPercentAndZeroSign() {
        val r = calc(current = "500", previous = "500")!!
        assertThat(r.percentChange).isEqualTo(0)
        assertThat(r.deltaSign).isEqualTo(0)
    }

    @Test
    fun percentRoundsHalfUp() {
        // 1150 vs 1000 = +15%; 1155 vs 1000 = +15.5% → 16
        assertThat(calc("1155", "1000")!!.percentChange).isEqualTo(16)
    }

    @Test
    fun noPriorSpending_hasNullPercentButKeepsUpSign() {
        val r = calc(current = "300", previous = "0")!!
        assertThat(r.percentChange).isNull() // "New" in the UI
        assertThat(r.deltaSign).isEqualTo(1)
    }

    @Test
    fun bothEmpty_returnsNull() {
        assertThat(calc(current = "0", previous = "0")).isNull()
    }

    @Test
    fun tinyPriorBase_clampsPercent() {
        // 100000 vs 1 = +9,999,900% → clamped to 9999
        assertThat(calc("100000", "1")!!.percentChange).isEqualTo(9999)
    }
}
