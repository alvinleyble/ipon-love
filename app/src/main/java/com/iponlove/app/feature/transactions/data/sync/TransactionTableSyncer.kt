package com.iponlove.app.feature.transactions.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionRemoteSource
import com.iponlove.app.feature.transactions.data.toDto
import com.iponlove.app.feature.transactions.data.toEntity
import javax.inject.Inject

/**
 * Plugs the transactions table into the generic sync engine. Transactions are never a
 * shared note, so the conflict-copy hooks keep their defaults; this only supplies the
 * I/O and the entity↔DTO mapping.
 */
class TransactionTableSyncer @Inject constructor(
    private val dao: TransactionDao,
    private val remote: TransactionRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<TransactionEntity>(SyncTable.TRANSACTIONS, cursors, resolver) {

    override suspend fun dirtyRows(): List<TransactionEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): TransactionEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<TransactionEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<TransactionEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<TransactionEntity>) = dao.applyPullBatch(rows)
}
