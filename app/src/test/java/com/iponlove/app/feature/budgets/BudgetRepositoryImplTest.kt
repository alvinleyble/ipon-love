package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.budgets.data.BudgetRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class BudgetRepositoryImplTest {

    private val dao = FakeBudgetDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = BudgetRepositoryImpl(dao, clock, currentUser)

    @Test
    fun upsert_newBudget_isPersonal_andStampsSyncColumns() = runTest {
        repository.upsertBudget(budget("b", categoryId = "cat-1", amount = "5000.00"))

        val row = dao.store.getValue("b")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.coupleId).isNull() // personal budget
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun upsert_existingBudget_advancesUpdatedAtMonotonically_andPreservesProvenance() = runTest {
        dao.store["b"] = budgetEntity(
            id = "b",
            userId = "owner-x",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        repository.upsertBudget(budget("b", categoryId = "cat-1", amount = "7500.00"))

        val row = dao.store.getValue("b")
        assertThat(row.amount.toPlainString()).isEqualTo("7500.00")
        assertThat(row.pendingSync).isTrue()
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["b"] = budgetEntity(id = "b", serverRev = 3)

        repository.deleteBudget("b")

        val row = dao.store.getValue("b")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }
}
