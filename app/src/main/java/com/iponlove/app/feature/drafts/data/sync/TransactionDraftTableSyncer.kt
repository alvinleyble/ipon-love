package com.iponlove.app.feature.drafts.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.drafts.data.local.TransactionDraftDao
import com.iponlove.app.feature.drafts.data.local.TransactionDraftEntity
import com.iponlove.app.feature.drafts.data.remote.TransactionDraftRemoteSource
import com.iponlove.app.feature.drafts.data.toDto
import com.iponlove.app.feature.drafts.data.toEntity
import javax.inject.Inject

/**
 * Plugs `transaction_drafts` into the generic sync engine. Own-user-only rows with no shared-note
 * semantics, so the conflict-copy hooks keep their defaults: plain row-level LWW (ADR-0003) is the
 * intended resolution here — two devices editing one draft is last-writer-wins, which is
 * acceptable because a draft is an unfinished form and not a ledger row (ADR-0066 decision 4).
 *
 * Last in the FK order, appended after `NOTIFICATIONS` (contract §3.1): nothing depends on a
 * draft, and it must never delay a financial row's push.
 */
class TransactionDraftTableSyncer @Inject constructor(
    private val dao: TransactionDraftDao,
    private val remote: TransactionDraftRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<TransactionDraftEntity>(SyncTable.TRANSACTION_DRAFTS, cursors, resolver) {

    override suspend fun dirtyRows(): List<TransactionDraftEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): TransactionDraftEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<TransactionDraftEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<TransactionDraftEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<TransactionDraftEntity>) =
        dao.applyPullBatch(rows)
}
