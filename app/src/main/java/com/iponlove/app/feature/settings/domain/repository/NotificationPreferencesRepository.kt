package com.iponlove.app.feature.settings.domain.repository

import kotlinx.coroutines.flow.Flow

/** Local-only — mirrors [PrivacyModeRepository]'s shape. Defaults to enabled when unset. */
interface NotificationPreferencesRepository {
    fun observeBudgetAlertsEnabled(): Flow<Boolean>
    suspend fun setBudgetAlertsEnabled(enabled: Boolean)

    /** Recurring due-date reminders (ADR-0052 decision 4) — one combined toggle, default ON. */
    fun observeRecurringRemindersEnabled(): Flow<Boolean>
    suspend fun setRecurringRemindersEnabled(enabled: Boolean)
}
