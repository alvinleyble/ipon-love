package com.iponlove.app.feature.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.settings.data.AccountDeletionRepositoryImpl
import com.iponlove.app.feature.settings.data.CurrencySymbolRepositoryImpl
import com.iponlove.app.feature.settings.data.NotificationPreferencesRepositoryImpl
import com.iponlove.app.feature.settings.data.PrivacyModeRepositoryImpl
import com.iponlove.app.feature.settings.data.ReceiptPreferencesRepositoryImpl
import com.iponlove.app.feature.settings.data.ResetFinancesRepositoryImpl
import com.iponlove.app.feature.settings.data.ThemeRepositoryImpl
import com.iponlove.app.feature.settings.data.remote.AccountDeletionRemoteSource
import com.iponlove.app.feature.settings.data.remote.SupabaseAccountDeletionRemoteSource
import com.iponlove.app.feature.settings.domain.repository.AccountDeletionRepository
import com.iponlove.app.feature.settings.domain.repository.CurrencySymbolRepository
import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import com.iponlove.app.feature.settings.domain.repository.ReceiptPreferencesRepository
import com.iponlove.app.feature.settings.domain.repository.ResetFinancesRepository
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

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class CurrencyDataStore

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class FinanceDataStore

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")
private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "privacy_prefs")
private val Context.currencyDataStore: DataStore<Preferences> by preferencesDataStore(name = "currency_prefs")
private val Context.financeDataStore: DataStore<Preferences> by preferencesDataStore(name = "finance_prefs")

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

    @Provides
    @Singleton
    @CurrencyDataStore
    fun currencyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.currencyDataStore

    @Provides
    @Singleton
    @FinanceDataStore
    fun financeDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.financeDataStore
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

    @Binds
    @Singleton
    fun bindCurrencySymbolRepository(impl: CurrencySymbolRepositoryImpl): CurrencySymbolRepository

    @Binds
    @Singleton
    fun bindNotificationPreferencesRepository(
        impl: NotificationPreferencesRepositoryImpl,
    ): NotificationPreferencesRepository

    @Binds
    @Singleton
    fun bindReceiptPreferencesRepository(
        impl: ReceiptPreferencesRepositoryImpl,
    ): ReceiptPreferencesRepository

    @Binds
    @Singleton
    fun bindResetFinancesRepository(impl: ResetFinancesRepositoryImpl): ResetFinancesRepository

    @Binds
    @Singleton
    fun bindAccountDeletionRepository(impl: AccountDeletionRepositoryImpl): AccountDeletionRepository

    @Binds
    fun bindAccountDeletionRemoteSource(impl: SupabaseAccountDeletionRemoteSource): AccountDeletionRemoteSource
}
