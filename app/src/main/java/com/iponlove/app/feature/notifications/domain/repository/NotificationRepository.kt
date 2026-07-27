package com.iponlove.app.feature.notifications.domain.repository

import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import kotlinx.coroutines.flow.Flow
import java.time.Duration

/** The notification inbox — the synced source of truth for every notification (ADR-0053). */
interface NotificationRepository {

    /** Newest-first, dismissed rows excluded. */
    fun observeInbox(): Flow<List<AppNotification>>

    /** Drives the bell's unread badge. */
    fun observeUnreadCount(): Flow<Int>

    /**
     * Create-if-absent. Returns **true only when a new row was created** — producers use that
     * to decide whether to also raise the best-effort OS push, which is what makes the inbox
     * row itself the dedup record (ADR-0053 decision 3). A re-detected event returns false and
     * leaves the existing row's read/dismissed state untouched.
     */
    suspend fun record(
        id: String,
        category: NotificationCategory,
        title: String,
        body: String,
        deepLink: String? = null,
    ): Boolean

    /**
     * Every already-raised id starting with [prefix], including dismissed ones — lets a
     * producer filter a whole batch with one query instead of one round trip per candidate.
     */
    suspend fun raisedIds(prefix: String): Set<String>

    /** Marks every unread row read — the bulk-clear on opening the inbox. */
    suspend fun markAllRead()

    /** User dismiss: an ordinary soft-delete that syncs (ADR-0010). */
    suspend fun dismiss(id: String)

    /** User clear-all: soft-deletes every visible row. */
    suspend fun clearAll()

    /**
     * Retention sweep: **hard**-deletes rows older than [retention] locally and server-side
     * (ADR-0053 decision 4). Resurrection-safe because every client computes the same cutoff.
     * Best-effort — a failed remote delete leaves the row for the next sweep rather than
     * orphaning it server-side. Returns the number of rows removed.
     */
    suspend fun pruneExpired(retention: Duration): Int
}
