package com.iponlove.app.feature.categories.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.data.remote.CategoryRemoteSource
import com.iponlove.app.feature.categories.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's categories from the `partner_categories` view into the shared
 * `categories` Room table (ADR-0004/0005). Needed so partner transactions render with a
 * category name. Deleted → purged; visible → upserted.
 */
class PartnerCategoryTableSyncer @Inject constructor(
    private val dao: CategoryDao,
    private val remote: CategoryRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<CategoryEntity>(SyncTable.PARTNER_CATEGORIES, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<CategoryEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: CategoryEntity): Boolean = row.isDeleted
    override suspend fun hardDelete(id: String) = dao.deleteById(id)
    override suspend fun applyPullBatch(rows: List<CategoryEntity>) = dao.applyPullBatch(rows)
}
