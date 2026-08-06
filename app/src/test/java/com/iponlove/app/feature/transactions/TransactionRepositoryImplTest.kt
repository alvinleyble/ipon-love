package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.session.LastActiveUserStore
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

    // ---- persisted-id fallback for the balance ledger (Item 10 follow-up) -------------
    // The widget's balance math reads this on a cold/backgrounded process with no live session;
    // it must resolve the owner from the durable LastActiveUserStore, matching observeAccounts.

    /** A [CurrentUserProvider] with no live session — `userId()` throws, so `userIdOrNull()` is null. */
    private val noSession = CurrentUserProvider { error("no authenticated user") }
    private fun lastActive(id: String?) = object : LastActiveUserStore {
        override suspend fun lastUserId(): String? = id
        override suspend fun setLastUserId(id: String) {}
    }

    @Test
    fun balanceLedger_fallsBackToPersistedId_whenLiveSessionNull() = runTest {
        // Private + owned by user-1 → only surfaces when the query is scoped to user-1.
        dao.store["t1"] = transactionEntity(id = "t1", userId = "user-1", isPrivate = true)
        val repo = TransactionRepositoryImpl(dao, clock, noSession, lastActiveUser = lastActive("user-1"))

        assertThat(repo.observeBalanceLedger().first().map { it.id }).containsExactly("t1")
    }

    @Test
    fun balanceLedger_emitsEmpty_whenNoLiveSessionAndNoPersistedId() = runTest {
        dao.store["t1"] = transactionEntity(id = "t1", userId = "user-1", isPrivate = true)
        val repo = TransactionRepositoryImpl(dao, clock, noSession, lastActiveUser = lastActive(null))

        assertThat(repo.observeBalanceLedger().first()).isEmpty()
    }

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

    // ---- receipt-scan history reads (v1.7.3 Item 2 Slice 2, ADR-0062 decision 5) --------------

    @Test
    fun ownExpenseHistory_excludesPartnerRows_andRowsWithNothingToMatchOn() = runTest {
        // Decision 5's "own rows only": a partner row (ADR-0004) carries category/account ids that
        // aren't even usable on the user's own row, so it must never reach the matcher.
        dao.store["mine"] = transactionEntity(id = "mine", note = "SM Supermarket")
        dao.store["partner"] = transactionEntity(id = "partner", userId = "user-2", note = "SM Supermarket")
        dao.store["blank"] = transactionEntity(id = "blank", note = " ")
        dao.store["settled"] = transactionEntity(id = "settled", note = "Settlement", isSettlement = true)
        dao.store["adjusted"] = transactionEntity(id = "adjusted", note = "Correction", isAdjustment = true)
        dao.store["income"] = transactionEntity(id = "income", type = TransactionType.INCOME, note = "Payday")

        assertThat(repository.getOwnExpenseHistory(500).map { it.id }).containsExactly("mine")
    }

    @Test
    fun ownExpenseHistory_isCappedToTheMostRecentRows() = runTest {
        repeat(5) { i ->
            dao.store["t$i"] = transactionEntity(id = "t$i", note = "Shop", date = Instant.ofEpochMilli(1_000L * i))
        }

        assertThat(repository.getOwnExpenseHistory(2).map { it.id }).containsExactly("t4", "t3").inOrder()
    }

    @Test
    fun ownExpensesBetween_isOwnedAndWindowed() = runTest {
        dao.store["inside"] = transactionEntity(id = "inside", date = Instant.ofEpochMilli(5_000))
        dao.store["before"] = transactionEntity(id = "before", date = Instant.ofEpochMilli(999))
        dao.store["onEnd"] = transactionEntity(id = "onEnd", date = Instant.ofEpochMilli(9_000))
        dao.store["partner"] = transactionEntity(id = "partner", userId = "user-2", date = Instant.ofEpochMilli(5_000))

        val rows = repository.getOwnExpensesBetween(Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(9_000))

        assertThat(rows.map { it.id }).containsExactly("inside")
    }
}
