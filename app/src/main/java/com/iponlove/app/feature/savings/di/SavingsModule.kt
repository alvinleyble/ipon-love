package com.iponlove.app.feature.savings.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.savings.data.GoalContributionRepositoryImpl
import com.iponlove.app.feature.savings.data.SavingsGoalRepositoryImpl
import com.iponlove.app.feature.savings.data.remote.GoalContributionRemoteSource
import com.iponlove.app.feature.savings.data.remote.SavingsGoalRemoteSource
import com.iponlove.app.feature.savings.data.remote.SupabaseGoalContributionRemoteSource
import com.iponlove.app.feature.savings.data.remote.SupabaseSavingsGoalRemoteSource
import com.iponlove.app.feature.savings.data.sync.GoalContributionTableSyncer
import com.iponlove.app.feature.savings.data.sync.PartnerGoalContributionTableSyncer
import com.iponlove.app.feature.savings.data.sync.PartnerSavingsGoalTableSyncer
import com.iponlove.app.feature.savings.data.sync.SavingsGoalTableSyncer
import com.iponlove.app.feature.savings.domain.repository.GoalContributionRepository
import com.iponlove.app.feature.savings.domain.repository.SavingsGoalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface SavingsModule {

    @Binds
    fun savingsGoalRepository(impl: SavingsGoalRepositoryImpl): SavingsGoalRepository

    @Binds
    fun goalContributionRepository(impl: GoalContributionRepositoryImpl): GoalContributionRepository

    @Binds
    @Singleton
    fun savingsGoalRemoteSource(impl: SupabaseSavingsGoalRemoteSource): SavingsGoalRemoteSource

    @Binds
    @Singleton
    fun goalContributionRemoteSource(
        impl: SupabaseGoalContributionRemoteSource,
    ): GoalContributionRemoteSource

    @Binds
    @IntoSet
    fun savingsGoalTableSyncer(impl: SavingsGoalTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun goalContributionTableSyncer(impl: GoalContributionTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun partnerSavingsGoalTableSyncer(impl: PartnerSavingsGoalTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun partnerGoalContributionTableSyncer(
        impl: PartnerGoalContributionTableSyncer,
    ): TableSyncer
}
