package com.iponlove.app.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * The last top-level nav module the user was in, plus when they left it (a monotonic
 * [android.os.SystemClock.elapsedRealtime] stamp used only for the recency gate).
 *
 * [moduleId] is a [NavRegistry] module id — the stable persisted key, never the display route.
 */
data class SavedNavLocation(val moduleId: String, val backgroundedAt: Long)

/**
 * Persists the user's current nav module to disk so the shell can restore their place after the
 * OEM task-killer force-stops the process (v1.6.6 Item 39). Force-stop skips `onSaveInstanceState`,
 * so `rememberSaveable` (which the NavHost start destination relies on) can't carry it across the
 * kill — only disk survives.
 *
 * Per-device UI state, cleared on the account-switch wipe
 * ([com.iponlove.app.core.session.LocalDataWiper]) so one account never restores into another's
 * last tab.
 */
interface NavStateStore {
    /** The last saved location, or null on a fresh install / after [clear]. */
    suspend fun read(): SavedNavLocation?
    suspend fun save(moduleId: String, backgroundedAt: Long)
    suspend fun clear()
}

class DataStoreNavStateStore(
    private val dataStore: DataStore<Preferences>,
) : NavStateStore {

    override suspend fun read(): SavedNavLocation? {
        val prefs = dataStore.data.first()
        val moduleId = prefs[KEY_MODULE] ?: return null
        val backgroundedAt = prefs[KEY_AT] ?: return null
        return SavedNavLocation(moduleId, backgroundedAt)
    }

    override suspend fun save(moduleId: String, backgroundedAt: Long) {
        dataStore.edit {
            it[KEY_MODULE] = moduleId
            it[KEY_AT] = backgroundedAt
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(KEY_MODULE)
            it.remove(KEY_AT)
        }
    }

    private companion object {
        val KEY_MODULE = stringPreferencesKey("last_module_id")
        val KEY_AT = longPreferencesKey("backgrounded_at")
    }
}
