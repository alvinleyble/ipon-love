package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.iponlove.app.feature.settings.di.PrivacyDataStore
import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PrivacyModeRepositoryImpl @Inject constructor(
    @PrivacyDataStore private val dataStore: DataStore<Preferences>,
) : PrivacyModeRepository {

    override fun observe(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PRIVACY_MODE] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_PRIVACY_MODE] = enabled }
    }

    private companion object {
        val KEY_PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
    }
}
