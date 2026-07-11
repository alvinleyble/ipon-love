package com.iponlove.app.feature.budgets.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.categories.domain.model.Category
import java.math.BigDecimal

/** Screen state for the Budgets tab — always for one month at a time. */
data class BudgetsUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    /** Month name only (e.g. "July") for the "Duplicate for <month>" overflow action. */
    val nextMonthShortLabel: String = "",
    val rows: List<BudgetRow> = emptyList(),
    /** Expense categories offered in the editor (plus an "Overall" option). */
    val expenseCategories: List<Category> = emptyList(),
    val editor: BudgetEditorState? = null,
    /** Non-null while the count-cap upsell sheet is showing (S7; only ever set under enforcement). */
    val upsell: UpsellPrompt? = null,
    /**
     * Whether the rollover toggle is locked (S9 — `Feature.BUDGET_ROLLOVER`, individual scope):
     * enforcement ON and no premium. The editor shows a locked switch that routes to the paywall
     * instead of toggling. False while dormant, so the toggle works exactly as before pre-flip.
     */
    val rolloverLocked: Boolean = false,
)

/** One budget with its derived progress for the displayed month. */
data class BudgetRow(
    val id: String,
    val categoryId: String?,
    val title: String,
    val spent: BigDecimal,
    /** This month's own configured limit, before any rollover carry. */
    val baseAmount: BigDecimal,
    /** Rollover-adjusted limit (ADR-0036) — what [spent]/[remaining]/[fraction] are computed against. */
    val limit: BigDecimal,
    val remaining: BigDecimal,
    /** 0f..1f, clamped, for the progress bar. */
    val fraction: Float,
    val isOverBudget: Boolean,
    val rolloverEnabled: Boolean,
    /** [limit] minus [baseAmount] — positive if carried leftover, negative if carried deficit. */
    val carriedAmount: BigDecimal,
)

/** Editor form state. [categoryId] null means an overall budget. */
data class BudgetEditorState(
    val id: String? = null,
    val categoryId: String? = null,
    val amountText: String = "",
    val amountError: Boolean = false,
    val rolloverEnabled: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
}
