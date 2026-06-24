package com.iponlove.app.feature.auth.di

import com.iponlove.app.feature.auth.data.AuthRepositoryImpl
import com.iponlove.app.feature.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AuthModule {

    @Binds
    @Singleton
    fun authRepository(impl: AuthRepositoryImpl): AuthRepository
}
