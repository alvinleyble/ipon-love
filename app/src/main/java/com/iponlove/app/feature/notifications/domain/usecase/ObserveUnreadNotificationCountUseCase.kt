package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Drives the bell's unread badge on every top-level screen. */
class ObserveUnreadNotificationCountUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeUnreadCount()
}
