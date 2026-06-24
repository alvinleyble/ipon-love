package com.iponlove.app.feature.budgets.presentation

import com.iponlove.app.feature.categories.domain.model.Category
import java.math.BigDecimal

/** Screen state for the Budgets tab — always for one month at a time. */
data class BudgetsUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    val rows: List<BudgetRow> = emptyList(),
    /** Expense categories offered in the editor (plus an "Overall" option). */
    val expenseCategories: List<Category> = emptyList(),
    val editor: BudgetEditorState? = null,
)

/** One budget with its derived progress for the displayed month. */
data class BudgetRow(
    val id: String,
    val categoryId: String?,
    val title: String,
    val spent: BigDecimal,
    val limit: BigDecimal,
    val remaining: BigDecimal,
    /** 0f..1f, clamped, for the progress bar. */
    val fraction: Float,
    val isOverBudget: Boolean,
)

/** Editor form state. [categoryId] null means an overall budget. */
data class BudgetEditorState(
    val id: String? = null,
    val categoryId: String? = null,
    val amountText: String = "",
    val amountError: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
}
