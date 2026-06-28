package com.iponlove.app.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NavConfigRepositoryImpl @Inject constructor(
    @NavConfigDataStore private val dataStore: DataStore<Preferences>,
) : NavConfigRepository {

    override fun observe(): Flow<NavConfig> =
        dataStore.data.map { prefs -> NavConfig.deserialize(prefs[KEY_PINS]) }

    override suspend fun save(config: NavConfig) {
        dataStore.edit { it[KEY_PINS] = NavConfig.serialize(config) }
    }

    override suspend fun reset() {
        dataStore.edit { it.remove(KEY_PINS) }
    }

    private companion object {
        val KEY_PINS = stringPreferencesKey("nav_pinned_ids")
    }
}
