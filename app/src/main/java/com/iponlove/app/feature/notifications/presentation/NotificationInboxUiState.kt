package com.iponlove.app.feature.notifications.presentation

import com.iponlove.app.feature.notifications.domain.model.AppNotification

/**
 * The inbox screen's state.
 *
 * [unreadOnEntry] is captured once when the screen opens, *before* the bulk mark-as-read runs:
 * opening the inbox clears the badge, but the rows the user hadn't seen yet must stay visually
 * highlighted for that visit (ADR-0053 read model) — which the rows' own `isRead` can no longer
 * tell us a moment later.
 */
data class NotificationInboxUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadOnEntry: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)
