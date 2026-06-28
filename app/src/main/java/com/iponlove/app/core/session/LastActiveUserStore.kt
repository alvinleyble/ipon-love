package com.iponlove.app.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Remembers which account last occupied local storage on this device (ADR-0021). The login
 * guard compares the freshly authenticated id against this to decide whether the device is
 * holding a *different* user's offline-first data and must be wiped first.
 *
 * Lives in its own DataStore — it is session bookkeeping, not per-account state, so it must
 * survive the [LocalDataWiper] wipe (which clears Room, sync cursors, and nav config).
 */
interface LastActiveUserStore {
    /** The id recorded by the last [setLastUserId], or null on a fresh install. */
    suspend fun lastUserId(): String?
    suspend fun setLastUserId(id: String)
}

class DataStoreLastActiveUserStore(
    private val dataStore: DataStore<Preferences>,
) : LastActiveUserStore {

    override suspend fun lastUserId(): String? = dataStore.data.first()[KEY]

    override suspend fun setLastUserId(id: String) {
        dataStore.edit { it[KEY] = id }
    }

    private companion object {
        val KEY = stringPreferencesKey("last_user_id")
    }
}
