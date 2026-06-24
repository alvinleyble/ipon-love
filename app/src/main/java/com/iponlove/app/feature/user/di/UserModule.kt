package com.iponlove.app.feature.user.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.user.data.UserRepositoryImpl
import com.iponlove.app.feature.user.data.remote.SupabaseUserRemoteSource
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.data.sync.UserTableSyncer
import com.iponlove.app.feature.user.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface UserModule {

    @Binds
    @Singleton
    fun userRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    fun userRemoteSource(impl: SupabaseUserRemoteSource): UserRemoteSource

    @Binds
    @IntoSet
    fun userTableSyncer(impl: UserTableSyncer): TableSyncer
}
