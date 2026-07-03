package com.iponlove.app.feature.partnerdebt

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.partnerdebt.data.PartnerDebtRepositoryImpl
import com.iponlove.app.feature.partnerdebt.domain.usecase.ApplyDebtNettingUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.PaidOnBehalfUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.UpsertPartnerDebtUseCase
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
 * [PaidOnBehalfUseCase] wired to real partner-debt machinery (fake DAO) and a recording
 * transaction repository. Covers: full-pay vs bill-split owed amounts, the source-transaction
 * link, that the auto-created debt feeds #9 netting, and the owed-amount guards (and that a
 * rejected owed never records the transaction).
 */
class PaidOnBehalfUseCaseTest {

    /** Records what was upserted; the rest of the interface is unused by this use case. */
    private class RecordingTransactionRepository : TransactionRepository {
        val upserted = mutableListOf<Transaction>()
        override fun observeTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeCombinedTransactions(): Flow<List<OwnedTransaction>> = flowOf(emptyList())
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
    private val upsertDebt = UpsertPartnerDebtUseCase(debtRepo, ApplyDebtNettingUseCase(debtRepo, clock))
    private val txnRepo = RecordingTransactionRepository()
    private val upsertTransaction = UpsertTransactionUseCase(txnRepo)
    private val useCase = PaidOnBehalfUseCase(upsertTransaction, upsertDebt)

    private fun expense(id: String, amount: String, note: String? = "dinner") = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        accountId = "acc-1",
        categoryId = "cat-1",
        note = note,
        date = Instant.ofEpochMilli(5_000),
    )

    @Test
    fun fullPay_recordsTransactionAndCreatesMatchingDebt() = runTest {
        useCase(
            transaction = expense("t-1", "1000.00"),
            amountOwed = BigDecimal("1000.00"),
            borrowerId = "partner",
            lenderId = "me",
            coupleId = "c-1",
            debtId = "d-1",
        )

        // The real expense is recorded.
        assertThat(txnRepo.upserted.map { it.id }).containsExactly("t-1")

        // Debt: partner owes me the full amount, linked back to the source transaction.
        val debt = dao.debts.getValue("d-1")
        assertThat(debt.borrowerId).isEqualTo("partner")
        assertThat(debt.lenderId).isEqualTo("me")
        assertThat(debt.amount.toPlainString()).isEqualTo("1000.00")
        assertThat(debt.sourceTransactionId).isEqualTo("t-1")
        assertThat(debt.coupleId).isEqualTo("c-1")
        assertThat(debt.description).isEqualTo("dinner")
        assertThat(debt.pendingSync).isTrue()
    }

    @Test
    fun billSplit_debtUsesOwedAmount_transactionKeepsFullAmount() = runTest {
        useCase(
            transaction = expense("t-1", "1000.00"),
            amountOwed = BigDecimal("400.00"),
            borrowerId = "partner",
            lenderId = "me",
            coupleId = "c-1",
            debtId = "d-1",
        )

        assertThat(dao.debts.getValue("d-1").amount.toPlainString()).isEqualTo("400.00")
        assertThat(txnRepo.upserted.single().amount.toPlainString()).isEqualTo("1000.00")
    }

    @Test
    fun debt_feedsAutoNetting_againstOpposingOpenDebt() = runTest {
        // Already open: I owe partner 1000.
        dao.debts["d-old"] = partnerDebtEntity(
            id = "d-old", coupleId = "c-1",
            borrowerId = "me", lenderId = "partner", amount = "1000.00",
            createdAt = Instant.ofEpochMilli(1_000),
        )

        // I pay 1000 for partner → partner owes me 1000, which should net against d-old.
        useCase(
            transaction = expense("t-1", "1000.00"),
            amountOwed = BigDecimal("1000.00"),
            borrowerId = "partner",
            lenderId = "me",
            coupleId = "c-1",
            debtId = "d-new",
        )

        val netting = dao.payments.values.filter { it.isNetting }
        assertThat(netting).hasSize(2)
        val onNew = dao.payments.values.single { it.debtId == "d-new" }
        assertThat(onNew.counterDebtId).isEqualTo("d-old")
        assertThat(onNew.amount.toPlainString()).isEqualTo("1000.00")
    }

    @Test
    fun defaultDebtId_isDeterministicFromSourceTransaction_soReSaveDoesNotDoubleCount() = runTest {
        // Simulates process death between the debt commit and clearDraft(): the draft
        // (with the same stable transaction.id) survives and the user saves again.
        useCase(
            transaction = expense("t-1", "1000.00"),
            amountOwed = BigDecimal("1000.00"),
            borrowerId = "partner",
            lenderId = "me",
            coupleId = "c-1",
        )
        useCase(
            transaction = expense("t-1", "1000.00"),
            amountOwed = BigDecimal("1000.00"),
            borrowerId = "partner",
            lenderId = "me",
            coupleId = "c-1",
        )

        // One debt, not two — the second call upserted the same deterministic id.
        assertThat(dao.debts).hasSize(1)
    }

    @Test
    fun rejectsNonPositiveOwed_andDoesNotRecordTransaction() = runTest {
        val error = runCatching {
            useCase(
                transaction = expense("t-1", "1000.00"),
                amountOwed = BigDecimal("0.00"),
                borrowerId = "partner",
                lenderId = "me",
                coupleId = "c-1",
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(txnRepo.upserted).isEmpty()
        assertThat(dao.debts).isEmpty()
    }

    @Test
    fun rejectsOwedExceedingTransactionAmount_andDoesNotRecordTransaction() = runTest {
        val error = runCatching {
            useCase(
                transaction = expense("t-1", "1000.00"),
                amountOwed = BigDecimal("1500.00"),
                borrowerId = "partner",
                lenderId = "me",
                coupleId = "c-1",
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(txnRepo.upserted).isEmpty()
        assertThat(dao.debts).isEmpty()
    }
}
