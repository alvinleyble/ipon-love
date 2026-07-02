package com.iponlove.app.feature.onboarding.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.onboarding.data.OnboardingRepositoryImpl
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
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
annotation class OnboardingDataStore

// Deliberately not touched by LocalDataWiper (ADR-0021) — like theme/app-lock prefs, these
// flags are per-device, not per-account (ADR-0024).
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_prefs")

@Module
@InstallIn(SingletonComponent::class)
object OnboardingDataStoreModule {
    @Provides
    @Singleton
    @OnboardingDataStore
    fun onboardingDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.onboardingDataStore
}

@Module
@InstallIn(SingletonComponent::class)
interface OnboardingModule {
    @Binds
    @Singleton
    fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository
}
