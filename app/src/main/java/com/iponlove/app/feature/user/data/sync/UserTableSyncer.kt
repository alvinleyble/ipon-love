package com.iponlove.app.feature.user.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.data.toEntitlementWrite
import com.iponlove.app.feature.user.data.toEntity
import com.iponlove.app.feature.user.data.toPushDto
import javax.inject.Inject

/**
 * Plugs the users table into the sync engine. Push is self-only (RLS enforces it);
 * pull also returns the partner's row once paired (users_select policy covers both).
 */
class UserTableSyncer @Inject constructor(
    private val dao: UserDao,
    private val remote: UserRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<UserEntity>(SyncTable.USERS, cursors, resolver) {

    override suspend fun dirtyRows(): List<UserEntity> = dao.dirtyRows()
    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)
    override suspend fun localRow(id: String): UserEntity? = dao.getById(id)
    /**
     * Two writes, deliberately in one push (ADR-0060): the ordinary upsert can no longer carry
     * the entitlement columns, so they go through `set_self_entitlement` alongside it.
     *
     * Offline-first is preserved by *reusing the existing dirty-flag outbox* rather than adding
     * a marker: if either half throws, this method throws, [BaseTableSyncer.push] never reaches
     * `clearPending`, and the row simply retries on the next sync. The upsert runs first so a
     * genuine new signup's row exists before the RPC looks for it. Both halves are idempotent,
     * so a redo after a mid-push failure is a no-op.
     */
    override suspend fun remotePush(rows: List<UserEntity>): List<String> {
        val acked = remote.push(rows.map { it.toPushDto() })
        rows.forEach { remote.writeEntitlement(it.toEntitlementWrite()) }
        return acked
    }
    override suspend fun remotePull(cursor: Long, limit: Int): List<UserEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }
    override suspend fun applyPullBatch(rows: List<UserEntity>) = dao.upsertAll(rows)
}
