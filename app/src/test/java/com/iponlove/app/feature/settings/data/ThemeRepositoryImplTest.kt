package com.iponlove.app.feature.settings.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.settings.domain.model.ThemeMode
import com.iponlove.app.feature.settings.domain.model.ThemePalette
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeRepositoryImplTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun repo(name: String = "theme.preferences_pb"): ThemeRepositoryImpl {
        val ds = PreferenceDataStoreFactory.create { tmpFolder.newFile(name) }
        return ThemeRepositoryImpl(ds)
    }

    @Test
    fun observe_noSavedValue_defaultsToSystemModeAndRosePalette() = runTest {
        repo().observe().test {
            val prefs = awaitItem()
            assertThat(prefs.mode).isEqualTo(ThemeMode.SYSTEM)
            assertThat(prefs.palette).isEqualTo(ThemePalette.ROSE)
            cancel()
        }
    }

    @Test
    fun save_light_roundTrips() = runTest {
        val repo = repo()
        repo.save(ThemePreferences(palette = ThemePalette.ROSE, mode = ThemeMode.LIGHT))
        repo.observe().test {
            assertThat(awaitItem().mode).isEqualTo(ThemeMode.LIGHT)
            cancel()
        }
    }

    @Test
    fun save_dark_roundTrips() = runTest {
        val repo = repo()
        repo.save(ThemePreferences(palette = ThemePalette.ROSE, mode = ThemeMode.DARK))
        repo.observe().test {
            assertThat(awaitItem().mode).isEqualTo(ThemeMode.DARK)
            cancel()
        }
    }

    @Test
    fun save_system_roundTrips() = runTest {
        val repo = repo()
        repo.save(ThemePreferences(palette = ThemePalette.ROSE, mode = ThemeMode.SYSTEM))
        repo.observe().test {
            assertThat(awaitItem().mode).isEqualTo(ThemeMode.SYSTEM)
            cancel()
        }
    }

    @Test
    fun save_thenSaveAgain_replacesPreviousMode() = runTest {
        val repo = repo()
        repo.save(ThemePreferences(palette = ThemePalette.ROSE, mode = ThemeMode.DARK))
        repo.save(ThemePreferences(palette = ThemePalette.ROSE, mode = ThemeMode.LIGHT))
        repo.observe().test {
            assertThat(awaitItem().mode).isEqualTo(ThemeMode.LIGHT)
            cancel()
        }
    }
}
