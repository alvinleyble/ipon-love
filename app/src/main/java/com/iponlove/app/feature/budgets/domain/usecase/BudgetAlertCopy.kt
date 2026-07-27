package com.iponlove.app.feature.budgets.domain.usecase

/**
 * The user-facing wording for each budget alert rung, locked by ADR-0054 decision 10. Kept as a
 * pure object beside the detection logic (rather than inside the notifier) because the same
 * strings now go to two surfaces — the inbox row and the OS push — and must not drift apart.
 */
object BudgetAlertCopy {

    /** The budget's label: its category name, or "Overall Budget" for an overall budget. */
    fun label(categoryName: String?): String = categoryName ?: "Overall Budget"

    fun title(slot: BudgetAlertSlot, label: String, spentPercent: Int): String = when (slot) {
        BudgetAlertSlot.WARN -> "$label at $spentPercent%"
        BudgetAlertSlot.LIMIT -> "$label limit reached"
        BudgetAlertSlot.OVER -> "$label way over budget"
    }

    fun body(slot: BudgetAlertSlot, label: String, spentPercent: Int): String = when (slot) {
        BudgetAlertSlot.WARN -> "You've used $spentPercent% of your $label budget."
        BudgetAlertSlot.LIMIT -> "You've hit your $label budget for this month."
        BudgetAlertSlot.OVER -> "You've spent $spentPercent% of your $label budget."
    }
}
