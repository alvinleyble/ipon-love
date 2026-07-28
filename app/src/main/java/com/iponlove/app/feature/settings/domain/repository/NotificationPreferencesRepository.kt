package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/** Local-only — mirrors [PrivacyModeRepository]'s shape. Defaults to enabled when unset. */
interface NotificationPreferencesRepository {
    fun observeBudgetAlertsEnabled(): Flow<Boolean>
    suspend fun setBudgetAlertsEnabled(enabled: Boolean)

    /** Recurring due-date reminders (ADR-0052 decision 4) — one combined toggle, default ON. */
    fun observeRecurringRemindersEnabled(): Flow<Boolean>
    suspend fun setRecurringRemindersEnabled(enabled: Boolean)

    /**
     * Opt-in off-app delivery for the recurring reminder (ADR-0056 decision 7) — **default OFF**.
     * When on, a periodic sweep wakes the app to check; when off the periodic work does not exist
     * at all rather than existing-and-suppressed. Device-global like every other preference here.
     */
    fun observeOffAppRecurringRemindersEnabled(): Flow<Boolean>
    suspend fun setOffAppRecurringRemindersEnabled(enabled: Boolean)

    /** "Partner logs a new debt" alerts (Item 9 grill) — one toggle, default ON. */
    fun observePartnerDebtAlertsEnabled(): Flow<Boolean>
    suspend fun setPartnerDebtAlertsEnabled(enabled: Boolean)

    /** The single warn rung, applies to every budget (ADR-0054 decision 2) — 5-100%, default 80. */
    fun observeBudgetWarnThresholdPercent(): Flow<Int>
    suspend fun setBudgetWarnThresholdPercent(percent: Int)

    /** The opt-in `over` rung's own on/off (ADR-0054 decision 2) — default OFF. */
    fun observeBudgetOverAlertsEnabled(): Flow<Boolean>
    suspend fun setBudgetOverAlertsEnabled(enabled: Boolean)

    /** The `over` rung's threshold — 110-300%, default 120. */
    fun observeBudgetOverThresholdPercent(): Flow<Int>
    suspend fun setBudgetOverThresholdPercent(percent: Int)

    /**
     * Budget lines (keyed by [com.iponlove.app.feature.budgets.domain.usecase.BudgetLineId])
     * muted from all three rungs (ADR-0054 decisions 6-8) — persists across month rollover.
     */
    fun observeMutedBudgetLines(): Flow<Set<String>>
    suspend fun setBudgetLineMuted(lineId: String, muted: Boolean)
}
