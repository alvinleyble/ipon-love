package com.iponlove.app.core.analytics.di

import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.analytics.AnalyticsFlusher
import com.iponlove.app.core.analytics.AnalyticsSyncStep
import com.iponlove.app.core.analytics.RoomAnalytics
import com.iponlove.app.core.analytics.remote.AnalyticsRemoteSource
import com.iponlove.app.core.analytics.remote.SupabaseAnalyticsRemoteSource
import com.iponlove.app.core.sync.FullSyncStep
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AnalyticsModule {

    /** Both seams resolve to the one [RoomAnalytics] singleton (log + flush share the buffer). */
    @Binds
    @Singleton
    fun bindAnalytics(impl: RoomAnalytics): Analytics

    @Binds
    @Singleton
    fun bindAnalyticsFlusher(impl: RoomAnalytics): AnalyticsFlusher

    @Binds
    @Singleton
    fun bindAnalyticsRemoteSource(impl: SupabaseAnalyticsRemoteSource): AnalyticsRemoteSource

    /** Contributes the buffer flush to the full-sync trigger. */
    @Binds
    @IntoSet
    fun bindAnalyticsSyncStep(step: AnalyticsSyncStep): FullSyncStep
}
