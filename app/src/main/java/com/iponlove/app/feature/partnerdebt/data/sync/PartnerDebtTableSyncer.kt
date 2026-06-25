package com.iponlove.app.feature.partnerdebt.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtEntity
import com.iponlove.app.feature.partnerdebt.data.remote.PartnerDebtRemoteSource
import com.iponlove.app.feature.partnerdebt.data.toDto
import com.iponlove.app.feature.partnerdebt.data.toEntity
import javax.inject.Inject

/**
 * Plugs `partner_debts` into the generic sync engine — plain row-level LWW, no shared-note
 * conflict-copy. Pulls after couples/users so the couple FK parent is already present
 * (ADR-0009).
 */
class PartnerDebtTableSyncer @Inject constructor(
    private val dao: PartnerDebtDao,
    private val remote: PartnerDebtRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<PartnerDebtEntity>(SyncTable.PARTNER_DEBTS, cursors, resolver) {

    override suspend fun dirtyRows(): List<PartnerDebtEntity> = dao.dirtyDebts()

    override suspend fun clearPending(ids: List<String>) = dao.clearDebtPending(ids)

    override suspend fun localRow(id: String): PartnerDebtEntity? = dao.getDebt(id)

    override suspend fun remotePush(rows: List<PartnerDebtEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<PartnerDebtEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<PartnerDebtEntity>) = dao.applyDebtBatch(rows)
}
