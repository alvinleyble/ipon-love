package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import javax.inject.Inject

/** Bulk-clears the badge when the inbox is opened (ADR-0053 read model). */
class MarkAllNotificationsReadUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke() = repository.markAllRead()
}
