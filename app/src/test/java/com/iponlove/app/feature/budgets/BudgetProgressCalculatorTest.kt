package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/** Budget "spent" is derived from the expense ledger for the budget's month. */
class BudgetProgressCalculatorTest {

    private val zone = ZoneOffset.UTC
    private fun june(day: Int) = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")
    private val may = Instant.parse("2026-05-15T12:00:00Z")

    @Test
    fun categoryBudget_sumsOnlyThatCategorysExpensesInThatMonth() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1000.00", categoryId = "cat-1", date = june(5)),
            txn("t2", TransactionType.EXPENSE, "500.00", categoryId = "cat-1", date = june(10)),
            txn("t3", TransactionType.EXPENSE, "999.00", categoryId = "cat-2", date = june(11)),
            txn("t4", TransactionType.EXPENSE, "888.00", categoryId = "cat-1", date = may),
            txn("t5", TransactionType.INCOME, "2000.00", categoryId = "cat-1", date = june(12)),
        )

        val spent = BudgetProgressCalculator.spent(
            budget = budget("b", categoryId = "cat-1", yearMonth = "2026-06"),
            transactions = transactions,
            zone = zone,
        )

        assertThat(spent).isEqualTo(BigDecimal("1500.00"))
    }

    @Test
    fun overallBudget_sumsEveryExpenseInThatMonth_regardlessOfCategory() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1000.00", categoryId = "cat-1", date = june(5)),
            txn("t2", TransactionType.EXPENSE, "500.00", categoryId = "cat-2", date = june(10)),
            txn("t3", TransactionType.EXPENSE, "888.00", categoryId = "cat-1", date = may),
        )

        val spent = BudgetProgressCalculator.spent(
            budget = budget("b", categoryId = null, yearMonth = "2026-06"),
            transactions = transactions,
            zone = zone,
        )

        assertThat(spent).isEqualTo(BigDecimal("1500.00"))
    }

    @Test
    fun ignoresIncomeAndTransfers() {
        val transactions = listOf(
            txn("t1", TransactionType.INCOME, "5000.00", categoryId = "cat-1", date = june(5)),
            txn("t2", TransactionType.TRANSFER, "300.00", accountId = "acc-1", toAccountId = "acc-2", date = june(6)),
        )

        val spent = BudgetProgressCalculator.spent(
            budget = budget("b", categoryId = null, yearMonth = "2026-06"),
            transactions = transactions,
            zone = zone,
        )

        assertThat(spent).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun noMatchingTransactions_returnsZero() {
        val transactions = listOf(
            txn("t1", TransactionType.EXPENSE, "1000.00", categoryId = "cat-9", date = june(5)),
        )

        val spent = BudgetProgressCalculator.spent(
            budget = budget("b", categoryId = "cat-1", yearMonth = "2026-06"),
            transactions = transactions,
            zone = zone,
        )

        assertThat(spent).isEqualTo(BigDecimal.ZERO)
    }
}
