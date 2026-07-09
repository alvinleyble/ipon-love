package com.iponlove.app.core.config.di

import com.iponlove.app.core.config.AppConfigRepository
import com.iponlove.app.core.config.AppConfigRepositoryImpl
import com.iponlove.app.core.config.remote.AppConfigRemoteSource
import com.iponlove.app.core.config.remote.SupabaseAppConfigRemoteSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppConfigModule {

    @Binds
    @Singleton
    fun bindAppConfigRepository(impl: AppConfigRepositoryImpl): AppConfigRepository

    @Binds
    @Singleton
    fun bindAppConfigRemoteSource(impl: SupabaseAppConfigRemoteSource): AppConfigRemoteSource
}
