package com.iponlove.app.feature.savings.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.data.remote.SavingsGoalRemoteSource
import com.iponlove.app.feature.savings.data.toDto
import com.iponlove.app.feature.savings.data.toEntity
import javax.inject.Inject

/**
 * Plugs `savings_goals` into the generic sync engine. Plain row-level LWW — metadata is
 * creator-owned, so there's no shared-mutable field to conflict-copy (unlike shared notes).
 */
class SavingsGoalTableSyncer @Inject constructor(
    private val dao: SavingsGoalDao,
    private val remote: SavingsGoalRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<SavingsGoalEntity>(SyncTable.SAVINGS_GOALS, cursors, resolver) {

    override suspend fun dirtyRows(): List<SavingsGoalEntity> = dao.dirtyRows()
    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)
    override suspend fun localRow(id: String): SavingsGoalEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<SavingsGoalEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<SavingsGoalEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<SavingsGoalEntity>) = dao.applyPullBatch(rows)
}
