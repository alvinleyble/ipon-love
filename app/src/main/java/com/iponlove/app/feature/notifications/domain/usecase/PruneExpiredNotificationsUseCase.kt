package com.iponlove.app.feature.notifications.domain.usecase

import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import java.time.Duration
import javax.inject.Inject

/**
 * The 60-day retention sweep (ADR-0053 decision 4). Runs opportunistically from the same
 * post-sync worker that raises notifications — no cron, no scheduled job, matching the app's
 * foreground-only posture (ADR-0012).
 *
 * The window is a **constant, not a setting**, deliberately: the sweep hard-deletes rather
 * than tombstones, and that is only resurrection-safe while every client computes the same
 * cutoff from the same number.
 */
class PruneExpiredNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
) {
    suspend operator fun invoke(): Int = repository.pruneExpired(RETENTION)

    companion object {
        val RETENTION: Duration = Duration.ofDays(60)
    }
}
