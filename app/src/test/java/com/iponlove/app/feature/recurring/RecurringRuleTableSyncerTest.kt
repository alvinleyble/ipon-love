package com.iponlove.app.feature.recurring

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleDto
import com.iponlove.app.feature.recurring.data.remote.RecurringRuleRemoteSource
import com.iponlove.app.feature.recurring.data.sync.RecurringRuleTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecurringRuleTableSyncerTest {

    private class FakeRemote : RecurringRuleRemoteSource {
        val pushed = mutableListOf<RecurringRuleDto>()
        val serverRows = mutableListOf<RecurringRuleDto>()

        override suspend fun push(rows: List<RecurringRuleDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun pull(cursor: Long, limit: Int): List<RecurringRuleDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)
    }

    private val dao = FakeRecurringRuleDao()
    private val remote = FakeRemote()
    private val cursors = InMemoryCursorStore()
    private val syncer = RecurringRuleTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun usesRecurringRulesTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.RECURRING_RULES)
    }

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = ruleEntity(id = "a", pendingSync = true)
        dao.store["b"] = ruleEntity(id = "b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += ruleDto(id = "a", amount = "1234.00", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.templateAmount.toPlainString()).isEqualTo("1234.00")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.RECURRING_RULES)).isEqualTo(12)
    }
}
