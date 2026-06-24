package com.iponlove.app.feature.budgets.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.budgets.data.local.BudgetDao
import com.iponlove.app.feature.budgets.data.local.BudgetEntity
import com.iponlove.app.feature.budgets.data.remote.BudgetRemoteSource
import com.iponlove.app.feature.budgets.data.toDto
import com.iponlove.app.feature.budgets.data.toEntity
import javax.inject.Inject

/**
 * Plugs the budgets table into the generic sync engine. Budgets are never a shared note,
 * so the conflict-copy hooks keep their defaults; this only supplies the I/O and the
 * entity↔DTO mapping.
 */
class BudgetTableSyncer @Inject constructor(
    private val dao: BudgetDao,
    private val remote: BudgetRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<BudgetEntity>(SyncTable.BUDGETS, cursors, resolver) {

    override suspend fun dirtyRows(): List<BudgetEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): BudgetEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<BudgetEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<BudgetEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<BudgetEntity>) = dao.applyPullBatch(rows)
}
