package com.iponlove.app.core.billing.di

import com.iponlove.app.core.billing.BillingGateway
import com.iponlove.app.core.billing.PlayBillingGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BillingModule {

    @Binds
    @Singleton
    fun bindBillingGateway(impl: PlayBillingGateway): BillingGateway
}
