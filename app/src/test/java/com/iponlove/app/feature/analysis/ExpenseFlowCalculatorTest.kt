package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
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
    private val reg = LocalDate.of(2026, 1, 1)

    private fun june(day: Int): Instant = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")

    private fun windowFor(anchor: LocalDate, period: AnalysisPeriod) =
        AnalysisPeriodRange.windowFor(anchor, period, zone)

    private val juneWindow = windowFor(LocalDate.of(2026, 6, 15), AnalysisPeriod.MONTH)

    // ─── Daily bucketing (short ranges) ─────────────────────────────────────

    @Test
    fun month_emptyLedger_returnsAllZeroDailyBuckets() {
        val data = ExpenseFlowCalculator.calculate(emptyList(), juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.bucketMode).isEqualTo(FlowBucketMode.DAILY)
        assertThat(data.cumulativeByBucket).hasSize(30)
        assertThat(data.cumulativeByBucket.all { it == BigDecimal.ZERO }).isTrue()
    }

    @Test
    fun month_singleExpense_accumulatesFromThatDayOnward() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1000.00", categoryId = "cat-1", date = june(5)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket[3]).isEqualTo(BigDecimal.ZERO) // day 4
        assertThat(data.cumulativeByBucket[4]).isEqualTo(BigDecimal("1000.00")) // day 5
        assertThat(data.cumulativeByBucket[29]).isEqualTo(BigDecimal("1000.00")) // day 30
    }

    @Test
    fun month_multipleExpenses_cumulativeSumsCorrectly() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "500.00", categoryId = "cat-1", date = june(1)),
            txn("t2", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = june(1)),
            txn("t3", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = june(10)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket[0]).isEqualTo(BigDecimal("800.00"))
        assertThat(data.cumulativeByBucket[8]).isEqualTo(BigDecimal("800.00"))
        assertThat(data.cumulativeByBucket[9]).isEqualTo(BigDecimal("1000.00"))
        assertThat(data.cumulativeByBucket[29]).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun incomeAndTransferAreIgnored() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", date = june(1)),
            txn("t2", TransactionType.TRANSFER, "1000.00", accountId = "a1", toAccountId = "a2", date = june(5)),
            txn("t3", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = june(3)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket[29]).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun settlementExpensesAreExcluded() {
        val transactions = listOf(
            txn("real", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = june(3)),
            txn("settle", TransactionType.EXPENSE, "900.00", categoryId = null, date = june(5), isSettlement = true),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket[29]).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun transactionsOutsideWindowAreIgnored() {
        val transactions = listOf(
            txn("before", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-05-31T23:59:59Z")),
            txn("in", TransactionType.EXPENSE, "100.00", categoryId = "cat-1", date = june(15)),
            txn("after", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-07-01T00:00:00Z")),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket[29]).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun currentBucketIndex_isNullForPastMonth() {
        val data = ExpenseFlowCalculator.calculate(emptyList(), juneWindow, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.currentBucketIndex).isNull()
    }

    @Test
    fun currentBucketIndex_isTodaysDayIndexWhenViewingCurrentMonth() {
        val data = ExpenseFlowCalculator.calculate(emptyList(), juneWindow, zone, reg, LocalDate.of(2026, 6, 20))

        assertThat(data.currentBucketIndex).isEqualTo(19) // day 20 → index 19
    }

    @Test
    fun week_producesSevenDailyBuckets() {
        val window = windowFor(LocalDate.of(2026, 6, 24), AnalysisPeriod.WEEK) // Mon 6/22 .. Sun 6/28
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "50.00", categoryId = "cat-1", date = june(23)),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, window, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.bucketMode).isEqualTo(FlowBucketMode.DAILY)
        assertThat(data.cumulativeByBucket).hasSize(7)
        assertThat(data.cumulativeByBucket[0]).isEqualTo(BigDecimal.ZERO) // Mon 6/22
        assertThat(data.cumulativeByBucket[1]).isEqualTo(BigDecimal("50.00")) // Tue 6/23 onward
        assertThat(data.cumulativeByBucket[6]).isEqualTo(BigDecimal("50.00"))
    }

    @Test
    fun day_producesSingleBucket() {
        val window = windowFor(LocalDate.of(2026, 6, 24), AnalysisPeriod.DAY)
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "75.00", categoryId = "cat-1", date = Instant.parse("2026-06-24T09:00:00Z")),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, window, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.cumulativeByBucket).hasSize(1)
        assertThat(data.cumulativeByBucket[0]).isEqualTo(BigDecimal("75.00"))
    }

    // ─── Monthly bucketing (long ranges) ────────────────────────────────────

    @Test
    fun semiAnnual_producesSixMonthlyBuckets() {
        val window = windowFor(LocalDate.of(2026, 3, 10), AnalysisPeriod.SEMI_ANNUAL) // Jan..Jun 2026
        val transactions = listOf(
            txn("feb", TransactionType.EXPENSE, "400.00", categoryId = "cat-1", date = Instant.parse("2026-02-10T12:00:00Z")),
            txn("apr", TransactionType.EXPENSE, "600.00", categoryId = "cat-1", date = Instant.parse("2026-04-10T12:00:00Z")),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, window, zone, reg, LocalDate.of(2025, 1, 1))

        assertThat(data.bucketMode).isEqualTo(FlowBucketMode.MONTHLY)
        assertThat(data.cumulativeByBucket).hasSize(6)
        assertThat(data.cumulativeByBucket[0]).isEqualTo(BigDecimal.ZERO)        // Jan
        assertThat(data.cumulativeByBucket[1]).isEqualTo(BigDecimal("400.00"))   // Feb
        assertThat(data.cumulativeByBucket[3]).isEqualTo(BigDecimal("1000.00"))  // Apr (400+600)
        assertThat(data.cumulativeByBucket[5]).isEqualTo(BigDecimal("1000.00"))  // Jun
    }

    // ─── ALL_TIME (registration-anchored monthly range) ─────────────────────

    @Test
    fun allTime_bucketsMonthlyFromRegistrationThroughToday() {
        val window = windowFor(LocalDate.of(2026, 8, 10), AnalysisPeriod.ALL_TIME) // anchor ignored
        val registration = LocalDate.of(2026, 4, 1)
        val today = LocalDate.of(2026, 6, 15)
        val transactions = listOf(
            txn("may", TransactionType.EXPENSE, "250.00", categoryId = "cat-1", date = Instant.parse("2026-05-05T12:00:00Z")),
        )

        val data = ExpenseFlowCalculator.calculate(transactions, window, zone, registration, today)

        assertThat(data.bucketMode).isEqualTo(FlowBucketMode.MONTHLY)
        assertThat(data.cumulativeByBucket).hasSize(3) // Apr, May, Jun
        assertThat(data.cumulativeByBucket[0]).isEqualTo(BigDecimal.ZERO)      // Apr
        assertThat(data.cumulativeByBucket[1]).isEqualTo(BigDecimal("250.00")) // May onward
        assertThat(data.cumulativeByBucket[2]).isEqualTo(BigDecimal("250.00")) // Jun
        assertThat(data.currentBucketIndex).isEqualTo(2) // today = Jun = last bucket
    }

    @Test
    fun allTime_nullRegistration_fallsBackToTodaySingleBucket() {
        val window = windowFor(LocalDate.of(2026, 8, 10), AnalysisPeriod.ALL_TIME)
        val today = LocalDate.of(2026, 6, 15)

        val data = ExpenseFlowCalculator.calculate(emptyList(), window, zone, registrationDate = null, today = today)

        assertThat(data.cumulativeByBucket).hasSize(1)
        assertThat(data.currentBucketIndex).isEqualTo(0)
    }
}
