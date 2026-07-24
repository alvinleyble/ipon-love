package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iponlove.app.feature.settings.di.ThemeDataStore
import com.iponlove.app.feature.settings.domain.model.ThemeMode
import com.iponlove.app.feature.settings.domain.model.ThemePalette
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    @ThemeDataStore private val dataStore: DataStore<Preferences>,
) : ThemeRepository {

    override fun observe(): Flow<ThemePreferences> =
        dataStore.data.map { prefs ->
            val paletteName = prefs[KEY_PALETTE] ?: ThemePalette.ROSE.name
            val palette = runCatching { ThemePalette.valueOf(paletteName) }.getOrDefault(ThemePalette.ROSE)
            val modeName = prefs[KEY_MODE] ?: ThemeMode.SYSTEM.name
            val mode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.SYSTEM)
            ThemePreferences(palette = palette, mode = mode)
        }

    override suspend fun save(prefs: ThemePreferences) {
        dataStore.edit { p ->
            p[KEY_PALETTE] = prefs.palette.name
            p[KEY_MODE] = prefs.mode.name
        }
    }

    private companion object {
        val KEY_PALETTE = stringPreferencesKey("theme_palette")
        val KEY_MODE = stringPreferencesKey("theme_mode")
    }
}
