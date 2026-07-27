package com.iponlove.app.feature.settings.presentation

/** Notifications sub-screen. Instant, undrafted write-through — no Apply gate. */
data class NotificationsUiState(
    /**
     * The **Budgets category** switch (ADR-0053 decision 5): off means silent everywhere for
     * budget alerts — no inbox row and no OS push. Also the master gate for the warn/over
     * sliders below (ADR-0054 decision 1) — they stay visible but inactive when this is off.
     */
    val budgetAlertsEnabled: Boolean = true,
    /** The single warn rung, applies to every budget — 5-100% in 5% steps, default 80. */
    val budgetWarnThresholdPercent: Int = 80,
    /** The opt-in `over` rung's own on/off — default OFF (ADR-0054 decision 2). */
    val budgetOverAlertsEnabled: Boolean = false,
    /** The `over` rung's threshold — 110-300% in 10% steps, default 120. */
    val budgetOverThresholdPercent: Int = 120,
    /** Recurring due-date reminders (ADR-0052 decision 4) — one combined toggle, default ON. */
    val recurringRemindersEnabled: Boolean = true,
    /** "Partner logs a new debt" alerts (Item 9 grill) — default ON. */
    val partnerDebtAlertsEnabled: Boolean = true,
    /** Gates the Couple section entirely — hidden while unpaired, matching the Debt Tracker
     *  itself being paired-only. */
    val isPaired: Boolean = false,
)
