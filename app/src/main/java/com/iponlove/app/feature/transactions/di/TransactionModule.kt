package com.iponlove.app.feature.transactions.di

import com.iponlove.app.core.sync.PreSyncStep
import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.transactions.data.TransactionImageRepositoryImpl
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.data.remote.SupabaseTransactionImageRemoteSource
import com.iponlove.app.feature.transactions.data.remote.SupabaseTransactionRemoteSource
import com.iponlove.app.feature.transactions.data.remote.TransactionImageRemoteSource
import com.iponlove.app.feature.transactions.data.remote.TransactionRemoteSource
import com.iponlove.app.feature.transactions.data.sync.PartnerTransactionImageTableSyncer
import com.iponlove.app.feature.transactions.data.sync.PartnerTransactionTableSyncer
import com.iponlove.app.feature.transactions.data.sync.TransactionImageTableSyncer
import com.iponlove.app.feature.transactions.data.sync.TransactionTableSyncer
import com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
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

    @Binds
    fun transactionImageRepository(impl: TransactionImageRepositoryImpl): TransactionImageRepository

    @Binds
    @Singleton
    fun transactionRemoteSource(impl: SupabaseTransactionRemoteSource): TransactionRemoteSource

    @Binds
    @Singleton
    fun transactionImageRemoteSource(impl: SupabaseTransactionImageRemoteSource): TransactionImageRemoteSource

    /** Contributes transactions to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun transactionTableSyncer(impl: TransactionTableSyncer): TableSyncer

    /** Contributes the partner-transactions replica pull (ADR-0004/0005). */
    @Binds
    @IntoSet
    fun partnerTransactionTableSyncer(impl: PartnerTransactionTableSyncer): TableSyncer

    /** Receipt images: owned table + partner redacting-view replica. */
    @Binds
    @IntoSet
    fun transactionImageTableSyncer(impl: TransactionImageTableSyncer): TableSyncer

    @Binds
    @IntoSet
    fun partnerTransactionImageTableSyncer(impl: PartnerTransactionImageTableSyncer): TableSyncer

    /** Uploads pending receipt images to Storage before the push loop. */
    @Binds
    @IntoSet
    fun transactionImageUploader(impl: TransactionImageUploader): PreSyncStep
}
