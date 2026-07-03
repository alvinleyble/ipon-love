package com.iponlove.app.feature.savings.data.sync

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.remote.GoalContributionRemoteSource
import com.iponlove.app.feature.savings.data.toDto
import com.iponlove.app.feature.savings.data.toEntity
import javax.inject.Inject

/**
 * Plugs `goal_contributions` into the generic sync engine. Plain row-level LWW — each row is
 * owned solely by its contributor and has a unique id, so concurrent adds never conflict; an
 * edit/delete only touches its author's own row (ADR-0025).
 */
class GoalContributionTableSyncer @Inject constructor(
    private val dao: GoalContributionDao,
    private val goalDao: SavingsGoalDao,
    private val currentUser: CurrentUserProvider,
    private val remote: GoalContributionRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<GoalContributionEntity>(SyncTable.GOAL_CONTRIBUTIONS, cursors, resolver) {

    /**
     * Dirty contributions whose goal is still locally pushable — own, or shared-into-the-couple
     * and not deleted (mirrors the server's INSERT check). A contribution to a goal that was
     * unshared/deleted out from under it can never satisfy that INSERT check, so pushing it would
     * be rejected forever; skipping it keeps the row a benign local orphan instead (F1, ADR-0025).
     * The author's edits/deletes to a *still-pushable* goal go through normally.
     */
    override suspend fun dirtyRows(): List<GoalContributionEntity> {
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return dirty
        val pushable = goalDao.pushableGoalIds(currentUser.userId()).toHashSet()
        return dirty.filter { it.goalId in pushable }
    }
    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)
    override suspend fun localRow(id: String): GoalContributionEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<GoalContributionEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<GoalContributionEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<GoalContributionEntity>) =
        dao.applyPullBatch(rows)
}
