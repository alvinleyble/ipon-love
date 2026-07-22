package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.transactions.domain.model.Transaction
import java.math.BigDecimal
import java.time.ZoneId

/**
 * Merges the couple's **personal** and **shared** budgets into one per-month row list for the
 * Budgets tab (Item 35), computing each row's progress against the right spend source:
 * a personal budget counts the user's own transactions; a shared budget counts **both** members'
 * combined non-private transactions. Rollover chains stay within a single scope — a shared row
 * chains only with shared rows of its category, a personal row only with personal ones — so a
 * scope-crossing chain can never form.
 *
 * Pure (no Android / no coroutines) so the merge + scope-pick is JVM-unit-testable. Personal rows
 * come first, shared rows after (each tagged [Row.isShared] for the "Shared" badge).
 *
 * Both personal and shared budgets are calendar-month.
 */
object BudgetRowsCalculator {

    /** One budget with its derived progress for the displayed month — presentation-agnostic. */
    data class Row(
        val id: String,
        val categoryId: String?,
        val title: String,
        val spent: BigDecimal,
        val baseAmount: BigDecimal,
        val limit: BigDecimal,
        val remaining: BigDecimal,
        val fraction: Float,
        val isOverBudget: Boolean,
        val rolloverEnabled: Boolean,
        val carriedAmount: BigDecimal,
        val isShared: Boolean,
    )

    fun build(
        personalBudgets: List<Budget>,
        sharedBudgets: List<Budget>,
        ownTransactions: List<Transaction>,
        combinedTransactions: List<Transaction>,
        categoryNames: Map<String, String>,
        monthKey: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Row> {
        val personalRows = personalBudgets
            .filter { it.yearMonth == monthKey }
            .map { budget ->
                row(budget, personalBudgets, ownTransactions, categoryNames, zone)
            }
        val sharedRows = sharedBudgets
            .filter { it.yearMonth == monthKey }
            .map { budget ->
                row(budget, sharedBudgets, combinedTransactions, categoryNames, zone)
            }
        return personalRows + sharedRows
    }

    private fun row(
        budget: Budget,
        sameScopeBudgets: List<Budget>,
        transactions: List<Transaction>,
        categoryNames: Map<String, String>,
        zone: ZoneId,
    ): Row {
        val spent = BudgetProgressCalculator.spent(budget, transactions, zone)
        val sameCategoryBudgets = sameScopeBudgets.filter { it.categoryId == budget.categoryId }
        val limit = BudgetProgressCalculator.effectiveLimit(budget, sameCategoryBudgets, transactions, zone)
        val fraction = if (limit.signum() > 0) {
            (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return Row(
            id = budget.id,
            categoryId = budget.categoryId,
            title = budget.categoryId?.let { categoryNames[it] ?: "Category" } ?: "Overall",
            spent = spent,
            baseAmount = budget.amount,
            limit = limit,
            remaining = limit - spent,
            fraction = fraction,
            isOverBudget = spent > limit,
            rolloverEnabled = budget.rolloverEnabled,
            carriedAmount = limit - budget.amount,
            isShared = budget.isShared,
        )
    }
}
