package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The inbox list: newest-first, dismissed rows excluded. */
class ObserveNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    operator fun invoke(): Flow<List<AppNotification>> = repository.observeInbox()
}
