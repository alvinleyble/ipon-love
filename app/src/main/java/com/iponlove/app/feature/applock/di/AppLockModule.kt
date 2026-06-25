package com.iponlove.app.feature.applock.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.applock.data.AppLockRepositoryImpl
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
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
annotation class AppLockDataStore

private val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppLockDataStoreModule {
    @Provides
    @Singleton
    @AppLockDataStore
    fun appLockDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appLockDataStore
}

@Module
@InstallIn(SingletonComponent::class)
interface AppLockModule {
    @Binds
    @Singleton
    fun bindAppLockRepository(impl: AppLockRepositoryImpl): AppLockRepository
}
