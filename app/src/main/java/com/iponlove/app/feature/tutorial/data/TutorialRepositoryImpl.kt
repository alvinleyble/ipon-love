package com.iponlove.app.feature.tutorial.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.iponlove.app.feature.tutorial.di.TutorialDataStore
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TutorialRepositoryImpl @Inject constructor(
    @TutorialDataStore private val dataStore: DataStore<Preferences>,
) : TutorialRepository {

    override fun observeSeenTours(): Flow<Set<String>> =
        dataStore.data.map { it.effectiveSeenTours() }

    override suspend fun markTourSeen(tourId: String) {
        dataStore.edit { prefs ->
            // Read through the legacy seed so writing the explicit set doesn't drop the shell ID an
            // upgrading tester already earned via the old boolean.
            prefs[KEY_SEEN_TOURS] = prefs.effectiveSeenTours() + tourId
        }
    }

    override suspend fun clearAllTours() {
        dataStore.edit { prefs ->
            prefs[KEY_SEEN_TOURS] = emptySet()
            // Drop the legacy flag too, else effectiveSeenTours() would re-seed the shell ID and the
            // "Replay tutorial" reset wouldn't restart the shell tour.
            prefs.remove(KEY_TUTORIAL_SEEN)
        }
    }

    /**
     * The seen-set as it should be read: the explicit set if present, otherwise the legacy
     * single-boolean migrated to `{shell}` (ADR-0038 dec. 2). Once any tour is marked seen the
     * explicit set exists and supersedes the legacy flag.
     */
    private fun Preferences.effectiveSeenTours(): Set<String> =
        this[KEY_SEEN_TOURS]
            ?: if (this[KEY_TUTORIAL_SEEN] == true) setOf(TutorialTours.SHELL) else emptySet()

    private companion object {
        val KEY_SEEN_TOURS = stringSetPreferencesKey("seen_tours")

        /** v1.6.1 single-boolean gate (ADR-0034), read-only now — see [effectiveSeenTours]. */
        val KEY_TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
    }
}
