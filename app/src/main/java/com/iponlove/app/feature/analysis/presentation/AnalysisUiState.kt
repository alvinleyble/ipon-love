package com.iponlove.app.feature.analysis.presentation

import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import java.math.BigDecimal

/** Screen state for the Analysis tab — one [period] window at a time. */
data class AnalysisUiState(
    val isLoading: Boolean = true,
    val period: AnalysisPeriod = AnalysisPeriod.MONTH,
    /** Human label for the current window, e.g. "June 2026" or "Jun 22 – Jun 28". */
    val periodLabel: String = "",
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val net: BigDecimal = BigDecimal.ZERO,
    /** Expense breakdown, largest first; drives both the donut and its legend. */
    val slices: List<CategorySliceUi> = emptyList(),
) {
    val hasExpenses: Boolean get() = totalExpense.signum() > 0
}

/**
 * One expense slice for display. [colorHex] is the category's stored color (may be null —
 * the screen falls back to a generated palette by position); [fraction] is 0f..1f.
 */
data class CategorySliceUi(
    val categoryId: String?,
    val name: String,
    val colorHex: String?,
    val amount: BigDecimal,
    val fraction: Float,
    val percentLabel: String,
)
