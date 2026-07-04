package com.iponlove.app.feature.tutorial.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.iponlove.app.feature.tutorial.di.TutorialDataStore
import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class TutorialRepositoryImpl @Inject constructor(
    @TutorialDataStore private val dataStore: DataStore<Preferences>,
) : TutorialRepository {

    override suspend fun isTutorialSeen(): Boolean =
        dataStore.data.first()[KEY_TUTORIAL_SEEN] ?: false

    override suspend fun setTutorialSeen() {
        dataStore.edit { it[KEY_TUTORIAL_SEEN] = true }
    }

    private companion object {
        val KEY_TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }
}
