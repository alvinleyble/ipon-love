package com.iponlove.app.feature.drafts.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.drafts.data.TransactionDraftRepositoryImpl
import com.iponlove.app.feature.drafts.data.remote.SupabaseTransactionDraftRemoteSource
import com.iponlove.app.feature.drafts.data.remote.TransactionDraftRemoteSource
import com.iponlove.app.feature.drafts.data.sync.TransactionDraftTableSyncer
import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DraftsModule {

    @Binds
    fun transactionDraftRepository(impl: TransactionDraftRepositoryImpl): TransactionDraftRepository

    @Binds
    @Singleton
    fun transactionDraftRemoteSource(
        impl: SupabaseTransactionDraftRemoteSource,
    ): TransactionDraftRemoteSource

    /** Contributes drafts to the sync engine's table set; the engine sorts into FK order. */
    @Binds
    @IntoSet
    fun transactionDraftTableSyncer(impl: TransactionDraftTableSyncer): TableSyncer
}
