package com.iponlove.app.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iponlove.app.feature.settings.di.ThemeDataStore
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
            val isDark = prefs[KEY_DARK] ?: false
            ThemePreferences(palette = palette, isDark = isDark)
        }

    override suspend fun save(prefs: ThemePreferences) {
        dataStore.edit { p ->
            p[KEY_PALETTE] = prefs.palette.name
            p[KEY_DARK] = prefs.isDark
        }
    }

    private companion object {
        val KEY_PALETTE = stringPreferencesKey("theme_palette")
        val KEY_DARK = booleanPreferencesKey("theme_is_dark")
    }
}
