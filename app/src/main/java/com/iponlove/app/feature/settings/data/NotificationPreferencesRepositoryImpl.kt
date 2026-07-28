package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.iponlove.app.feature.settings.di.FinanceDataStore
import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationPreferencesRepositoryImpl @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) : NotificationPreferencesRepository {

    override fun observeBudgetAlertsEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_BUDGET_ALERTS_ENABLED] ?: true }

    override suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_BUDGET_ALERTS_ENABLED] = enabled }
    }

    override fun observeRecurringRemindersEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_RECURRING_REMINDERS_ENABLED] ?: true }

    override suspend fun setRecurringRemindersEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_RECURRING_REMINDERS_ENABLED] = enabled }
    }

    // Default OFF (ADR-0056 decision 7): this is the first thing in the app that makes it wake
    // itself on a schedule, which should be a choice rather than something that appears in an update.
    override fun observeOffAppRecurringRemindersEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_OFF_APP_RECURRING_REMINDERS] ?: false }

    override suspend fun setOffAppRecurringRemindersEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_OFF_APP_RECURRING_REMINDERS] = enabled }
    }

    override fun observePartnerDebtAlertsEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PARTNER_DEBT_ALERTS_ENABLED] ?: true }

    override suspend fun setPartnerDebtAlertsEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_PARTNER_DEBT_ALERTS_ENABLED] = enabled }
    }

    override fun observeBudgetWarnThresholdPercent(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[KEY_BUDGET_WARN_THRESHOLD] ?: 80 }

    override suspend fun setBudgetWarnThresholdPercent(percent: Int) {
        dataStore.edit { p -> p[KEY_BUDGET_WARN_THRESHOLD] = percent }
    }

    override fun observeBudgetOverAlertsEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_BUDGET_OVER_ENABLED] ?: false }

    override suspend fun setBudgetOverAlertsEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_BUDGET_OVER_ENABLED] = enabled }
    }

    override fun observeBudgetOverThresholdPercent(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[KEY_BUDGET_OVER_THRESHOLD] ?: 120 }

    override suspend fun setBudgetOverThresholdPercent(percent: Int) {
        dataStore.edit { p -> p[KEY_BUDGET_OVER_THRESHOLD] = percent }
    }

    override fun observeMutedBudgetLines(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[KEY_MUTED_BUDGET_LINES] ?: emptySet() }

    override suspend fun setBudgetLineMuted(lineId: String, muted: Boolean) {
        dataStore.edit { p ->
            val current = p[KEY_MUTED_BUDGET_LINES] ?: emptySet()
            p[KEY_MUTED_BUDGET_LINES] = if (muted) current + lineId else current - lineId
        }
    }

    private companion object {
        val KEY_BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val KEY_RECURRING_REMINDERS_ENABLED = booleanPreferencesKey("recurring_reminders_enabled")
        val KEY_OFF_APP_RECURRING_REMINDERS = booleanPreferencesKey("off_app_recurring_reminders_enabled")
        val KEY_PARTNER_DEBT_ALERTS_ENABLED = booleanPreferencesKey("partner_debt_alerts_enabled")
        val KEY_BUDGET_WARN_THRESHOLD = intPreferencesKey("budget_warn_threshold_percent")
        val KEY_BUDGET_OVER_ENABLED = booleanPreferencesKey("budget_over_alerts_enabled")
        val KEY_BUDGET_OVER_THRESHOLD = intPreferencesKey("budget_over_threshold_percent")
        val KEY_MUTED_BUDGET_LINES = stringSetPreferencesKey("muted_budget_lines")
    }
}
