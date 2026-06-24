package com.iponlove.app.feature.recurring.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.recurring.data.RecurringRuleRepositoryImpl
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleRemoteSource
import com.iponlove.app.feature.recurring.data.remote.SupabaseRecurringRuleRemoteSource
import com.iponlove.app.feature.recurring.data.sync.RecurringRuleTableSyncer
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RecurringModule {

    @Binds
    fun recurringRuleRepository(impl: RecurringRuleRepositoryImpl): RecurringRuleRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun recurringRuleRemoteSource(impl: SupabaseRecurringRuleRemoteSource): RecurringRuleRemoteSource

    /** Contributes recurring_rules to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun recurringRuleTableSyncer(impl: RecurringRuleTableSyncer): TableSyncer
}
