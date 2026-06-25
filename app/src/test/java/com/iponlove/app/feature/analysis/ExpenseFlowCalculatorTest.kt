package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.analysis.domain.usecase.ExpenseFlowCalculator
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ExpenseFlowCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun june(day: Int): Instant = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")

    private val juneWindow =
        AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 15), AnalysisPeriod.MONTH, zone)

    @Test
    fun emptyLedger_returnsAllZeroes() {
        val data = ExpenseFlowCalculator.calculate(emptyList(), juneWindow, zone)

        assertThat(data.daysInMonth).isEqualTo(30)
        assertThat(data.cumulativeByDay).hasSize(30)
        assertThat(data.cumulativeByDay.all { it == BigDecimal.ZERO }).isTrue()
    }

    @Test
    fun singleExpense_accumulatesFromThatDayOnward() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1000.00", categoryId = "cat-1", date = june(5)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone)

        // Days 1–4: zero. Day 5 onward: 1000.00.
        assertThat(data.cumulativeByDay[3]).isEqualTo(BigDecimal.ZERO) // day 4
        assertThat(data.cumulativeByDay[4]).isEqualTo(BigDecimal("1000.00")) // day 5
        assertThat(data.cumulativeByDay[29]).isEqualTo(BigDecimal("1000.00")) // day 30
    }

    @Test
    fun multipleExpenses_cumulativeSumsCorrectly() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "500.00", categoryId = "cat-1", date = june(1)),
            txn("t2", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = june(1)),
            txn("t3", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = june(10)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.cumulativeByDay[0]).isEqualTo(BigDecimal("800.00"))  // day 1: 500+300
        assertThat(data.cumulativeByDay[8]).isEqualTo(BigDecimal("800.00"))  // day 9: unchanged
        assertThat(data.cumulativeByDay[9]).isEqualTo(BigDecimal("1000.00")) // day 10: +200
        assertThat(data.cumulativeByDay[29]).isEqualTo(BigDecimal("1000.00")) // day 30: unchanged
    }

    @Test
    fun incomeAndTransferAreIgnored() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", date = june(1)),
            txn("t2", TransactionType.TRANSFER, "1000.00", accountId = "a1", toAccountId = "a2", date = june(5)),
            txn("t3", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = june(3)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.cumulativeByDay[29]).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun transactionsOutsideWindowAreIgnored() {
        val transactions = listOf(
            txn("before", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-05-31T23:59:59Z")),
            txn("in", TransactionType.EXPENSE, "100.00", categoryId = "cat-1", date = june(15)),
            txn("after", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-07-01T00:00:00Z")),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.cumulativeByDay[29]).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun todayDayOfMonth_isNullForPastMonth() {
        val pastWindow =
            AnalysisPeriodRange.windowFor(LocalDate.of(2025, 1, 1), AnalysisPeriod.MONTH, zone)

        val data = ExpenseFlowCalculator.calculate(emptyList(), pastWindow, zone)

        assertThat(data.todayDayOfMonth).isNull()
    }
}
