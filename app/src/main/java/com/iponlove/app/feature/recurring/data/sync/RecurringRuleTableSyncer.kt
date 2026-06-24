package com.iponlove.app.feature.recurring.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.recurring.data.local.RecurringRuleDao
import com.iponlove.app.feature.recurring.data.local.RecurringRuleEntity
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleRemoteSource
import com.iponlove.app.feature.recurring.data.toDto
import com.iponlove.app.feature.recurring.data.toEntity
import javax.inject.Inject

/**
 * Plugs the recurring_rules table into the generic sync engine. Rules are owner-only and
 * never a shared note, so the conflict-copy hooks keep their defaults (row-level LWW);
 * this only supplies the I/O and the entity↔DTO mapping.
 */
class RecurringRuleTableSyncer @Inject constructor(
    private val dao: RecurringRuleDao,
    private val remote: RecurringRuleRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<RecurringRuleEntity>(SyncTable.RECURRING_RULES, cursors, resolver) {

    override suspend fun dirtyRows(): List<RecurringRuleEntity> = dao.dirtyRows()

    override suspend fun clearPending(ids: List<String>) = dao.clearPending(ids)

    override suspend fun localRow(id: String): RecurringRuleEntity? = dao.getById(id)

    override suspend fun remotePush(rows: List<RecurringRuleEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<RecurringRuleEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<RecurringRuleEntity>) = dao.applyPullBatch(rows)
}
