package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * Every inbox id a producer has already raised under [prefix] — read, unread, and dismissed
 * alike. Lets a producer dedupe a whole batch of candidate events with one query before it
 * starts writing (ADR-0053 decision 3); [RecordNotificationUseCase]'s create-if-absent is
 * still the atomic backstop.
 */
class GetRaisedNotificationIdsUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(prefix: String): Set<String> = repository.raisedIds(prefix)
}
