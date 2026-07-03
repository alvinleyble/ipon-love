package com.iponlove.app.feature.savings.data.sync

import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.GoalContributionEntity
import com.iponlove.app.feature.savings.data.remote.GoalContributionRemoteSource
import com.iponlove.app.feature.savings.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's contributions to shared goals from `partner_goal_contributions`
 * (ADR-0005). A contribution deleted by its author, OR whose parent goal is unshared/deleted,
 * crosses with content nulled — the mapper folds that (null amount) into `isDeleted`, so the
 * purge signal is uniformly `isDeleted`.
 */
class PartnerGoalContributionTableSyncer @Inject constructor(
    private val dao: GoalContributionDao,
    private val remote: GoalContributionRemoteSource,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<GoalContributionEntity>(SyncTable.PARTNER_GOAL_CONTRIBUTIONS, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<GoalContributionEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: GoalContributionEntity): Boolean = row.isDeleted
    override suspend fun hardDelete(id: String) = dao.deleteById(id)
    override suspend fun applyPullBatch(rows: List<GoalContributionEntity>) =
        dao.applyPullBatch(rows)
}
