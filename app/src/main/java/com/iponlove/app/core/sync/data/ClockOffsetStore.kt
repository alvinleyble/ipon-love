package com.iponlove.app.core.sync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.iponlove.app.core.sync.SyncClock
import kotlinx.coroutines.flow.first

/**
 * Persists [SyncClock]'s clock offset (ADR-0001) across launches. The clock keeps the
 * live offset in memory for a synchronous write hot path; this store only restores it on
 * startup and saves it after a sync captures fresh server time.
 */
class ClockOffsetStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Load the persisted offset into [clock]. Call once on startup before the first write. */
    suspend fun restoreInto(clock: SyncClock) {
        dataStore.data.first()[KEY]?.let { clock.offsetMillis = it }
    }

    /** Persist [clock]'s current offset. Call after a sync updates it from server time. */
    suspend fun save(clock: SyncClock) {
        dataStore.edit { it[KEY] = clock.offsetMillis }
    }

    private companion object {
        val KEY = longPreferencesKey("clock_offset_millis")
    }
}
