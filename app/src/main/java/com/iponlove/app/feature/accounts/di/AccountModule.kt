package com.iponlove.app.feature.accounts.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.accounts.data.AccountRepositoryImpl
import com.iponlove.app.feature.accounts.data.remote.AccountRemoteSource
import com.iponlove.app.feature.accounts.data.remote.StubAccountRemoteSource
import com.iponlove.app.feature.accounts.data.sync.AccountTableSyncer
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AccountModule {

    @Binds
    fun accountRepository(impl: AccountRepositoryImpl): AccountRepository

    // No-op stub until the Supabase backend slice provides the real remote source.
    @Binds
    @Singleton
    fun accountRemoteSource(impl: StubAccountRemoteSource): AccountRemoteSource

    /** Contributes accounts to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun accountTableSyncer(impl: AccountTableSyncer): TableSyncer
}
