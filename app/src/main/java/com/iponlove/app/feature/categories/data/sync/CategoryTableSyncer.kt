package com.iponlove.app.feature.categories.data.sync

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.core.sync.isLocallyPushable
import com.iponlove.app.feature.categories.data.local.CategoryDao
import com.iponlove.app.feature.categories.data.local.CategoryEntity
import com.iponlove.app.feature.categories.data.remote.CategoryRemoteSource
import com.iponlove.app.feature.categories.data.toDto
import com.iponlove.app.feature.categories.data.toEntity
import javax.inject.Inject

/**
 * Plugs the categories table into the generic sync engine. Categories are never a shared
 * note, so the conflict-copy hooks keep their defaults; this only supplies the I/O and
 * the entity↔DTO mapping.
 */
class CategoryTableSyncer @Inject constructor(
    private val dao: CategoryDao,
    private val remote: CategoryRemoteSource,
    private val currentUser: CurrentUserProvider,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<CategoryEntity>(SyncTable.CATEGORIES, cursors, resolver) {

    // Push only rows this session can own (v1.6.5 Item 20) — see AccountTableSyncer.
    override suspend fun dirtyRows(): List<CategoryEntity> {
        val me = currentUser.userId()
        val myCoupleId = dao.coupleIdOf(me)
        return dao.dirtyRows().filter { isLocallyPushable(it.userId, it.coupleId, me, myCoupleId) }
    }

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): CategoryEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<CategoryEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<CategoryEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<CategoryEntity>) = dao.applyPullBatch(rows)
}
