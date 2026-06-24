package com.iponlove.app.feature.budgets.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.budgets.data.BudgetRepositoryImpl
import com.iponlove.app.feature.budgets.data.remote.BudgetRemoteSource
import com.iponlove.app.feature.budgets.data.remote.StubBudgetRemoteSource
import com.iponlove.app.feature.budgets.data.sync.BudgetTableSyncer
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BudgetModule {

    @Binds
    fun budgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun budgetRemoteSource(impl: StubBudgetRemoteSource): BudgetRemoteSource

    /** Contributes budgets to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun budgetTableSyncer(impl: BudgetTableSyncer): TableSyncer
}
