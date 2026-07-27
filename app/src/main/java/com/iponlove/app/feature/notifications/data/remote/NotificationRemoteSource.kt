package com.iponlove.app.feature.notifications.data.remote

/**
 * Remote port for the `notifications` table.
 *
 * Unlike every other synced table this one also exposes a **hard** [delete] — the 60-day
 * retention sweep is a genuine deletion rather than a tombstone (ADR-0053 decision 4). User
 * dismiss / clear-all remain ordinary soft-deletes that sync through push like any other row.
 */
interface NotificationRemoteSource {
    suspend fun push(rows: List<NotificationDto>): List<String>

    suspend fun pull(cursor: Long, limit: Int): List<NotificationDto>

    /** Permanently removes [ids] server-side. Only ever called by the retention sweep. */
    suspend fun delete(ids: List<String>)
}
