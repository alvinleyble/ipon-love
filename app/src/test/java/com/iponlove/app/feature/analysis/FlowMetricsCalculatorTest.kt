package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.usecase.FlowMetricsCalculator
import org.junit.Test
import java.math.BigDecimal

class FlowMetricsCalculatorTest {

    private fun calc(
        expense: String,
        elapsed: Int,
        daysInMonth: Int = 30,
        isCurrentMonth: Boolean = true,
        budget: String = "0",
    ) = FlowMetricsCalculator.calculate(
        totalExpense = BigDecimal(expense),
        daysElapsed = elapsed,
        daysInMonth = daysInMonth,
        isCurrentMonth = isCurrentMonth,
        budgetTotal = BigDecimal(budget),
    )

    @Test
    fun avgDailySpend_dividesTotalByElapsed() {
        val result = calc("3000.00", elapsed = 10)
        assertThat(result.avgDailySpend).isEqualTo(BigDecimal("300.00"))
    }

    @Test
    fun avgDailySpend_roundsHalfUp() {
        // 100 / 3 = 33.333… → rounds to 33.33
        val result = calc("100", elapsed = 3)
        assertThat(result.avgDailySpend).isEqualTo(BigDecimal("33.33"))
    }

    @Test
    fun avgDailySpend_zeroExpense_isZero() {
        val result = calc("0", elapsed = 15)
        assertThat(result.avgDailySpend).isEqualTo(BigDecimal("0.00"))
    }

    @Test
    fun projectedMonthEnd_currentMonth_avgTimesMonthLength() {
        // avg = 100/day, 30-day month → projected = 3000
        val result = calc("1000.00", elapsed = 10, daysInMonth = 30, isCurrentMonth = true)
        assertThat(result.projectedMonthEnd).isEqualTo(BigDecimal("3000.00"))
    }

    @Test
    fun projectedMonthEnd_pastMonth_isNull() {
        val result = calc("1000.00", elapsed = 30, daysInMonth = 30, isCurrentMonth = false)
        assertThat(result.projectedMonthEnd).isNull()
    }

    @Test
    fun budgetRemaining_underBudget_returnsPositiveRemainder() {
        val result = calc("3000.00", elapsed = 10, budget = "10000")
        assertThat(result.budgetRemaining).isEqualTo(BigDecimal("7000.00"))
    }

    @Test
    fun budgetRemaining_overBudget_floorsToZero() {
        val result = calc("12000.00", elapsed = 20, budget = "10000")
        assertThat(result.budgetRemaining).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun budgetRemaining_noBudgetSet_isNull() {
        val result = calc("3000.00", elapsed = 10, budget = "0")
        assertThat(result.budgetRemaining).isNull()
    }

    @Test
    fun elapsedEqualsFullMonth_pastMonthComplete() {
        // Past month: projected null, avg covers full month
        val result = calc("9000.00", elapsed = 30, daysInMonth = 30, isCurrentMonth = false)
        assertThat(result.avgDailySpend).isEqualTo(BigDecimal("300.00"))
        assertThat(result.projectedMonthEnd).isNull()
    }
}
