package com.iponlove.app.core.sync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed per-table pull cursor (ADR-0002). Keyed by [SyncTable.name] so the
 * key set survives reordering of the enum's ordinals.
 */
class DataStoreSyncCursorStore(
    private val dataStore: DataStore<Preferences>,
) : SyncCursorStore {

    override suspend fun cursor(table: SyncTable): Long =
        dataStore.data.first()[key(table)] ?: 0L

    override suspend fun setCursor(table: SyncTable, value: Long) {
        dataStore.edit { it[key(table)] = value }
    }

    override suspend fun reset() {
        // Remove only the cursor keys; the clock offset shares this DataStore and must survive
        // (it's a device-level calibration, not per-account state).
        dataStore.edit { prefs -> SyncTable.entries.forEach { prefs.remove(key(it)) } }
    }

    private fun key(table: SyncTable) = longPreferencesKey("cursor_${table.name}")
}
