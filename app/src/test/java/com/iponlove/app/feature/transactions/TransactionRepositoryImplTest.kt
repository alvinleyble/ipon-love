package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class TransactionRepositoryImplTest {

    private val dao = FakeTransactionDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = TransactionRepositoryImpl(dao, clock, currentUser)

    @Test
    fun upsert_newTransaction_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertTransaction(txn("t1", TransactionType.EXPENSE, "100.00"))

        val row = dao.store.getValue("t1")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.isDeleted).isFalse()
        assertThat(row.serverRev).isNull()
        assertThat(row.recurringRuleId).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
        assertThat(row.createdAt).isEqualTo(now)
    }

    @Test
    fun upsert_existingTransaction_preservesProvenanceIncludingRecurringRule() = runTest {
        dao.store["t1"] = transactionEntity(
            id = "t1",
            userId = "owner-x",
            recurringRuleId = "rule-1",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        repository.upsertTransaction(txn("t1", TransactionType.EXPENSE, "250.00"))

        val row = dao.store.getValue("t1")
        assertThat(row.amount.toPlainString()).isEqualTo("250.00")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001)) // monotonic floor
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
        assertThat(row.recurringRuleId).isEqualTo("rule-1") // survives an edit
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["t1"] = transactionEntity(id = "t1", serverRev = 3)

        repository.deleteTransaction("t1")

        val row = dao.store.getValue("t1")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun observeTransactions_hidesDeleted_andOrdersMostRecentFirst() = runTest {
        dao.store["old"] = transactionEntity(id = "old", date = Instant.ofEpochMilli(1_000))
        dao.store["new"] = transactionEntity(id = "new", date = Instant.ofEpochMilli(5_000))
        dao.store["gone"] = transactionEntity(id = "gone", date = Instant.ofEpochMilli(9_000), isDeleted = true)

        val txns = repository.observeTransactions().first()

        assertThat(txns.map { it.id }).containsExactly("new", "old").inOrder()
    }
}
