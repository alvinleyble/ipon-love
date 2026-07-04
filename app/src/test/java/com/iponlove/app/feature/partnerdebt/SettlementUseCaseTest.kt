package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.domain.usecase.AddSettlementIncomeUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.RecordDebtPaymentUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtUseCase
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Transaction-linked settlement (ADR-0019 #14): the payor leg ([SettleDebtUseCase]) records a
 * flagged EXPENSE plus the couple-owned [com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment]
 * that links it; the receiver leg ([AddSettlementIncomeUseCase]) records a flagged INCOME and
 * stamps `receiver_txn_id`. Both legs carry `is_settlement = true` (balance-affecting, not Analysis).
 */
class SettlementUseCaseTest {

    private class RecordingTransactionRepository : TransactionRepository {
        val upserted = mutableListOf<Transaction>()
        override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> =
            flowOf(emptyList())
        override fun observeHasAnyTransaction(): Flow<Boolean> = flowOf(false)
        override fun observeCombinedTransactions(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<OwnedTransaction>> = flowOf(emptyList())
        override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = flowOf(false)
        override fun observeBalanceLedger(): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun getTransaction(id: String): Transaction? = upserted.lastOrNull { it.id == id }
        override suspend fun upsertTransaction(transaction: Transaction) { upserted += transaction }
        override suspend fun deleteTransaction(id: String) = Unit
        override suspend fun materializeTransaction(transaction: Transaction, recurringRuleId: String) = false
        override suspend fun purgePartnerData() = Unit
    }

    private val dao = FakePartnerDebtDao()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(10_000) })
    private val debtRepo = PartnerDebtRepositoryImpl(dao, clock)
    private val txnRepo = RecordingTransactionRepository()
    private val upsertTransaction = UpsertTransactionUseCase(txnRepo)
    private val settleDebt = SettleDebtUseCase(upsertTransaction, RecordDebtPaymentUseCase(debtRepo))
    private val addIncome = AddSettlementIncomeUseCase(upsertTransaction, debtRepo)

    @Test
    fun settle_recordsFlaggedExpense_andLinkedPayment() = runTest {
        settleDebt(
            debtId = "d-1",
            amount = BigDecimal("500.00"),
            payorAccountId = "acc-1",
            note = "gcash",
            date = Instant.ofEpochMilli(5_000),
            transactionId = "txn-pay",
            paymentId = "p-1",
        )

        // The payor's expense leg: flagged, no category, on their account.
        val expense = txnRepo.upserted.single()
        assertThat(expense.id).isEqualTo("txn-pay")
        assertThat(expense.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(expense.isSettlement).isTrue()
        assertThat(expense.categoryId).isNull()
        assertThat(expense.accountId).isEqualTo("acc-1")

        // The payment links to that transaction + account; receiver leg not yet present.
        val payment = dao.payments.getValue("p-1")
        assertThat(payment.debtId).isEqualTo("d-1")
        assertThat(payment.amount.toPlainString()).isEqualTo("500.00")
        assertThat(payment.payorAccountId).isEqualTo("acc-1")
        assertThat(payment.payorTxnId).isEqualTo("txn-pay")
        assertThat(payment.receiverTxnId).isNull()
        assertThat(payment.isNetting).isFalse()
    }

    @Test
    fun settle_rejectsNonPositiveAmount() = runTest {
        val error = runCatching {
            settleDebt(debtId = "d-1", amount = BigDecimal("0.00"), payorAccountId = "acc-1", note = null)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(txnRepo.upserted).isEmpty()
        assertThat(dao.payments).isEmpty()
    }

    @Test
    fun addIncome_recordsFlaggedIncome_andStampsReceiverTxn() = runTest {
        // A payor-only settlement already exists.
        dao.payments["p-1"] = debtPaymentEntity(
            id = "p-1", debtId = "d-1", amount = "500.00",
            payorAccountId = "acc-1", payorTxnId = "txn-pay",
        )

        addIncome(
            paymentId = "p-1",
            amount = BigDecimal("500.00"),
            receiverAccountId = "acc-2",
            note = "dinner",
            transactionId = "txn-recv",
        )

        val income = txnRepo.upserted.single()
        assertThat(income.id).isEqualTo("txn-recv")
        assertThat(income.type).isEqualTo(TransactionType.INCOME)
        assertThat(income.isSettlement).isTrue()
        assertThat(income.categoryId).isNull()
        assertThat(income.accountId).isEqualTo("acc-2")

        assertThat(dao.payments.getValue("p-1").receiverTxnId).isEqualTo("txn-recv")
    }

    @Test
    fun stampReceiverTxn_firstWriterWins() = runTest {
        // Receiver leg already recorded once.
        dao.payments["p-1"] = debtPaymentEntity(
            id = "p-1", debtId = "d-1", amount = "500.00",
            payorAccountId = "acc-1", payorTxnId = "txn-pay", receiverTxnId = "txn-first",
        )

        addIncome(
            paymentId = "p-1",
            amount = BigDecimal("500.00"),
            receiverAccountId = "acc-2",
            note = "dinner",
            transactionId = "txn-second",
        )

        // The income leg is still recorded, but the existing receiver link is not overwritten.
        assertThat(txnRepo.upserted.single().id).isEqualTo("txn-second")
        assertThat(dao.payments.getValue("p-1").receiverTxnId).isEqualTo("txn-first")
    }
}
