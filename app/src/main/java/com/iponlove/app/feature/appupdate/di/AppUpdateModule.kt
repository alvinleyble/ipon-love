package com.iponlove.app.feature.appupdate.di

import com.iponlove.app.feature.appupdate.data.AppReleaseInfoRepositoryImpl
import com.iponlove.app.feature.appupdate.domain.repository.AppReleaseInfoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppUpdateModule {
    @Binds
    @Singleton
    fun bindAppReleaseInfoRepository(impl: AppReleaseInfoRepositoryImpl): AppReleaseInfoRepository
}
