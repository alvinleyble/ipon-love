package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import javax.inject.Inject

/** Swipe-to-dismiss one row — an ordinary soft-delete that syncs (ADR-0053). */
class DismissNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(id: String) = repository.dismiss(id)
}
