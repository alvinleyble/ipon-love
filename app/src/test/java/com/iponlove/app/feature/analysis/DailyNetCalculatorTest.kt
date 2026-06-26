package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DailyNetCalculatorTest {

    private val zone = ZoneOffset.UTC

    private fun june(day: Int): Instant = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")

    // June 2026 starts on a Monday → Sun-first firstWeekdayOffset = 1
    private val juneWindow =
        AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 15), AnalysisPeriod.MONTH, zone)

    @Test
    fun emptyLedger_allZeroesAndCorrectShape() {
        val data = DailyNetCalculator.calculate(emptyList(), juneWindow, zone)

        assertThat(data.daysInMonth).isEqualTo(30)
        assertThat(data.incomeByDay).hasSize(30)
        assertThat(data.expenseByDay).hasSize(30)
        assertThat(data.incomeByDay.all { it == BigDecimal.ZERO }).isTrue()
        assertThat(data.expenseByDay.all { it == BigDecimal.ZERO }).isTrue()
    }

    @Test
    fun settlementLegsAreExcludedFromBothDays() {
        val transactions = listOf(
            txn("income", TransactionType.INCOME, "5000.00", date = june(1)),
            txn("spend", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = june(1)),
            // Settlement legs move balance but are not net income/expense (ADR-0019 #14).
            txn("settle-out", TransactionType.EXPENSE, "900.00", categoryId = null, date = june(1), isSettlement = true),
            txn("settle-in", TransactionType.INCOME, "700.00", categoryId = null, date = june(1), isSettlement = true),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[0]).isEqualTo(BigDecimal("5000.00"))
        assertThat(data.expenseByDay[0]).isEqualTo(BigDecimal("300.00"))
    }

    @Test
    fun juneStartsOnMonday_firstWeekdayOffsetIsOne() {
        // 2026-06-01 is a Monday → Sun-first offset = 1 (Sunday=0, Monday=1)
        val data = DailyNetCalculator.calculate(emptyList(), juneWindow, zone)

        assertThat(data.firstWeekdayOffset).isEqualTo(1)
    }

    @Test
    fun incomeOnDay1_incomeArrayUpdated() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", date = june(1)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[0]).isEqualTo(BigDecimal("5000.00"))
        assertThat(data.expenseByDay[0]).isEqualTo(BigDecimal.ZERO)
        assertThat(data.incomeByDay[1]).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun expenseOnDay1_expenseArrayUpdated() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1200.00", categoryId = "food", date = june(1)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.expenseByDay[0]).isEqualTo(BigDecimal("1200.00"))
        assertThat(data.incomeByDay[0]).isEqualTo(BigDecimal.ZERO)
        assertThat(data.expenseByDay[1]).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun sameDay_incomeAndExpense_bothArraysCorrect() {
        val transactions = listOf(
            txn("i1", TransactionType.INCOME, "3000.00", date = june(10)),
            txn("e1", TransactionType.EXPENSE, "1500.00", categoryId = "cat", date = june(10)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[9]).isEqualTo(BigDecimal("3000.00"))
        assertThat(data.expenseByDay[9]).isEqualTo(BigDecimal("1500.00"))
    }

    @Test
    fun transferIsIgnored() {
        val transactions = listOf(
            txn("t1", TransactionType.TRANSFER, "2000.00", accountId = "a1", toAccountId = "a2", date = june(5)),
            txn("i1", TransactionType.INCOME, "1000.00", date = june(5)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[4]).isEqualTo(BigDecimal("1000.00"))
        assertThat(data.expenseByDay[4]).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun transactionsOutsideWindowAreIgnored() {
        val transactions = listOf(
            txn("before", TransactionType.INCOME, "9999.00", date = Instant.parse("2026-05-31T23:59:59Z")),
            txn("in", TransactionType.INCOME, "100.00", date = june(15)),
            txn("after", TransactionType.EXPENSE, "9999.00", categoryId = "c", date = Instant.parse("2026-07-01T00:00:00Z")),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[14]).isEqualTo(BigDecimal("100.00"))
        assertThat(data.incomeByDay.filterIndexed { i, _ -> i != 14 }.all { it == BigDecimal.ZERO }).isTrue()
        assertThat(data.expenseByDay.all { it == BigDecimal.ZERO }).isTrue()
    }

    @Test
    fun todayDayOfMonth_isNullForPastMonth() {
        val pastWindow =
            AnalysisPeriodRange.windowFor(LocalDate.of(2025, 1, 1), AnalysisPeriod.MONTH, zone)

        val data = DailyNetCalculator.calculate(emptyList(), pastWindow, zone)

        assertThat(data.todayDayOfMonth).isNull()
    }

    @Test
    fun multipleTransactionsOnSameDay_summedCorrectly() {
        val transactions = listOf(
            txn("e1", TransactionType.EXPENSE, "300.00", categoryId = "food", date = june(20)),
            txn("e2", TransactionType.EXPENSE, "200.00", categoryId = "food", date = june(20)),
            txn("i1", TransactionType.INCOME, "100.00", date = june(20)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.incomeByDay[19]).isEqualTo(BigDecimal("100.00"))
        assertThat(data.expenseByDay[19]).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun firstWeekdayOffset_correctForJuly2026() {
        // 2026-07-01 is a Wednesday → Sun-first offset = 3 (Sun=0, Mon=1, Tue=2, Wed=3)
        val julyWindow =
            AnalysisPeriodRange.windowFor(LocalDate.of(2026, 7, 1), AnalysisPeriod.MONTH, zone)

        val data = DailyNetCalculator.calculate(emptyList(), julyWindow, zone)

        assertThat(data.firstWeekdayOffset).isEqualTo(3)
    }
}
