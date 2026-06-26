package com.iponlove.app.navigation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
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
annotation class NavConfigDataStore

private val Context.navConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "nav_config_prefs")

@Module
@InstallIn(SingletonComponent::class)
object NavConfigDataStoreModule {
    @Provides
    @Singleton
    @NavConfigDataStore
    fun navConfigDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.navConfigDataStore
}

@Module
@InstallIn(SingletonComponent::class)
interface NavConfigModule {
    @Binds
    @Singleton
    fun bindNavConfigRepository(impl: NavConfigRepositoryImpl): NavConfigRepository
}
