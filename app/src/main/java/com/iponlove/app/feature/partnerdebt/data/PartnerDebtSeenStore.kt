package com.iponlove.app.feature.partnerdebt.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.iponlove.app.feature.settings.di.FinanceDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Local "authored/seen" store for the partner-debt notification (Item 9 grill) — the ids a debt
 * must never fire a notification for. Two sources feed it:
 *
 * 1. **Locally authored** — [markAuthored] is called from [com.iponlove.app.feature.partnerdebt.domain.usecase.UpsertPartnerDebtUseCase]
 *    at create time, so a device's own debts (either direction) are pre-seeded and can never
 *    notify their own author.
 * 2. **One-time backlog freeze** — mirrors [com.iponlove.app.feature.recurring.data.RecurringReminderBacklogStore]:
 *    the first time [snapshot] ever runs on a device, it freezes whichever debt ids already
 *    existed, so shipping the feature (or a fresh pairing) doesn't fire for every pre-existing
 *    partner debt.
 *
 * Both live in one store (rather than split like Item 1's) because they feed the exact same
 * "must never fire" set with no other consumer of either half alone.
 */
class PartnerDebtSeenStore @Inject constructor(
    @FinanceDataStore private val dataStore: DataStore<Preferences>,
) {
    suspend fun markAuthored(debtId: String) {
        dataStore.edit { p -> p[AUTHORED_KEY] = (p[AUTHORED_KEY] ?: emptySet()) + debtId }
    }

    /** Returns the union of the authored set and the frozen backlog, seeding the backlog from
     *  [currentlyExisting] on the very first call. */
    suspend fun snapshot(currentlyExisting: Set<String>): Set<String> {
        val prefs = dataStore.data.first()
        val authored = prefs[AUTHORED_KEY] ?: emptySet()
        if (prefs[BACKLOG_SEEDED_KEY] == true) {
            return authored + (prefs[BACKLOG_KEY] ?: emptySet())
        }
        dataStore.edit { p ->
            p[BACKLOG_SEEDED_KEY] = true
            p[BACKLOG_KEY] = currentlyExisting
        }
        return authored + currentlyExisting
    }

    private companion object {
        val AUTHORED_KEY = stringSetPreferencesKey("partner_debt_authored_ids")
        val BACKLOG_SEEDED_KEY = booleanPreferencesKey("partner_debt_backlog_seeded")
        val BACKLOG_KEY = stringSetPreferencesKey("partner_debt_backlog_ids")
    }
}
