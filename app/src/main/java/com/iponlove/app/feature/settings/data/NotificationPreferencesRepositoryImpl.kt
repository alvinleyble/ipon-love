package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    private companion object {
        val KEY_BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val KEY_RECURRING_REMINDERS_ENABLED = booleanPreferencesKey("recurring_reminders_enabled")
    }
}
