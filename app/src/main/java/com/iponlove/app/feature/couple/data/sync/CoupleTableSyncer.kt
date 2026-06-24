package com.iponlove.app.feature.couple.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.couple.data.local.CoupleDao
import com.iponlove.app.feature.couple.data.local.CoupleEntity
import com.iponlove.app.feature.couple.data.remote.CoupleRemoteSource
import com.iponlove.app.feature.couple.data.toDto
import com.iponlove.app.feature.couple.data.toEntity
import javax.inject.Inject

/**
 * Plugs the couples table into the sync engine. Push is effectively a no-op (couples are
 * RPC-only writes, so the outbox is always empty); pull fetches the caller's couple row
 * once paired, propagating a join from one partner to the other (ADR-0006) and an unpair's
 * soft-delete to both (ADR-0008). FK-ordered after users (`SyncTable.COUPLES`).
 */
class CoupleTableSyncer @Inject constructor(
    private val dao: CoupleDao,
    private val remote: CoupleRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<CoupleEntity>(SyncTable.COUPLES, cursors, resolver) {

    override suspend fun dirtyRows(): List<CoupleEntity> = dao.dirtyRows()
    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)
    override suspend fun localRow(id: String): CoupleEntity? = dao.getById(id)
    override suspend fun remotePush(rows: List<CoupleEntity>): List<String> =
        remote.push(rows.map { it.toDto() })
    override suspend fun remotePull(cursor: Long, limit: Int): List<CoupleEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }
    override suspend fun applyPullBatch(rows: List<CoupleEntity>) = dao.upsertAll(rows)
}
