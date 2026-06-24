package com.iponlove.app.feature.transactions.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.data.remote.StubTransactionRemoteSource
import com.iponlove.app.feature.transactions.data.remote.TransactionRemoteSource
import com.iponlove.app.feature.transactions.data.sync.TransactionTableSyncer
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TransactionModule {

    @Binds
    fun transactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun transactionRemoteSource(impl: StubTransactionRemoteSource): TransactionRemoteSource

    /** Contributes transactions to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun transactionTableSyncer(impl: TransactionTableSyncer): TableSyncer
}
