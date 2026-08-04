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

    override suspend fun getActiveDebts(coupleId: String): List<PartnerDebt> =
        dao.activeDebts(coupleId).map { it.toDomain() }

    override suspend fun getActivePayments(): List<DebtPayment> =
        dao.activePayments().map { it.toDomain() }

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
                // Set once at creation; survives edits (display-only, ADR-0019 #12).
                sourceTransactionId = existing?.sourceTransactionId ?: debt.sourceTransactionId,
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
        // Cascade soft-delete to all netting payments that reference this debt (either as
        // the direct payment or as the counter-debt side of an offsetting pair — ADR-0019).
        val nettingPayments = dao.nettingPaymentsForDebt(id)
        nettingPayments.forEach { p ->
            dao.upsertPayment(
                p.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(p.updatedAt),
                    pendingSync = true,
                ),
            )
        }
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
                isNetting = existing?.isNetting ?: payment.isNetting,
                counterDebtId = existing?.counterDebtId ?: payment.counterDebtId,
                // Settlement links are set once and immutable thereafter (existing wins);
                // receiver_txn_id is stamped later via [stampReceiverTxn], not here.
                payorAccountId = existing?.payorAccountId ?: payment.payorAccountId,
                payorTxnId = existing?.payorTxnId ?: payment.payorTxnId,
                receiverTxnId = existing?.receiverTxnId ?: payment.receiverTxnId,
                createdAt = existing?.createdAt ?: updatedAt,
                updatedAt = updatedAt,
                isDeleted = existing?.isDeleted ?: false,
                serverRev = existing?.serverRev,
                pendingSync = true,
            ),
        )
        syncTrigger.requestPush()
    }

    override suspend fun stampReceiverTxn(payorTxnId: String, receiverTxnId: String) {
        // One payor expense can back several payments when a lump was split across debts
        // (ADR-0055) — the whole group takes the same receiver leg.
        val group = dao.paymentsForPayorTxn(payorTxnId)
        // First writer wins per row — if the receiver leg was already added, don't double-stamp.
        val unstamped = group.filter { it.receiverTxnId == null }
        if (unstamped.isEmpty()) return
        unstamped.forEach { existing ->
            dao.upsertPayment(
                existing.copy(
                    receiverTxnId = receiverTxnId,
                    updatedAt = clock.stamp(existing.updatedAt),
                    pendingSync = true,
                ),
            )
        }
        syncTrigger.requestPush()
    }

    override suspend fun retirePaymentsForPayorTxn(payorTxnId: String) {
        val group = dao.paymentsForPayorTxn(payorTxnId)
        if (group.isEmpty()) return
        group.forEach { payment ->
            dao.upsertPayment(
                payment.copy(
                    isDeleted = true,
                    updatedAt = clock.stamp(payment.updatedAt),
                    pendingSync = true,
                ),
            )
        }
        syncTrigger.requestPush()
    }

    override suspend fun clearReceiverStamp(receiverTxnId: String) {
        val group = dao.paymentsForReceiverTxn(receiverTxnId)
        if (group.isEmpty()) return
        group.forEach { payment ->
            dao.upsertPayment(
                payment.copy(
                    receiverTxnId = null,
                    updatedAt = clock.stamp(payment.updatedAt),
                    pendingSync = true,
                ),
            )
        }
        syncTrigger.requestPush()
    }

    override suspend fun purgeCoupleDebts() {
        dao.deleteAllPayments()
        dao.deleteAllDebts()
    }
}
