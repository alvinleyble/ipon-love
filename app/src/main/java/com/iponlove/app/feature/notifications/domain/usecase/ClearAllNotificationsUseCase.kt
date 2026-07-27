package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import javax.inject.Inject

/** "Clear all" — soft-deletes every visible row (ADR-0053). */
class ClearAllNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke() = repository.clearAll()
}
