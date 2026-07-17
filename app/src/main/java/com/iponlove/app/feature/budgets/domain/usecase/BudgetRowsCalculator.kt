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
 * **Cycle:** personal budgets honour the user's payday-aligned "budget month starts on" [startDay]
 * (ADR-0046); **shared budgets always use calendar months** ([CALENDAR_START_DAY]) — a jointly-owned
 * budget can't follow one partner's personal payday (which differs per device), and this preserves
 * the pre-Item-35 overall-shared-budget behaviour (it was always a calendar-month window).
 */
object BudgetRowsCalculator {

    /** Shared budgets are calendar-month; see the class doc. */
    const val CALENDAR_START_DAY = 1

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
        startDay: Int = 1,
    ): List<Row> {
        val personalRows = personalBudgets
            .filter { it.yearMonth == monthKey }
            .map { budget ->
                row(budget, personalBudgets, ownTransactions, categoryNames, zone, startDay)
            }
        val sharedRows = sharedBudgets
            .filter { it.yearMonth == monthKey }
            .map { budget ->
                row(budget, sharedBudgets, combinedTransactions, categoryNames, zone, CALENDAR_START_DAY)
            }
        return personalRows + sharedRows
    }

    private fun row(
        budget: Budget,
        sameScopeBudgets: List<Budget>,
        transactions: List<Transaction>,
        categoryNames: Map<String, String>,
        zone: ZoneId,
        startDay: Int,
    ): Row {
        val spent = BudgetProgressCalculator.spent(budget, transactions, zone, startDay)
        val sameCategoryBudgets = sameScopeBudgets.filter { it.categoryId == budget.categoryId }
        val limit = BudgetProgressCalculator.effectiveLimit(budget, sameCategoryBudgets, transactions, zone, startDay)
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
