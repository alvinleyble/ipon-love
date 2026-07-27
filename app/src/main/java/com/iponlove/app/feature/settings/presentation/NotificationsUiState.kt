package com.iponlove.app.feature.settings.presentation

/** Notifications sub-screen (v1.6.6 Item 7). Instant, undrafted write-through — no Apply gate. */
data class NotificationsUiState(
    /**
     * The **Budgets category** switch (ADR-0053 decision 5): off means silent everywhere for
     * budget alerts — no inbox row and no OS push.
     */
    val budgetAlertsEnabled: Boolean = true,
    /** Recurring due-date reminders (ADR-0052 decision 4) — one combined toggle, default ON. */
    val recurringRemindersEnabled: Boolean = true,
)
