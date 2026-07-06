package com.iponlove.app.feature.transactions.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.transactions.data.local.TransactionImageDao
import com.iponlove.app.feature.transactions.data.local.TransactionImageEntity
import com.iponlove.app.feature.transactions.data.remote.TransactionImageRemoteSource
import com.iponlove.app.feature.transactions.data.toDto
import com.iponlove.app.feature.transactions.data.toEntity
import javax.inject.Inject

/**
 * Syncs the `transaction_images` table. [dirtyRows] filters to uploaded-only rows
 * (`url IS NOT NULL`) — pre-upload rows are handled by [com.iponlove.app.feature.transactions.data.upload.TransactionImageUploader]
 * which runs before the standard push/pull loop in [com.iponlove.app.core.sync.SyncEngine].
 */
class TransactionImageTableSyncer @Inject constructor(
    private val dao: TransactionImageDao,
    private val remote: TransactionImageRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<TransactionImageEntity>(SyncTable.TRANSACTION_IMAGES, cursors, resolver) {

    override suspend fun dirtyRows(): List<TransactionImageEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): TransactionImageEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<TransactionImageEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<TransactionImageEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<TransactionImageEntity>) =
        dao.applyPullBatch(rows)
}
