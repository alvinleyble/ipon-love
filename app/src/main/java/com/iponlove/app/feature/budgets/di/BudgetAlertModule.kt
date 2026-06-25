package com.iponlove.app.feature.budgets.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.iponlove.app.feature.budgets.data.BudgetAlertStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class BudgetAlertDataStore

private val Context.budgetAlertDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "budget_alert_prefs")

@Module
@InstallIn(SingletonComponent::class)
object BudgetAlertModule {

    @Provides
    @Singleton
    @BudgetAlertDataStore
    fun budgetAlertDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.budgetAlertDataStore

    @Provides
    @Singleton
    fun budgetAlertStore(@BudgetAlertDataStore dataStore: DataStore<Preferences>): BudgetAlertStore =
        BudgetAlertStore(dataStore)
}
