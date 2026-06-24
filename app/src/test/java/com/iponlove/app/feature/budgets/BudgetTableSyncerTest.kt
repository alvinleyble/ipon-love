package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.budgets.data.remote.BudgetDto
import com.iponlove.app.feature.budgets.data.remote.BudgetRemoteSource
import com.iponlove.app.feature.budgets.data.sync.BudgetTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BudgetTableSyncerTest {

    private class FakeBudgetRemoteSource : BudgetRemoteSource {
        val pushed = mutableListOf<BudgetDto>()
        val serverRows = mutableListOf<BudgetDto>()

        override suspend fun push(rows: List<BudgetDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun pull(cursor: Long, limit: Int): List<BudgetDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
    }

    private val dao = FakeBudgetDao()
    private val remote = FakeBudgetRemoteSource()
    private val cursors = InMemoryCursorStore()
    private val syncer = BudgetTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun usesBudgetsTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.BUDGETS)
    }

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = budgetEntity(id = "a", pendingSync = true)
        dao.store["b"] = budgetEntity(id = "b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += budgetDto(id = "a", amount = "1234.00", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.amount.toPlainString()).isEqualTo("1234.00")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.BUDGETS)).isEqualTo(12)
    }
}
