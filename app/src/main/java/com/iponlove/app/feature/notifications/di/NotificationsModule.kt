package com.iponlove.app.feature.notifications.di

import com.iponlove.app.core.sync.TableSyncer
import com.iponlove.app.feature.notifications.data.NotificationRepositoryImpl
import com.iponlove.app.feature.notifications.data.remote.NotificationRemoteSource
import com.iponlove.app.feature.notifications.data.remote.SupabaseNotificationRemoteSource
import com.iponlove.app.feature.notifications.data.sync.NotificationTableSyncer
import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NotificationsModule {

    @Binds
    fun notificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    fun notificationRemoteSource(impl: SupabaseNotificationRemoteSource): NotificationRemoteSource

    /** Contributes notifications to the sync engine's table set; engine sorts into FK order. */
    @Binds
    @IntoSet
    fun notificationTableSyncer(impl: NotificationTableSyncer): TableSyncer
}
