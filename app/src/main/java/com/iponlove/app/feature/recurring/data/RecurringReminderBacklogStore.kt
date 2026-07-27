package com.iponlove.app.feature.recurring.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.iponlove.app.feature.settings.di.FinanceDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One-time backlog guard for recurring due-date reminders (ADR-0052 decision 3). The first
 * time this ever runs on a device, it freezes whichever occurrence ids were *already* pending
 * — so shipping the feature (default ON), or a user's first sync after updating, doesn't dump
 * a whole `PENDING_WINDOW_MONTHS` backlog of reminders at once. Occurrences that become pending
 * afterward are not in the frozen set and fire normally.
 *
 * Distinct from the inbox's own dedup (ADR-0053), which tracks "already notified" going
 * forward — this only answers "existed before reminders could ever have fired for it", once,
 * per device (no couples/schema question, ADR-0052 decision 7).
 */
class RecurringReminderBacklogStore @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) {
    /** Returns the frozen backlog set, seeding it from [currentlyPending] on the first call. */
    suspend fun freeze(currentlyPending: Set<String>): Set<String> {
        val prefs = dataStore.data.first()
        if (prefs[SEEDED_KEY] == true) return prefs[BACKLOG_KEY] ?: emptySet()
        dataStore.edit { p ->
            p[SEEDED_KEY] = true
            p[BACKLOG_KEY] = currentlyPending
        }
        return currentlyPending
    }

    private companion object {
        val SEEDED_KEY = booleanPreferencesKey("recurring_reminder_backlog_seeded")
        val BACKLOG_KEY = stringSetPreferencesKey("recurring_reminder_backlog_ids")
    }
}
