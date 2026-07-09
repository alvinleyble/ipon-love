package com.iponlove.app.core.entitlement.di

import com.iponlove.app.core.entitlement.EntitlementReconcileStep
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.entitlement.EntitlementRepositoryImpl
import com.iponlove.app.core.sync.FullSyncStep
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface EntitlementModule {

    @Binds
    @Singleton
    fun bindEntitlementRepository(impl: EntitlementRepositoryImpl): EntitlementRepository

    /** Contributes the Play-reconcile pass to the full-sync trigger (S4). */
    @Binds
    @IntoSet
    fun bindEntitlementReconcileStep(step: EntitlementReconcileStep): FullSyncStep
}
