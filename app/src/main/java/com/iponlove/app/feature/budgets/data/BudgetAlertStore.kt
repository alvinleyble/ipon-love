package com.iponlove.app.feature.budgets.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * DataStore-backed deduplication store for budget alert notifications.
 *
 * Keys are `"budgetId:month:threshold"` (e.g. `"abc123:2026-06:80"`). At month rollover
 * the entire set is cleared so alerts re-arm for the new month automatically — no cron job
 * needed. A `DataStore<Preferences>` scoped specifically to budget alerts is injected via
 * [com.iponlove.app.feature.budgets.di.BudgetAlertDataStore] qualifier.
 */
class BudgetAlertStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val firedKey = stringSetPreferencesKey("fired_alert_keys")
    private val monthKey = stringPreferencesKey("alert_month")

    /** Returns the set of already-fired keys for [currentMonth], clearing on rollover. */
    suspend fun loadFired(currentMonth: String): Set<String> {
        val prefs = dataStore.data.first()
        val storedMonth = prefs[monthKey]
        if (storedMonth != currentMonth) {
            dataStore.edit { it.clear() }
            return emptySet()
        }
        return prefs[firedKey] ?: emptySet()
    }

    /** Records [key] as fired for [currentMonth]. */
    suspend fun markFired(key: String, currentMonth: String) {
        dataStore.edit { prefs ->
            val current = prefs[firedKey] ?: emptySet()
            prefs[firedKey] = current + key
            prefs[monthKey] = currentMonth
        }
    }
}
