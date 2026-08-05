package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.transactions.data.TransactionRepositoryImpl
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.BulkDeleteTransactionsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SettlementDeletionEffects
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Records' bulk delete (v1.7.3 Item 7 / ADR-0064): the dedupe that keeps a transfer ticked with
 * its own fee from writing the fee twice (Q3), the counts the confirmation names, and the
 * single-atomic-pass guarantee (Q4).
 */
class BulkDeleteTransactionsUseCaseTest {

    private val dao = FakeTransactionDao()
    private val repository = TransactionRepositoryImpl(
        dao = dao,
        clock = SyncClock(now = { Instant.ofEpochMilli(10_000) }),
        currentUser = CurrentUserProvider { "user-1" },
    )
    private var runCount = 0
    private val deleted = mutableListOf<Transaction>()
    private val useCase = BulkDeleteTransactionsUseCase(
        repository = repository,
        deleteTransaction = DeleteTransactionUseCase(
            repository = repository,
            settlementEffects = SettlementDeletionEffects { deleted += it },
        ),
        transactionRunner = LocalTransactionRunner { block -> runCount++; block() },
    )

    @Test
    fun deletesEverySelectedRow_inOneAtomicPass() = runTest {
        dao.store["a"] = transactionEntity(id = "a")
        dao.store["b"] = transactionEntity(id = "b")
        dao.store["c"] = transactionEntity(id = "c")

        useCase(setOf("a", "c"))

        assertThat(runCount).isEqualTo(1)
        assertThat(dao.store.getValue("a").isDeleted).isTrue()
        assertThat(dao.store.getValue("c").isDeleted).isTrue()
        assertThat(dao.store.getValue("b").isDeleted).isFalse()
    }

    @Test
    fun emptySelection_writesNothingAndOpensNoTransaction() = runTest {
        dao.store["a"] = transactionEntity(id = "a")

        useCase(emptySet())

        assertThat(runCount).isEqualTo(0)
        assertThat(dao.store.getValue("a").isDeleted).isFalse()
    }

    // ---- Q3: a transfer and its own fee row ticked together ----

    @Test
    fun transferSelectedWithItsOwnFee_writesTheFeeExactlyOnce() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(
            id = "transfer-1",
            type = TransactionType.TRANSFER,
            categoryId = null,
            transferFeeTransactionId = "fee-1",
        )

        useCase(setOf("transfer-1", "fee-1"))

        assertThat(dao.store.getValue("transfer-1").isDeleted).isTrue()
        assertThat(dao.store.getValue("fee-1").isDeleted).isTrue()
        // The cascade retires the fee; had the fee also been a deletion root it would have been
        // written a second time, spuriously re-stamping updated_at on an already-tombstoned row.
        assertThat(deleted.map { it.id }).containsExactly("fee-1", "transfer-1")
    }

    @Test
    fun feeRowSelectedWithoutItsTransfer_isStillDeletedOnItsOwn() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(
            id = "transfer-1",
            type = TransactionType.TRANSFER,
            categoryId = null,
            transferFeeTransactionId = "fee-1",
        )

        useCase(setOf("fee-1"))

        assertThat(dao.store.getValue("fee-1").isDeleted).isTrue()
        assertThat(dao.store.getValue("transfer-1").isDeleted).isFalse()
    }

    // ---- the plan behind the confirmation dialog ----

    @Test
    fun plan_countsTheFeeRowDraggedInByASelectedTransfer() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(
            id = "transfer-1",
            type = TransactionType.TRANSFER,
            categoryId = null,
            transferFeeTransactionId = "fee-1",
        )

        val plan = useCase.plan(setOf("transfer-1"))

        assertThat(plan.rowCount).isEqualTo(2)
        assertThat(plan.effectiveIds).containsExactly("transfer-1", "fee-1")
        assertThat(plan.deletionRoots).containsExactly("transfer-1")
        assertThat(plan.untickedFeeCount).isEqualTo(1)
    }

    @Test
    fun plan_doesNotDoubleCountAFeeTheUserTickedThemselves() = runTest {
        dao.store["fee-1"] = transactionEntity(id = "fee-1")
        dao.store["transfer-1"] = transactionEntity(
            id = "transfer-1",
            type = TransactionType.TRANSFER,
            categoryId = null,
            transferFeeTransactionId = "fee-1",
        )

        val plan = useCase.plan(setOf("transfer-1", "fee-1"))

        assertThat(plan.rowCount).isEqualTo(2)
        assertThat(plan.untickedFeeCount).isEqualTo(0)
        assertThat(plan.deletionRoots).containsExactly("transfer-1")
    }

    @Test
    fun plan_countsSettlementRows_soTheConfirmationCanWarn() = runTest {
        dao.store["settle-1"] = transactionEntity(id = "settle-1", isSettlement = true, categoryId = null)
        dao.store["plain-1"] = transactionEntity(id = "plain-1")

        val plan = useCase.plan(setOf("settle-1", "plain-1"))

        assertThat(plan.settlementCount).isEqualTo(1)
        assertThat(plan.rowCount).isEqualTo(2)
    }

    @Test
    fun plan_doesNotWarnOnAdjustmentRows_theyAreOrdinaryHere() = runTest {
        // ADR-0057 decision 1 / ADR-0064 decision 5: a balance adjustment is a plain ledger row
        // whose documented repair path is deleting it. No special-casing, no warning.
        dao.store["adj-1"] = transactionEntity(id = "adj-1", isAdjustment = true, categoryId = null)

        val plan = useCase.plan(setOf("adj-1"))

        assertThat(plan.settlementCount).isEqualTo(0)
        assertThat(plan.untickedFeeCount).isEqualTo(0)
        assertThat(plan.rowCount).isEqualTo(1)
    }

    @Test
    fun plan_ignoresIdsWithNoLiveRow_soTheCountNeverOverstates() = runTest {
        dao.store["alive"] = transactionEntity(id = "alive")
        dao.store["gone"] = transactionEntity(id = "gone", isDeleted = true)

        val plan = useCase.plan(setOf("alive", "gone", "never-existed"))

        assertThat(plan.rowCount).isEqualTo(1)
        assertThat(plan.effectiveIds).containsExactly("alive")
    }

    @Test
    fun plan_onAnEmptySelection_isEmpty() = runTest {
        assertThat(useCase.plan(emptySet()).isEmpty).isTrue()
    }

    // ---- Q6: a deleted recurring occurrence must stay resolved, not resurface as pending ----

    @Test
    fun bulkDeletingAMaterializedOccurrence_leavesItsIdInTheMaterializedSet() = runTest {
        dao.store["rule-1:2026-08-01"] =
            transactionEntity(id = "rule-1:2026-08-01", recurringRuleId = "rule-1")

        useCase(setOf("rule-1:2026-08-01"))

        assertThat(dao.store.getValue("rule-1:2026-08-01").isDeleted).isTrue()
        // Soft delete keeps the row (ADR-0010), and the materialized-id query has no isDeleted
        // filter — so the occurrence stays resolved and materialization won't re-create it.
        assertThat(repository.observeMaterializedRecurringIds().first())
            .containsExactly("rule-1:2026-08-01")
    }

    // ---- Q8: Records is own-rows-only, so a partner row can never be ticked ----

    @Test
    fun partnerReplicatedRows_areNeverInTheSelectableSet() = runTest {
        dao.store["mine"] = transactionEntity(id = "mine", userId = "user-1")
        dao.store["theirs"] = transactionEntity(id = "theirs", userId = "partner-2")

        // The Records list — the only source of ids selection can reach — is user-scoped, so the
        // selection needs no partner-exclusion logic of its own.
        assertThat(repository.observeTransactions().first().map { it.id }).containsExactly("mine")
    }
}
