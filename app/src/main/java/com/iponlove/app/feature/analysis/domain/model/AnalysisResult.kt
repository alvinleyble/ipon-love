package com.iponlove.app.feature.analysis.domain.model

import java.math.BigDecimal

/**
 * Aggregated spending/earning for one [AnalysisWindow], derived purely from the
 * transaction ledger (ADR-0007 — nothing here is stored or synced). Transfers are
 * excluded from every total: they move money between the user's own accounts and are
 * neither income nor expense.
 */
data class AnalysisResult(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    /** [totalIncome] − [totalExpense]; can be negative. */
    val net: BigDecimal,
    /** Expense categories for the window, largest spend first. */
    val expenseByCategory: List<CategorySpend>,
)

/**
 * One slice of the expense breakdown. [categoryId] is null for expenses left
 * uncategorized. [fraction] is this slice's share of [AnalysisResult.totalExpense],
 * in 0f..1f (0f when there were no expenses).
 */
data class CategorySpend(
    val categoryId: String?,
    val amount: BigDecimal,
    val fraction: Float,
)
