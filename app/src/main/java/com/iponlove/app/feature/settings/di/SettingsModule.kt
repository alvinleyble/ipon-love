package com.iponlove.app.feature.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.settings.data.PrivacyModeRepositoryImpl
import com.iponlove.app.feature.settings.data.ThemeRepositoryImpl
import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import com.iponlove.app.feature.settings.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ThemeDataStore

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class PrivacyDataStore

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")
private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_prefs")

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {
    @Provides
    @Singleton
    @ThemeDataStore
    fun themeDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.themeDataStore

    @Provides
    @Singleton
    @PrivacyDataStore
    fun privacyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.privacyDataStore
}

@Module
@InstallIn(SingletonComponent::class)
interface SettingsModule {
    @Binds
    @Singleton
    fun bindThemeRepository(impl: ThemeRepositoryImpl): ThemeRepository

    @Binds
    @Singleton
    fun bindPrivacyModeRepository(impl: PrivacyModeRepositoryImpl): PrivacyModeRepository
}
