package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * The write half of the inbox — the entry point every notification producer calls (budget
 * alerts today; recurring reminders and partner-debt alerts next). Create-if-absent: returns
 * true only when a new row was created, which is the producer's signal to also raise the
 * best-effort OS push (ADR-0053, Way A).
 */
class RecordNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(
        id: String,
        category: NotificationCategory,
        title: String,
        body: String,
        deepLink: String? = null,
    ): Boolean = repository.record(id, category, title, body, deepLink)
}
