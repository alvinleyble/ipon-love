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
    /**
     * Opt-in off-app delivery for the recurring reminder (ADR-0056 decision 7) — **default OFF**.
     * Sub-row of [recurringRemindersEnabled]: greyed out, not hidden, when that master is off, so
     * its stored value survives and resumes when the master comes back on.
     */
    val offAppRecurringRemindersEnabled: Boolean = false,
    /**
     * OS-level notifications are denied, so every switch on this screen is a silent no-op
     * (ADR-0056 decision 9). Drives the screen-level banner; re-read on every resume.
     */
    val notificationsBlocked: Boolean = false,
    /** "Partner logs a new debt" alerts (Item 9 grill) — default ON. */
    val partnerDebtAlertsEnabled: Boolean = true,
    /** Gates the Couple section entirely — hidden while unpaired, matching the Debt Tracker
     *  itself being paired-only. */
    val isPaired: Boolean = false,
)
