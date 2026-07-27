package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.domain.usecase.AddSettlementIncomeUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocation
import com.iponlove.app.feature.partnerdebt.domain.usecase.RecordDebtPaymentUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtsUseCase
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
 * Transaction-linked settlement (ADR-0019 #14, ADR-0055): the payor leg ([SettleDebtsUseCase])
 * records **one** flagged EXPENSE for the lump plus one couple-owned
 * [com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment] per allocation, all sharing
 * that expense's id; the receiver leg ([AddSettlementIncomeUseCase]) records one flagged INCOME
 * for the lump and stamps `receiver_txn_id` across the whole group. Both legs carry
 * `is_settlement = true` (balance-affecting, not Analysis).
 */
class SettlementUseCaseTest {

    private class RecordingTransactionRepository : TransactionRepository {
        val upserted = mutableListOf<Transaction>()
        override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeTransactions(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> =
            flowOf(emptyList())
        override fun observeHasAnyTransaction(): Flow<Boolean> = flowOf(false)
        override fun observeMaterializedRecurringIds(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeCombinedTransactions(
            startInclusive: Instant,
            endExclusive: Instant,
        ): Flow<List<OwnedTransaction>> = flowOf(emptyList())
        override fun observeCombinedTransactionsUnbounded(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeHasAnyCombinedTransaction(): Flow<Boolean> = flowOf(false)
        override fun observeBalanceLedger(): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun getTransaction(id: String): Transaction? = upserted.lastOrNull { it.id == id }
        override suspend fun countByCategory(categoryId: String): Int = 0
        override suspend fun countByAccount(accountId: String): Int = 0
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
    // Real Room transactions need an opened database, so the atomic seam runs the block inline.
    private val transactionRunner = LocalTransactionRunner { block -> block() }
    private val settleDebts = SettleDebtsUseCase(
        transactionRunner,
        upsertTransaction,
        RecordDebtPaymentUseCase(debtRepo),
    )
    private val addIncome = AddSettlementIncomeUseCase(transactionRunner, upsertTransaction, debtRepo)

    @Test
    fun settle_recordsFlaggedExpense_andLinkedPayment() = runTest {
        settleDebts(
            allocations = listOf(DebtAllocation("d-1", BigDecimal("500.00"))),
            payorAccountId = "acc-1",
            note = "gcash",
            date = Instant.ofEpochMilli(5_000),
            transactionId = "txn-pay",
            paymentIds = listOf("p-1"),
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
            settleDebts(
                allocations = listOf(DebtAllocation("d-1", BigDecimal("0.00"))),
                payorAccountId = "acc-1",
                note = null,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(txnRepo.upserted).isEmpty()
        assertThat(dao.payments).isEmpty()
    }

    @Test
    fun cascade_recordsOneExpenseForTheLump_andOnePaymentPerDebt() = runTest {
        settleDebts(
            allocations = listOf(
                DebtAllocation("d-1", BigDecimal("300.00")),
                DebtAllocation("d-2", BigDecimal("200.00")),
                DebtAllocation("d-3", BigDecimal("50.00")),
            ),
            payorAccountId = "acc-1",
            note = "payday",
            date = Instant.ofEpochMilli(5_000),
            transactionId = "txn-lump",
            paymentIds = listOf("p-1", "p-2", "p-3"),
        )

        // The money left the account once — one expense, for the whole lump (ADR-0055 #5).
        val expense = txnRepo.upserted.single()
        assertThat(expense.id).isEqualTo("txn-lump")
        assertThat(expense.type).isEqualTo(TransactionType.EXPENSE)
        assertThat(expense.amount.toPlainString()).isEqualTo("550.00")
        assertThat(expense.isSettlement).isTrue()

        // One payment per debt, each carrying its own share and all linked to that one expense.
        assertThat(dao.payments.keys).containsExactly("p-1", "p-2", "p-3")
        assertThat(dao.payments.values.map { it.debtId }).containsExactly("d-1", "d-2", "d-3")
        assertThat(dao.payments.values.map { it.amount.toPlainString() })
            .containsExactly("300.00", "200.00", "50.00")
        assertThat(dao.payments.values.map { it.payorTxnId }.distinct()).containsExactly("txn-lump")
        assertThat(dao.payments.values.none { it.isNetting }).isTrue()
    }

    @Test
    fun cascade_writesNothingWhenTheTransactionFails() = runTest {
        // The atomic seam stands in for Room's rollback: a failure mid-batch discards the lot.
        val failing = LocalTransactionRunner { block ->
            runCatching { block() }
            dao.payments.clear()
            txnRepo.upserted.clear()
        }
        val atomicSettle = SettleDebtsUseCase(failing, upsertTransaction, RecordDebtPaymentUseCase(debtRepo))

        atomicSettle(
            allocations = listOf(
                DebtAllocation("d-1", BigDecimal("300.00")),
                // A zero share can't happen through the calculator, but if one ever reached
                // here it must take the expense down with it rather than leave it standing.
                DebtAllocation("d-2", BigDecimal("0.00")),
            ),
            payorAccountId = "acc-1",
            note = null,
            transactionId = "txn-lump",
            paymentIds = listOf("p-1", "p-2"),
        )

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
            payorTxnId = "txn-pay",
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
    fun addIncome_stampsEveryPaymentTheLumpBacks() = runTest {
        // One payor expense split across three debts (ADR-0055 #6) — one income clears them all.
        listOf("p-1" to "d-1", "p-2" to "d-2", "p-3" to "d-3").forEach { (paymentId, debtId) ->
            dao.payments[paymentId] = debtPaymentEntity(
                id = paymentId, debtId = debtId, amount = "100.00",
                payorAccountId = "acc-1", payorTxnId = "txn-lump",
            )
        }
        // An unrelated settlement that must stay untouched.
        dao.payments["p-other"] = debtPaymentEntity(
            id = "p-other", debtId = "d-4", amount = "70.00",
            payorAccountId = "acc-1", payorTxnId = "txn-other",
        )

        addIncome(
            payorTxnId = "txn-lump",
            amount = BigDecimal("300.00"),
            receiverAccountId = "acc-2",
            note = "payday",
            transactionId = "txn-recv",
        )

        // One income for the lump, not one per debt.
        val income = txnRepo.upserted.single()
        assertThat(income.amount.toPlainString()).isEqualTo("300.00")
        assertThat(dao.payments.getValue("p-1").receiverTxnId).isEqualTo("txn-recv")
        assertThat(dao.payments.getValue("p-2").receiverTxnId).isEqualTo("txn-recv")
        assertThat(dao.payments.getValue("p-3").receiverTxnId).isEqualTo("txn-recv")
        assertThat(dao.payments.getValue("p-other").receiverTxnId).isNull()
    }

    @Test
    fun stampReceiverTxn_firstWriterWins() = runTest {
        // Receiver leg already recorded once.
        dao.payments["p-1"] = debtPaymentEntity(
            id = "p-1", debtId = "d-1", amount = "500.00",
            payorAccountId = "acc-1", payorTxnId = "txn-pay", receiverTxnId = "txn-first",
        )

        addIncome(
            payorTxnId = "txn-pay",
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
