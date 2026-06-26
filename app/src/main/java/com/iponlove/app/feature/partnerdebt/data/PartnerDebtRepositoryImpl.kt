package com.iponlove.app.feature.partnerdebt.data

import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTrigger
import com.iponlove.app.feature.partnerdebt.data.local.DebtPaymentEntity
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtDao
import com.iponlove.app.feature.partnerdebt.data.local.PartnerDebtEntity
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [PartnerDebtRepository]. The single place every debt/payment write applies the
 * sync bookkeeping: a fresh monotonic `updated_at` (ADR-0001) and `pending_sync` (ADR-0002);
 * deletes are soft (ADR-0010). Couple ownership ([PartnerDebtEntity.coupleId]) is stamped on
 * create and survives edits.
 */
class PartnerDebtRepositoryImpl @Inject constructor(
    private val dao: PartnerDebtDao,
    private val clock: SyncClock,
    private val syncTrigger: SyncTrigger = SyncTrigger.NONE,
) : PartnerDebtRepository {

    override fun observeDebts(coupleId: String): Flow<List<PartnerDebt>> =
        dao.observeDebts(coupleId).map { rows -> rows.map { it.toDomain() } }

    override fun observePayments(): Flow<List<DebtPayment>> =
        dao.observePayments().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getDebt(id: String): PartnerDebt? = dao.getDebt(id)?.toDomain()

    override suspend fun upsertDebt(debt: PartnerDebt, coupleId: String) {
        val existing = dao.getDebt(debt.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsertDebt(
            PartnerDebtEntity(
                id = debt.id,
                coupleId = existing?.coupleId ?: coupleId,
                borrowerId = debt.borrowerId,
                lenderId = debt.lenderId,
                amount = debt.amount,
                description = debt.description,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun deleteDebt(id: String) {
        val existing = dao.getDebt(id) ?: return
        dao.upsertDebt(
            existing.copy(
                isDeleted = true,
                updatedAt = clock.stamp(existing.updatedAt),
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun upsertPayment(payment: DebtPayment) {
        val existing = dao.getPayment(payment.id)
        val updatedAt = clock.stamp(existing?.updatedAt)
        dao.upsertPayment(
            DebtPaymentEntity(
                id = payment.id,
                debtId = existing?.debtId ?: payment.debtId,
                amount = payment.amount,
                note = payment.note,
                date = payment.date,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun purgeCoupleDebts() {
        dao.deleteAllPayments()
        dao.deleteAllDebts()
    }
}
