package com.iponlove.app.feature.notifications.data

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.notifications.data.local.NotificationDao
import com.iponlove.app.feature.notifications.data.local.NotificationEntity
import com.iponlove.app.feature.notifications.data.remote.NotificationRemoteSource
import com.iponlove.app.feature.notifications.domain.model.AppNotification
import com.iponlove.app.feature.notifications.domain.model.NotificationCategory
import com.iponlove.app.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject

/**
 * Room-backed [NotificationRepository]. The single place every inbox write applies the sync
 * bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync` (ADR-0002).
 * Dismiss/clear-all are soft (ADR-0010); only the retention sweep deletes for real.
 */
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao,
    private val remote: NotificationRemoteSource,
    private val clock: SyncClock,
    private val currentUser: CurrentUserProvider,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : NotificationRepository {

    override fun observeInbox(): Flow<List<AppNotification>> =
        dao.observeInbox().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun record(
        id: String,
        category: NotificationCategory,
        title: String,
        body: String,
        deepLink: String?,
    ): Boolean {
        val stamped = clock.stamp(null)
        // insertIfAbsent is IGNORE-on-conflict, so this is atomic create-if-absent: a row that
        // already exists (read, or dismissed to a tombstone) is left exactly as it is and the
        // insert reports -1. Never an upsert — that would resurrect a dismissed notification.
        val inserted = dao.insertIfAbsent(
            NotificationEntity(
                id = id,
                userId = currentUser.userId(),
                category = category.key,
                title = title,
                body = body,
                deepLink = deepLink,
                isRead = false,
                createdAt = stamped,
                updatedAt = stamped,
                isDeleted = false,
                serverRev = null,
                pendingSync = true,
            ),
        )
        if (inserted == -1L) return false
        syncTrigger.requestPush()
        return true
    }

    override suspend fun raisedIds(prefix: String): Set<String> =
        dao.idsWithPrefix(prefix).toSet()

    override suspend fun markAllRead() {
        val unread = dao.unread()
        if (unread.isEmpty()) return
        dao.upsertAll(
            unread.map {
                it.copy(isRead = true, updatedAt = clock.stamp(it.updatedAt), pendingSync = true)
            },
        )
        syncTrigger.requestPush()
    }

    override suspend fun dismiss(id: String) {
        val existing = dao.getById(id) ?: return
        dao.upsertAll(
            listOf(
                existing.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(existing.updatedAt),
                    pendingSync = true,
                ),
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun clearAll() {
        val active = dao.active()
        if (active.isEmpty()) return
        dao.upsertAll(
            active.map {
                it.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(it.updatedAt),
                    pendingSync = true,
                )
            },
        )
        syncTrigger.requestPush()
    }

    override suspend fun pruneExpired(retention: Duration): Int {
        // Cutoff from the offset-corrected clock, not the raw device clock (ADR-0001): rows are
        // stamped with the same corrected time, and a skewed phone must not prune a different
        // window than the rest of the user's devices — the sweep hard-deletes, so a client that
        // ran early would just re-pull the rows another client still considers current.
        val expired = dao.expiredBefore(clock.stamp(null).minus(retention))
        if (expired.isEmpty()) return 0
        // Delete server-side FIRST, then locally: dropping the local row first would leave the
        // server copy orphaned with no client still holding its id to clean it up. A failed
        // remote call (offline, transient) prunes nothing and simply retries on the next sweep.
        //
        // Every expired id is sent, NOT just the ones whose local `serverRev` is populated: a row
        // this device pushed itself keeps `serverRev = null` locally (push only clears the dirty
        // flag, and the pull-back resolves to KeepLocal under LWW), so that column cannot tell
        // "never pushed" from "pushed and acked". A `delete ... where id in (…)` is a harmless
        // no-op for ids the server never had, which makes over-sending the safe direction.
        val ids = expired.map { it.id }
        if (runCatching { remote.delete(ids) }.isFailure) return 0
        dao.hardDelete(ids)
        return ids.size
    }
}
