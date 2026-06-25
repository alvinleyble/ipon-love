package com.iponlove.app.feature.partnerdebt.data.sync

import com.iponlove.app.core.sync.BaseTableSyncer
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.SyncCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.partnerdebt.data.local.DebtPaymentEntity
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.remote.DebtPaymentRemoteSource
import com.iponlove.app.feature.partnerdebt.data.toDto
import com.iponlove.app.feature.partnerdebt.data.toEntity
import javax.inject.Inject

/**
 * Plugs `partner_debt_payments` into the generic sync engine — plain row-level LWW. Pulls
 * after [PartnerDebtTableSyncer] so the debt FK parent is already present (ADR-0009).
 */
class DebtPaymentTableSyncer @Inject constructor(
    private val dao: PartnerDebtDao,
    private val remote: DebtPaymentRemoteSource,
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
) : BaseTableSyncer<DebtPaymentEntity>(SyncTable.DEBT_PAYMENTS, cursors, resolver) {

    override suspend fun dirtyRows(): List<DebtPaymentEntity> = dao.dirtyPayments()

    override suspend fun clearPending(ids: List<String>) = dao.clearPaymentPending(ids)

    override suspend fun localRow(id: String): DebtPaymentEntity? = dao.getPayment(id)

    override suspend fun remotePush(rows: List<DebtPaymentEntity>): List<String> =
        remote.push(rows.map { it.toDto() })

    override suspend fun remotePull(cursor: Long, limit: Int): List<DebtPaymentEntity> =
        remote.pull(cursor, limit).map { it.toEntity() }

    override suspend fun applyPullBatch(rows: List<DebtPaymentEntity>) = dao.applyPaymentBatch(rows)
}
