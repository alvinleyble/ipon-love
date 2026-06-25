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

    // June 2026 starts on a Monday → firstWeekdayOffset = 0
    private val juneWindow =
        AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 15), AnalysisPeriod.MONTH, zone)

    @Test
    fun emptyLedger_allZeroesAndCorrectShape() {
        val data = DailyNetCalculator.calculate(emptyList(), juneWindow, zone)

        assertThat(data.daysInMonth).isEqualTo(30)
        assertThat(data.netByDay).hasSize(30)
        assertThat(data.netByDay.all { it == BigDecimal.ZERO }).isTrue()
    }

    @Test
    fun juneStartsOnMonday_firstWeekdayOffsetIsOne() {
        // 2026-06-01 is a Monday → Sun-first offset = 1 (Sunday=0, Monday=1)
        val data = DailyNetCalculator.calculate(emptyList(), juneWindow, zone)

        assertThat(data.firstWeekdayOffset).isEqualTo(1)
    }

    @Test
    fun incomeOnDay1_positivenetOnDay1() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", date = june(1)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.netByDay[0]).isEqualTo(BigDecimal("5000.00"))
        assertThat(data.netByDay[1]).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun expenseOnDay1_negativeNetOnDay1() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1200.00", categoryId = "food", date = june(1)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.netByDay[0]).isEqualTo(BigDecimal("-1200.00"))
        assertThat(data.netByDay[1]).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun sameDay_incomeAndExpense_netsCorrectly() {
        val transactions = listOf(
            txn("i1", TransactionType.INCOME, "3000.00", date = june(10)),
            txn("e1", TransactionType.EXPENSE, "1500.00", categoryId = "cat", date = june(10)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.netByDay[9]).isEqualTo(BigDecimal("1500.00"))
    }

    @Test
    fun transferIsIgnored() {
        val transactions = listOf(
            txn("t1", TransactionType.TRANSFER, "2000.00", accountId = "a1", toAccountId = "a2", date = june(5)),
            txn("i1", TransactionType.INCOME, "1000.00", date = june(5)),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.netByDay[4]).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun transactionsOutsideWindowAreIgnored() {
        val transactions = listOf(
            txn("before", TransactionType.INCOME, "9999.00", date = Instant.parse("2026-05-31T23:59:59Z")),
            txn("in", TransactionType.INCOME, "100.00", date = june(15)),
            txn("after", TransactionType.EXPENSE, "9999.00", categoryId = "c", date = Instant.parse("2026-07-01T00:00:00Z")),
        )

        val data = DailyNetCalculator.calculate(transactions, juneWindow, zone)

        assertThat(data.netByDay[14]).isEqualTo(BigDecimal("100.00"))
        assertThat(data.netByDay.filterIndexed { i, _ -> i != 14 }.all { it == BigDecimal.ZERO }).isTrue()
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

        // net = 100 - (300 + 200) = -400
        assertThat(data.netByDay[19]).isEqualTo(BigDecimal("-400.00"))
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
