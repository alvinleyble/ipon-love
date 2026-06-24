package com.iponlove.app.feature.transactions

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.transactions.data.remote.TransactionDto
import com.iponlove.app.feature.transactions.data.remote.PartnerTransactionDto
import com.iponlove.app.feature.transactions.data.remote.TransactionRemoteSource
import com.iponlove.app.feature.transactions.data.sync.TransactionTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Proves the transactions table is correctly wired into the generic sync engine. */
class TransactionTableSyncerTest {

    private class FakeTransactionRemoteSource : TransactionRemoteSource {
        val pushed = mutableListOf<TransactionDto>()
        val serverRows = mutableListOf<TransactionDto>()

        override suspend fun push(rows: List<TransactionDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun pull(cursor: Long, limit: Int): List<TransactionDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }
                .sortedBy { it.serverRev }
                .take(limit)

        override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerTransactionDto> = emptyList()
    }

    private val dao = FakeTransactionDao()
    private val remote = FakeTransactionRemoteSource()
    private val cursors = InMemoryCursorStore()
    private val syncer = TransactionTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun usesTransactionsTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.TRANSACTIONS)
    }

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = transactionEntity(id = "a", pendingSync = true)
        dao.store["b"] = transactionEntity(id = "b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += transactionDto(id = "a", amount = "42.00", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.amount.toPlainString()).isEqualTo("42.00")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(12)
    }
}
