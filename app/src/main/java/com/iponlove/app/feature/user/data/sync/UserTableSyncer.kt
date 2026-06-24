package com.iponlove.app.feature.user.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.user.data.local.UserDao
import com.iponlove.app.feature.user.data.local.UserEntity
import com.iponlove.app.feature.user.data.remote.UserRemoteSource
import com.iponlove.app.feature.user.data.toDto
import com.iponlove.app.feature.user.data.toEntity
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
    override suspend fun remotePush(rows: List<UserEntity>): List<String> =
        remote.push(rows.map { it.toDto() })
    override suspend fun remotePull(cursor: Long, limit: Int): List<UserEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }
    override suspend fun applyPullBatch(rows: List<UserEntity>) = dao.upsertAll(rows)
}
