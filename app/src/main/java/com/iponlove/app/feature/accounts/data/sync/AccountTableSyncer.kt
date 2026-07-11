package com.iponlove.app.feature.accounts.data.sync

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.core.sync.isLocallyPushable
import com.iponlove.app.feature.accounts.data.local.AccountDao
import com.iponlove.app.feature.accounts.data.local.AccountEntity
import com.iponlove.app.feature.accounts.data.remote.AccountRemoteSource
import com.iponlove.app.feature.accounts.data.toDto
import com.iponlove.app.feature.accounts.data.toEntity
import javax.inject.Inject

/**
 * Plugs the accounts table into the generic sync engine. All the algorithm —
 * dirty-flag push, cursor pull, conflict resolution, idempotent apply, post-commit
 * cursor advance — lives in [BaseTableSyncer]; this only supplies the accounts I/O and
 * the entity↔DTO mapping.
 *
 * Accounts are never a shared note, so the conflict-copy hooks keep their defaults.
 */
class AccountTableSyncer @Inject constructor(
    private val dao: AccountDao,
    private val remote: AccountRemoteSource,
    private val currentUser: CurrentUserProvider,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<AccountEntity>(SyncTable.ACCOUNTS, cursors, resolver) {

    // Push only rows this session can actually own (v1.6.5 Item 20): a partner-owned row, a
    // stale-couple row from an unpair race, or an un-share reverted onto the partner would be
    // RLS-rejected and wedge the whole batch. Skipped rows stay benign local orphans.
    override suspend fun dirtyRows(): List<AccountEntity> {
        val me = currentUser.userId()
        val myCoupleId = dao.coupleIdOf(me)
        return dao.dirtyRows().filter { isLocallyPushable(it.userId, it.coupleId, me, myCoupleId) }
    }

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): AccountEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<AccountEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<AccountEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<AccountEntity>) = dao.applyPullBatch(rows)
}
