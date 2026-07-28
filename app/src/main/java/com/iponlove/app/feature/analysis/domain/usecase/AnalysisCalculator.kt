package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.AnalysisResult
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.model.CategorySpend
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Aggregates the transaction ledger into an [AnalysisResult] for one [AnalysisWindow].
 *
 * Only transactions whose date is in [AnalysisWindow.startInclusive, endExclusive) count.
 * INCOME and EXPENSE roll into their totals; TRANSFER is ignored entirely (it moves money
 * between the user's own accounts — neither income nor expense, consistent with
 * [com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator]). Debt-settlement
 * legs (`is_settlement`) are also ignored — they repay a partner debt, not real spend/income
 * (ADR-0019 #14). Private
 * transactions are included — this is the user's own view; privacy only redacts the
 * partner/combined view (ADR-0004).
 */
object AnalysisCalculator {

    fun analyze(transactions: List<Transaction>, window: AnalysisWindow): AnalysisResult {
        var income = BigDecimal.ZERO
        var expense = BigDecimal.ZERO
        // Insertion order is stable but irrelevant — slices are sorted by amount below.
        val byCategory = LinkedHashMap<String?, BigDecimal>()

        for (t in transactions) {
            if (t.date < window.startInclusive || t.date >= window.endExclusive) continue
            // Debt-settlement legs (ADR-0019 #14) and balance corrections (ADR-0057) move money
            // but aren't spending/income.
            if (t.isSettlement || t.isAdjustment) continue
            when (t.type) {
                TransactionType.INCOME -> income += t.amount
                TransactionType.EXPENSE -> {
                    expense += t.amount
                    byCategory[t.categoryId] = (byCategory[t.categoryId] ?: BigDecimal.ZERO) + t.amount
                }
                TransactionType.TRANSFER -> Unit
            }
        }

        val slices = byCategory.entries
            .map { (categoryId, amount) ->
                CategorySpend(
                    categoryId = categoryId,
                    amount = amount,
                    fraction = if (expense.signum() > 0) amount.toFloat() / expense.toFloat() else 0f,
                )
            }
            .sortedByDescending { it.amount }

        return AnalysisResult(
            totalIncome = income,
            totalExpense = expense,
            net = income - expense,
            expenseByCategory = slices,
        )
    }
}
