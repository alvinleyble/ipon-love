package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisCalculator
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Analysis aggregations are derived purely from the ledger for one time window. */
class AnalysisCalculatorTest {

    private val zone = ZoneOffset.UTC
    private fun june(day: Int) = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")

    // June 2026 window, used by most cases.
    private val juneWindow =
        AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 15), AnalysisPeriod.MONTH, zone)

    @Test
    fun totals_sumIncomeAndExpense_andNetIsTheDifference() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", date = june(2)),
            txn("t2", TransactionType.EXPENSE, "1200.00", categoryId = "cat-1", date = june(3)),
            txn("t3", TransactionType.EXPENSE, "800.00", categoryId = "cat-2", date = june(4)),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.totalIncome).isEqualTo(BigDecimal("5000.00"))
        assertThat(result.totalExpense).isEqualTo(BigDecimal("2000.00"))
        assertThat(result.net).isEqualTo(BigDecimal("3000.00"))
    }

    @Test
    fun net_canBeNegativeWhenSpendingExceedsIncome() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "1000.00", date = june(2)),
            txn("t2", TransactionType.EXPENSE, "1500.00", categoryId = "cat-1", date = june(3)),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.net).isEqualTo(BigDecimal("-500.00"))
    }

    @Test
    fun transfersAreIgnoredEverywhere() {
        val transactions = listOf(
            txn("t1", TransactionType.TRANSFER, "300.00", accountId = "acc-1", toAccountId = "acc-2", date = june(5)),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.totalIncome).isEqualTo(BigDecimal.ZERO)
        assertThat(result.totalExpense).isEqualTo(BigDecimal.ZERO)
        assertThat(result.expenseByCategory).isEmpty()
    }

    @Test
    fun expenseBreakdown_groupsByCategory_sortedByAmountDesc_withFractions() {
        // cat-1 is entered first but totals less, so the desc sort must reorder it second.
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "100.00", categoryId = "cat-1", date = june(2)),
            txn("t2", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = june(3)),
            txn("t3", TransactionType.EXPENSE, "600.00", categoryId = "cat-2", date = june(4)),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.totalExpense).isEqualTo(BigDecimal("1000.00"))
        assertThat(result.expenseByCategory.map { it.categoryId }).containsExactly("cat-2", "cat-1").inOrder()
        assertThat(result.expenseByCategory[0].amount).isEqualTo(BigDecimal("600.00"))
        assertThat(result.expenseByCategory[1].amount).isEqualTo(BigDecimal("400.00"))
        assertThat(result.expenseByCategory[0].fraction).isWithin(1e-6f).of(0.6f)
        assertThat(result.expenseByCategory[1].fraction).isWithin(1e-6f).of(0.4f)
    }

    @Test
    fun uncategorizedExpenses_collapseIntoANullCategorySlice() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "100.00", categoryId = null, date = june(2)),
            txn("t2", TransactionType.EXPENSE, "100.00", categoryId = null, date = june(3)),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.expenseByCategory).hasSize(1)
        assertThat(result.expenseByCategory[0].categoryId).isNull()
        assertThat(result.expenseByCategory[0].amount).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun onlyTransactionsInsideTheWindowCount() {
        val transactions = listOf(
            txn("before", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-05-31T23:59:59Z")),
            txn("in", TransactionType.EXPENSE, "100.00", categoryId = "cat-1", date = june(1)),
            txn("after", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = Instant.parse("2026-07-01T00:00:00Z")),
        )

        val result = AnalysisCalculator.analyze(transactions, juneWindow)

        assertThat(result.totalExpense).isEqualTo(BigDecimal("100.00"))
    }

    @Test
    fun windowIsHalfOpen_startInclusive_endExclusive() {
        // Day window for June 10: [Jun 10 00:00, Jun 11 00:00).
        val dayWindow = AnalysisPeriodRange.windowFor(LocalDate.of(2026, 6, 10), AnalysisPeriod.DAY, zone)
        val transactions = listOf(
            txn("start", TransactionType.EXPENSE, "10.00", categoryId = "cat-1", date = Instant.parse("2026-06-10T00:00:00Z")),
            txn("end", TransactionType.EXPENSE, "20.00", categoryId = "cat-1", date = Instant.parse("2026-06-11T00:00:00Z")),
        )

        val result = AnalysisCalculator.analyze(transactions, dayWindow)

        // Start instant is included; the next-day boundary is excluded.
        assertThat(result.totalExpense).isEqualTo(BigDecimal("10.00"))
    }

    @Test
    fun emptyLedger_returnsZeroesAndNoSlices() {
        val result = AnalysisCalculator.analyze(emptyList(), juneWindow)

        assertThat(result.totalIncome).isEqualTo(BigDecimal.ZERO)
        assertThat(result.totalExpense).isEqualTo(BigDecimal.ZERO)
        assertThat(result.net).isEqualTo(BigDecimal.ZERO)
        assertThat(result.expenseByCategory).isEmpty()
    }
}
