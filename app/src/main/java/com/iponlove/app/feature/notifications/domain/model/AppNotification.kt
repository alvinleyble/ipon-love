package com.iponlove.app.feature.notifications.domain.model

import java.time.Instant

/**
 * One row in the notification inbox — the **source of truth** for every notification the app
 * raises (ADR-0053). The OS system-tray push is a best-effort courtesy layered on top; the
 * inbox row is what survives a dropped push, a force-stopped app, or a swipe-away.
 *
 * [id] is **deterministic**, not random: each producing category derives it from the event
 * itself (`budget:{budgetId}:{yyyy-MM}:{slot}`, `recurring:{occurrenceId}`, `debt:{debtId}`),
 * so the same event detected independently on two clients merges into one row instead of
 * duplicating. Generation is create-if-absent — re-detecting an event must never overwrite an
 * existing row's read/dismissed state, which is also what makes the row's existence the dedup
 * record (retiring the old per-feature "already fired" stores).
 *
 * [deepLink] is the nav route to open on tap, or null for a non-navigating notification.
 */
data class AppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val deepLink: String?,
    val createdAt: Instant,
    val isRead: Boolean,
)
