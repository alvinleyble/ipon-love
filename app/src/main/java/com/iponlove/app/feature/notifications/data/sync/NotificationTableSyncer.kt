package com.iponlove.app.feature.notifications.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notifications.data.local.NotificationDao
import com.iponlove.app.feature.notifications.data.local.NotificationEntity
import com.iponlove.app.feature.notifications.data.remote.NotificationRemoteSource
import com.iponlove.app.feature.notifications.data.toDto
import com.iponlove.app.feature.notifications.data.toEntity
import javax.inject.Inject

/**
 * Plugs the notifications table into the generic sync engine. Own-user-only rows with no
 * shared-note semantics, so the conflict-copy hooks keep their defaults (row-level LWW) —
 * which is exactly right for the one field that can genuinely race: `isRead`, where the
 * later mark-as-read simply wins (ADR-0053).
 */
class NotificationTableSyncer @Inject constructor(
    private val dao: NotificationDao,
    private val remote: NotificationRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<NotificationEntity>(SyncTable.NOTIFICATIONS, cursors, resolver) {

    override suspend fun dirtyRows(): List<NotificationEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): NotificationEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<NotificationEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<NotificationEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<NotificationEntity>) = dao.applyPullBatch(rows)
}
