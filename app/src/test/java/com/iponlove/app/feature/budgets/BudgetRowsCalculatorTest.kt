package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.BudgetRowsCalculator
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * The personal+shared merge (Item 35). The load-bearing rules: a personal budget counts the user's
 * own transactions while a shared budget counts the combined (both-partner) stream; both scopes
 * appear tagged; and rollover chains never cross scope.
 */
class BudgetRowsCalculatorTest {

    private val zone = ZoneOffset.UTC
    private fun jun(day: Int) = Instant.parse("2026-06-${"%02d".format(day)}T12:00:00Z")
    private fun may(day: Int) = Instant.parse("2026-05-${"%02d".format(day)}T12:00:00Z")
    private val names = mapOf("cat-1" to "Groceries")

    @Test
    fun personalCountsOwnTransactions_sharedCountsCombined_bothTaggedAndOrdered() {
        val ownTxns = listOf(
            txn("o1", TransactionType.EXPENSE, "500.00", categoryId = "cat-1", date = jun(5)),
        )
        val combinedTxns = listOf(
            txn("o1", TransactionType.EXPENSE, "500.00", categoryId = "cat-1", date = jun(5)),   // own
            txn("p1", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = jun(6)),   // partner
        )
        val rows = BudgetRowsCalculator.build(
            personalBudgets = listOf(budget("bp", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-06")),
            sharedBudgets = listOf(budget("bs", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-06", isShared = true)),
            ownTransactions = ownTxns,
            combinedTransactions = combinedTxns,
            categoryNames = names,
            monthKey = "2026-06",
            zone = zone,
        )

        assertThat(rows.map { it.id }).containsExactly("bp", "bs").inOrder() // personal first, shared after
        val personal = rows.first { it.id == "bp" }
        val shared = rows.first { it.id == "bs" }
        assertThat(personal.isShared).isFalse()
        assertThat(personal.spent).isEqualTo(BigDecimal("500.00"))          // own only
        assertThat(shared.isShared).isTrue()
        assertThat(shared.spent).isEqualTo(BigDecimal("800.00"))           // both partners
        assertThat(shared.title).isEqualTo("Groceries")
    }

    @Test
    fun sharedRolloverChainsOnlyWithSharedBudgets_notPersonalOfSameCategory() {
        val combinedTxns = listOf(
            txn("c1", TransactionType.EXPENSE, "400.00", categoryId = "cat-1", date = may(10)), // May combined spend
        )
        // A same-category *personal* May budget must not feed the shared June chain.
        val personal = listOf(
            budget("pMay", categoryId = "cat-1", amount = "9999.00", yearMonth = "2026-05"),
        )
        val shared = listOf(
            budget("sMay", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-05", isShared = true),
            budget("sJun", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-06", rolloverEnabled = true, isShared = true),
        )
        val rows = BudgetRowsCalculator.build(
            personalBudgets = personal,
            sharedBudgets = shared,
            ownTransactions = emptyList(),
            combinedTransactions = combinedTxns,
            categoryNames = names,
            monthKey = "2026-06",
            zone = zone,
        )

        // June shared limit = own 1000 + (May limit 1000 − May combined spend 400) = 1600.
        val june = rows.single()
        assertThat(june.id).isEqualTo("sJun")
        assertThat(june.limit).isEqualTo(BigDecimal("1600.00"))
    }

    @Test
    fun onlyDisplayedMonthRowsAreReturned() {
        val rows = BudgetRowsCalculator.build(
            personalBudgets = listOf(
                budget("jun", categoryId = "cat-1", yearMonth = "2026-06"),
                budget("may", categoryId = "cat-1", yearMonth = "2026-05"),
            ),
            sharedBudgets = emptyList(),
            ownTransactions = emptyList(),
            combinedTransactions = emptyList(),
            categoryNames = names,
            monthKey = "2026-06",
            zone = zone,
        )
        assertThat(rows.map { it.id }).containsExactly("jun")
    }
}
