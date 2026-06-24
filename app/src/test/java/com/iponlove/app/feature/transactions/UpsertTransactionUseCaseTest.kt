package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class UpsertTransactionUseCaseTest {

    private val dao = FakeTransactionDao()
    private val repository = TransactionRepositoryImpl(
        dao = dao,
        clock = SyncClock(now = { Instant.ofEpochMilli(1_000) }),
        currentUser = CurrentUserProvider { "user-1" },
    )
    private val upsert = UpsertTransactionUseCase(repository)

    @Test
    fun transfer_normalisesAwayCategory_andKeepsDestination() = runTest {
        val transfer = Transaction(
            id = "t1",
            type = TransactionType.TRANSFER,
            amount = BigDecimal("100.00"),
            accountId = "acc-1",
            toAccountId = "acc-2",
            categoryId = "cat-1", // should be cleared on save
            date = Instant.ofEpochMilli(1_000),
        )

        upsert(transfer)

        val row = dao.store.getValue("t1")
        assertThat(row.categoryId).isNull()
        assertThat(row.toAccountId).isEqualTo("acc-2")
    }

    @Test
    fun incomeOrExpense_normalisesAwayDestination() = runTest {
        val expense = Transaction(
            id = "t1",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("100.00"),
            accountId = "acc-1",
            toAccountId = "acc-2", // nonsensical for an expense; should be cleared
            categoryId = "cat-1",
            date = Instant.ofEpochMilli(1_000),
        )

        upsert(expense)

        assertThat(dao.store.getValue("t1").toAccountId).isNull()
    }

    @Test
    fun invalidTransaction_throws_andDoesNotPersist() = runTest {
        val zeroAmount = Transaction(
            id = "t1",
            type = TransactionType.EXPENSE,
            amount = BigDecimal.ZERO,
            accountId = "acc-1",
            categoryId = "cat-1",
            date = Instant.ofEpochMilli(1_000),
        )

        val result = runCatching { upsert(zeroAmount) }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dao.store).isEmpty()
    }
}
