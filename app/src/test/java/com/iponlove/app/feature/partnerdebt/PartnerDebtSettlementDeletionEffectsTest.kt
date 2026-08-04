package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtSettlementDeletionEffects
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.transactions.data.toDomain
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.transactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The [PartnerDebtSettlementDeletionEffects] routing: only a settlement-flagged EXPENSE or
 * INCOME calls into the debt repository (ADR-0065); everything else — plain transactions,
 * transfers, adjustments — must be a no-op so ordinary deletes never touch the debt ledger.
 */
class PartnerDebtSettlementDeletionEffectsTest {

    private class RecordingPartnerDebtRepo : PartnerDebtRepository {
        val retiredPayorTxns = mutableListOf<String>()
        val clearedReceiverTxns = mutableListOf<String>()
        override fun observeDebts(coupleId: String): Flow<List<PartnerDebt>> = emptyFlow()
        override fun observePayments(): Flow<List<DebtPayment>> = emptyFlow()
        override suspend fun getDebt(id: String): PartnerDebt? = null
        override suspend fun getActiveDebts(coupleId: String): List<PartnerDebt> = emptyList()
        override suspend fun getActivePayments(): List<DebtPayment> = emptyList()
        override suspend fun upsertDebt(debt: PartnerDebt, coupleId: String) = Unit
        override suspend fun deleteDebt(id: String) = Unit
        override suspend fun upsertPayment(payment: DebtPayment) = Unit
        override suspend fun stampReceiverTxn(payorTxnId: String, receiverTxnId: String) = Unit
        override suspend fun retirePaymentsForPayorTxn(payorTxnId: String) {
            retiredPayorTxns += payorTxnId
        }
        override suspend fun clearReceiverStamp(receiverTxnId: String) {
            clearedReceiverTxns += receiverTxnId
        }
        override suspend fun purgeCoupleDebts() = Unit
    }

    private val repo = RecordingPartnerDebtRepo()
    private val effects = PartnerDebtSettlementDeletionEffects(repo)

    @Test
    fun settlementExpense_retiresPayorGroup() = runTest {
        val txn = transactionEntity(id = "txn-pay", type = TransactionType.EXPENSE, isSettlement = true).toDomain()

        effects.onTransactionDeleted(txn)

        assertThat(repo.retiredPayorTxns).containsExactly("txn-pay")
        assertThat(repo.clearedReceiverTxns).isEmpty()
    }

    @Test
    fun settlementIncome_clearsReceiverStamp() = runTest {
        val txn = transactionEntity(id = "txn-recv", type = TransactionType.INCOME, isSettlement = true).toDomain()

        effects.onTransactionDeleted(txn)

        assertThat(repo.clearedReceiverTxns).containsExactly("txn-recv")
        assertThat(repo.retiredPayorTxns).isEmpty()
    }

    @Test
    fun ordinaryExpense_isNoOp() = runTest {
        val txn = transactionEntity(id = "t", type = TransactionType.EXPENSE, isSettlement = false).toDomain()

        effects.onTransactionDeleted(txn)

        assertThat(repo.retiredPayorTxns).isEmpty()
        assertThat(repo.clearedReceiverTxns).isEmpty()
    }

    @Test
    fun transfer_isNoOp() = runTest {
        val txn = transactionEntity(id = "t", type = TransactionType.TRANSFER, isSettlement = false).toDomain()

        effects.onTransactionDeleted(txn)

        assertThat(repo.retiredPayorTxns).isEmpty()
        assertThat(repo.clearedReceiverTxns).isEmpty()
    }
}
