package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
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
}
