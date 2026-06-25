package com.iponlove.app.feature.partnerdebt.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentRemoteSource
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtRemoteSource
import com.iponlove.app.feature.partnerdebt.data.remote.SupabaseDebtPaymentRemoteSource
import com.iponlove.app.feature.partnerdebt.data.remote.SupabasePartnerDebtRemoteSource
import com.iponlove.app.feature.partnerdebt.data.sync.DebtPaymentTableSyncer
import com.iponlove.app.feature.partnerdebt.data.sync.PartnerDebtTableSyncer
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PartnerDebtModule {

    @Binds
    fun partnerDebtRepository(impl: PartnerDebtRepositoryImpl): PartnerDebtRepository

    @Binds
    @Singleton
    fun partnerDebtRemoteSource(impl: SupabasePartnerDebtRemoteSource): PartnerDebtRemoteSource

    @Binds
    @Singleton
    fun debtPaymentRemoteSource(impl: SupabaseDebtPaymentRemoteSource): DebtPaymentRemoteSource

    /** Contributes both partner-debt tables to the sync engine; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun partnerDebtTableSyncer(impl: PartnerDebtTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun debtPaymentTableSyncer(impl: DebtPaymentTableSyncer): TableSyncer
}
