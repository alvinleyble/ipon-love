package com.iponlove.app.feature.savings.data.sync

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.BasePartnerTableSyncer
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.savings.data.local.GoalContributionDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalDao
import com.iponlove.app.feature.savings.data.local.SavingsGoalEntity
import com.iponlove.app.feature.savings.data.remote.SavingsGoalRemoteSource
import com.iponlove.app.feature.savings.data.toEntity
import javax.inject.Inject

/**
 * Replicates the partner's shared goals from `partner_savings_goals` (ADR-0004/0005). An
 * unshared OR deleted goal crosses with content nulled → purged; a shared, non-deleted goal is
 * upserted. Purging a goal replica cascades to purge the partner's contribution replicas for it
 * (safety net for the ordering window before those rows get their own redaction, ADR-0025).
 */
class PartnerSavingsGoalTableSyncer @Inject constructor(
    private val dao: SavingsGoalDao,
    private val contributionDao: GoalContributionDao,
    private val remote: SavingsGoalRemoteSource,
    private val currentUser: CurrentUserProvider,
    cursors: SyncCursorStore,
) : BasePartnerTableSyncer<SavingsGoalEntity>(SyncTable.PARTNER_SAVINGS_GOALS, cursors) {

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<SavingsGoalEntity> =
        remote.pullPartner(cursor, limit).map { it.toEntity() }

    override fun shouldPurge(row: SavingsGoalEntity): Boolean = !row.isShared || row.isDeleted

    override suspend fun hardDelete(id: String) {
        dao.deleteById(id)
        contributionDao.deleteByGoalNotOwnedBy(id, currentUser.userId())
    }

    override suspend fun applyPullBatch(rows: List<SavingsGoalEntity>) = dao.applyPullBatch(rows)
}
