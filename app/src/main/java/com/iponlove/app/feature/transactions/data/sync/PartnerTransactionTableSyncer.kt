package com.iponlove.app.feature.transactions.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.transactions.data.local.TransactionDao
import com.iponlove.app.feature.transactions.data.local.TransactionEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionRemoteSource
import com.iponlove.app.feature.transactions.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's transactions from the `partner_transactions` view into the
 * shared `transactions` Room table (ADR-0004/0005). A private OR deleted partner txn
 * crosses with content nulled → purged locally (privacy guarantee, ADR-0005); a visible
 * shared txn is upserted, ready for the combined couple view.
 */
class PartnerTransactionTableSyncer @Inject constructor(
    private val dao: TransactionDao,
    private val remote: TransactionRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<TransactionEntity>(SyncTable.PARTNER_TRANSACTIONS, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<TransactionEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: TransactionEntity): Boolean = row.isPrivate || row.isDeleted
    override suspend fun hardDelete(id: String) = dao.deleteById(id)
    override suspend fun applyPullBatch(rows: List<TransactionEntity>) = dao.applyPullBatch(rows)
}
