package com.iponlove.app.core.sync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Persists the instant of the last successful full sync (v1.6.5 Item 9). The engine's
 * in-memory state flow boots `Idle` on every launch, so without this the Settings sync
 * card would read "—" after each cold open. Written by `SyncEngine` at the exact point
 * its state flips to `Success`; the silent micro-paths (`pushOnly`/`pullOnly`) never
 * write it, by design — "last synced" means a full push+pull round trip.
 *
 * Open so tests can count writes (the coalescing single-write guarantee).
 */
open class SyncStatusStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Emits the last successful full-sync instant, or null before the first ever sync. */
    open fun observe(): Flow<Instant?> =
        dataStore.data.map { prefs -> prefs[KEY]?.let(Instant::ofEpochMilli) }

    open suspend fun save(at: Instant) {
        dataStore.edit { it[KEY] = at.toEpochMilli() }
    }

    /** Account-scoped: cleared on sign-out (ADR-0021) so the next account never reads the
     *  previous one's timestamp. */
    open suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = longPreferencesKey("last_synced_at_millis")
    }
}
