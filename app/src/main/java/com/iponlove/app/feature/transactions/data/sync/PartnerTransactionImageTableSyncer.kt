package com.iponlove.app.feature.transactions.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionImageRemoteSource
import com.iponlove.app.feature.transactions.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's receipt images from the `partner_transaction_images` view
 * (ADR-0004/0005). A row is purged when [TransactionImageEntity.url] is null (the parent
 * transaction became private/deleted, or the image was removed) or the row is deleted;
 * otherwise the image URL is upserted for display in the combined view.
 */
class PartnerTransactionImageTableSyncer @Inject constructor(
    private val dao: TransactionImageDao,
    private val remote: TransactionImageRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<TransactionImageEntity>(SyncTable.PARTNER_TRANSACTION_IMAGES, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<TransactionImageEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: TransactionImageEntity): Boolean =
        row.url == null || row.isDeleted

    override suspend fun hardDelete(id: String) = dao.deleteById(id)

    override suspend fun applyPullBatch(rows: List<TransactionImageEntity>) =
        dao.applyPullBatch(rows)
}
