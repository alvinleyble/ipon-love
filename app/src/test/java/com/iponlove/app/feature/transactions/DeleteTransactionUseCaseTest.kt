package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SettlementDeletionEffects
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** ADR-0031: deleting a transfer must also retire its linked fee expense, one-directionally. */
class DeleteTransactionUseCaseTest {

    private val dao = FakeTransactionDao()
    private val repository = TransactionRepositoryImpl(
        dao = dao,
        clock = SyncClock(now = { Instant.ofEpochMilli(10_000) }),
        currentUser = CurrentUserProvider { "user-1" },
    )
    private val useCase = DeleteTransactionUseCase(repository)

    @Test
    fun deletingTransferWithFee_alsoSoftDeletesLinkedExpense() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(id = "transfer-1").copy(transferFeeTransactionId = "fee-1")

        useCase("transfer-1")

        assertThat(dao.store.getValue("transfer-1").isDeleted).isTrue()
        assertThat(dao.store.getValue("fee-1").isDeleted).isTrue()
    }

    @Test
    fun deletingTransferWithoutFee_isNoOpOnLinkedRow() = runTest {
        dao.store["transfer-1"] = transactionEntity(id = "transfer-1")

        useCase("transfer-1")

        assertThat(dao.store.getValue("transfer-1").isDeleted).isTrue()
    }

    @Test
    fun deletingLinkedFeeExpenseDirectly_neverCascadesBackToTransfer() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(id = "transfer-1").copy(transferFeeTransactionId = "fee-1")

        useCase("fee-1")

        assertThat(dao.store.getValue("fee-1").isDeleted).isTrue()
        assertThat(dao.store.getValue("transfer-1").isDeleted).isFalse()
    }

    @Test
    fun delete_runsInsideOneAtomicPass_andFiresSettlementEffectsForEachDeletedRow() = runTest {
        var runCount = 0
        val runner = LocalTransactionRunner { block ->
            runCount++
            block()
        }
        val deletedTransactions = mutableListOf<Transaction>()
        val effects = SettlementDeletionEffects { transaction -> deletedTransactions += transaction }
        val atomicUseCase = DeleteTransactionUseCase(repository, runner, effects)
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(id = "transfer-1").copy(transferFeeTransactionId = "fee-1")

        atomicUseCase("transfer-1")

        assertThat(runCount).isEqualTo(1)
        assertThat(deletedTransactions.map { it.id }).containsExactly("fee-1", "transfer-1")
    }

    @Test
    fun delete_onATransactionWithNoRow_isNoOp_neverFiresSettlementEffects() = runTest {
        val deletedTransactions = mutableListOf<Transaction>()
        val effects = SettlementDeletionEffects { transaction -> deletedTransactions += transaction }
        val atomicUseCase = DeleteTransactionUseCase(repository, LocalTransactionRunner { it() }, effects)

        atomicUseCase("never-existed")

        assertThat(deletedTransactions).isEmpty()
    }
}
