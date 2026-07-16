package com.iponlove.app.feature.settings.presentation

/** Notifications sub-screen (v1.6.6 Item 7). Instant, undrafted write-through — no Apply gate. */
data class NotificationsUiState(
    /** Gates [com.iponlove.app.feature.budgets.presentation.BudgetAlertNotifier]'s posts. */
    val budgetAlertsEnabled: Boolean = true,
)
