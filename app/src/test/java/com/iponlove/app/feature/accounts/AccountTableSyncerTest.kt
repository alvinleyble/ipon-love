package com.iponlove.app.feature.accounts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.accounts.data.remote.AccountDto
import com.iponlove.app.feature.accounts.data.remote.AccountRemoteSource
import com.iponlove.app.feature.accounts.data.remote.PartnerAccountDto
import com.iponlove.app.feature.accounts.data.sync.AccountTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/** Proves the accounts table is correctly wired into the generic sync engine. */
class AccountTableSyncerTest {

    private class FakeAccountRemoteSource : AccountRemoteSource {
        val pushed = mutableListOf<AccountDto>()
        val serverRows = mutableListOf<AccountDto>()
        var ack: (List<AccountDto>) -> List<String> = { rows -> rows.map { it.id } }

        override suspend fun push(rows: List<AccountDto>): List<String> {
            pushed += rows
            return ack(rows)
        }

        override suspend fun pull(cursor: Long, limit: Int): List<AccountDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }
                .sortedBy { it.serverRev }
                .take(limit)

        override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerAccountDto> = emptyList()
    }

    private val dao = FakeAccountDao()
    private val remote = FakeAccountRemoteSource()
    private val cursors = InMemoryCursorStore()
    private val syncer = AccountTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = accountEntity(id = "a", name = "GCash", pendingSync = true)
        dao.store["b"] = accountEntity(id = "b", name = "Clean", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += accountDto(id = "a", name = "BPI", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.name).isEqualTo("BPI")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.ACCOUNTS)).isEqualTo(12)
    }

    @Test
    fun pull_dirtyLocalNewer_keepsLocalEdit() = runTest {
        dao.store["a"] = accountEntity(
            id = "a",
            name = "Local edit",
            updatedAt = Instant.ofEpochMilli(9_000),
            pendingSync = true,
        )
        remote.serverRows += accountDto(
            id = "a",
            name = "Stale server",
            serverRev = 4,
            updatedAt = Instant.ofEpochMilli(1_000),
        )

        syncer.pull()

        // Local unpushed edit wins (LWW), but the cursor still advances past the seen rev.
        assertThat(dao.store.getValue("a").name).isEqualTo("Local edit")
        assertThat(dao.store.getValue("a").pendingSync).isTrue()
        assertThat(cursors.cursor(SyncTable.ACCOUNTS)).isEqualTo(4)
    }
}
