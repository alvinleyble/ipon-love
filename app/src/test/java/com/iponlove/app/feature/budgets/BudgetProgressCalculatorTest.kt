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

    // ---- effectiveLimit (rollover, ADR-0036) ----

    @Test
    fun effectiveLimit_rolloverDisabled_equalsOwnAmount() {
        val b = budget("b", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = false)

        val limit = BudgetProgressCalculator.effectiveLimit(b, listOf(b), emptyList(), zone)

        assertThat(limit).isEqualTo(BigDecimal("5000.00"))
    }

    @Test
    fun effectiveLimit_rolloverEnabled_noPreviousMonthRow_equalsOwnAmount() {
        val b = budget("b", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)

        val limit = BudgetProgressCalculator.effectiveLimit(b, listOf(b), emptyList(), zone)

        assertThat(limit).isEqualTo(BigDecimal("5000.00"))
    }

    @Test
    fun effectiveLimit_chainsLeftoverAndDeficitAcrossConsecutiveMonths() {
        val juneBudget = budget("june", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)
        val julyBudget = budget("july", amount = "4000.00", yearMonth = "2026-07", rolloverEnabled = true)
        val augustBudget = budget("august", amount = "5000.00", yearMonth = "2026-08", rolloverEnabled = true)
        val all = listOf(juneBudget, julyBudget, augustBudget)
        val transactions = listOf(
            txn("t-june", TransactionType.EXPENSE, "3000.00", categoryId = "cat-1", date = june(15)),
            txn("t-july", TransactionType.EXPENSE, "6500.00", categoryId = "cat-1", date = Instant.parse("2026-07-15T12:00:00Z")),
        )

        // June: 5000 limit, 3000 spent -> 2000 leftover carries forward.
        assertThat(BudgetProgressCalculator.effectiveLimit(juneBudget, all, transactions, zone))
            .isEqualTo(BigDecimal("5000.00"))
        // July: 4000 + 2000 carried = 6000 effective limit; 6500 spent -> 500 overspend carries forward.
        assertThat(BudgetProgressCalculator.effectiveLimit(julyBudget, all, transactions, zone))
            .isEqualTo(BigDecimal("6000.00"))
        // August: 5000 + (6000 - 6500) = 4500.
        assertThat(BudgetProgressCalculator.effectiveLimit(augustBudget, all, transactions, zone))
            .isEqualTo(BigDecimal("4500.00"))
    }

    @Test
    fun effectiveLimit_gapMonthBreaksChain() {
        val juneBudget = budget("june", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)
        // No July row at all — the chain must not skip through the gap to reach June's leftover.
        val augustBudget = budget("august", amount = "4000.00", yearMonth = "2026-08", rolloverEnabled = true)
        val all = listOf(juneBudget, augustBudget)
        val transactions = listOf(
            txn("t-june", TransactionType.EXPENSE, "3000.00", categoryId = "cat-1", date = june(15)),
        )

        val limit = BudgetProgressCalculator.effectiveLimit(augustBudget, all, transactions, zone)

        assertThat(limit).isEqualTo(BigDecimal("4000.00"))
    }

    @Test
    fun effectiveLimit_carriedDeficitHasNoFloor() {
        val juneBudget = budget("june", amount = "3000.00", yearMonth = "2026-06", rolloverEnabled = true)
        val julyBudget = budget("july", amount = "1000.00", yearMonth = "2026-07", rolloverEnabled = true)
        val all = listOf(juneBudget, julyBudget)
        val transactions = listOf(
            txn("t-june", TransactionType.EXPENSE, "5000.00", categoryId = "cat-1", date = june(15)),
        )

        val limit = BudgetProgressCalculator.effectiveLimit(julyBudget, all, transactions, zone)

        // 1000 + (3000 - 5000) = -1000 — a real negative limit, never clamped to zero.
        assertThat(limit).isEqualTo(BigDecimal("-1000.00"))
    }
}
