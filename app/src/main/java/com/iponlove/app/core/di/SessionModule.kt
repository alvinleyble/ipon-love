package com.iponlove.app.core.di

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.SupabaseCurrentUserProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface SessionModule {

    @Binds
    @Singleton
    fun currentUserProvider(impl: SupabaseCurrentUserProvider): CurrentUserProvider
}
