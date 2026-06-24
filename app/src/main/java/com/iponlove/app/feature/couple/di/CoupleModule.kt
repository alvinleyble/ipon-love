package com.iponlove.app.feature.couple.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.couple.data.CoupleRepositoryImpl
import com.iponlove.app.feature.couple.data.remote.CoupleRemoteSource
import com.iponlove.app.feature.couple.data.remote.SupabaseCoupleRemoteSource
import com.iponlove.app.feature.couple.data.sync.CoupleTableSyncer
import com.iponlove.app.feature.couple.domain.repository.CoupleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CoupleModule {

    @Binds
    @Singleton
    fun coupleRepository(impl: CoupleRepositoryImpl): CoupleRepository

    @Binds
    @Singleton
    fun coupleRemoteSource(impl: SupabaseCoupleRemoteSource): CoupleRemoteSource

    @Binds
    @IntoSet
    fun coupleTableSyncer(impl: CoupleTableSyncer): TableSyncer
}
