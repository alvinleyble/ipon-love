package com.iponlove.app.feature.budgets.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.iponlove.app.feature.settings.di.FinanceDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Backlog guard for the opt-in `over` rung (ADR-0054 consequences — the "seed the over slot as
 * fired while off" build-time recommendation). While the over toggle is off, [sync] keeps this
 * store's ids matched to whichever budgets are *currently* crossing the over threshold; when the
 * toggle is switched on, the worker folds [current] into its already-raised set so those
 * pre-existing crossings don't blast the user the moment they opt in — only a genuinely new
 * crossing after enabling fires.
 *
 * Distinct from the inbox's own dedup (ADR-0053): this is a local-only, un-synced snapshot, not
 * a notification record — nothing here ever appears in the inbox or raises a push.
 */
class BudgetOverAlertBacklogStore @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) {
    suspend fun current(): Set<String> = dataStore.data.first()[BACKLOG_KEY] ?: emptySet()

    suspend fun sync(currentlyOverIds: Set<String>) {
        dataStore.edit { p -> p[BACKLOG_KEY] = currentlyOverIds }
    }

    private companion object {
        val BACKLOG_KEY = stringSetPreferencesKey("budget_over_alert_backlog_ids")
    }
}
