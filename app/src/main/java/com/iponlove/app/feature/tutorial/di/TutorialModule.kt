package com.iponlove.app.feature.tutorial.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.tutorial.data.TutorialRepositoryImpl
import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
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
annotation class TutorialDataStore

// Its own DataStore file, intentionally separate from `onboarding_prefs`: the tutorial flag is a
// once-per-install marker (ADR-0034) and is NOT reset by LocalDataWiper on sign-out/account-switch,
// unlike the onboarding flags. It resets only when local storage itself is cleared.
private val Context.tutorialDataStore: DataStore<Preferences> by preferencesDataStore(name = "tutorial_prefs")

@Module
@InstallIn(SingletonComponent::class)
object TutorialDataStoreModule {
    @Provides
    @Singleton
    @TutorialDataStore
    fun tutorialDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.tutorialDataStore
}

@Module
@InstallIn(SingletonComponent::class)
interface TutorialModule {
    @Binds
    @Singleton
    fun bindTutorialRepository(impl: TutorialRepositoryImpl): TutorialRepository
}
