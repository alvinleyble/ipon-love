package com.iponlove.app.feature.drafts

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.drafts.data.sync.TransactionDraftTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionDraftTableSyncerTest {

    private val dao = FakeTransactionDraftDao()
    private val remote = FakeTransactionDraftRemote()
    private val cursors = InMemoryCursorStore()
    private val syncer = TransactionDraftTableSyncer(dao, remote, cursors, ConflictResolver())

    @Test
    fun usesTheTransactionDraftsTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.TRANSACTION_DRAFTS)
    }

    /**
     * Appended after `NOTIFICATIONS` (contract §3.1's booked amendment), so ordinals 1–23 are
     * unchanged and a draft can never delay a financial row's push.
     */
    @Test
    fun sortsLastInTheFkOrder_afterNotifications() {
        assertThat(SyncTable.TRANSACTION_DRAFTS.ordinal)
            .isEqualTo(SyncTable.entries.maxOf { it.ordinal })
        assertThat(SyncTable.TRANSACTION_DRAFTS.ordinal)
            .isGreaterThan(SyncTable.NOTIFICATIONS.ordinal)
    }

    @Test
    fun push_sendsDirtyRowsOnly_andClearsTheAckedFlag() = runTest {
        dao.store["a"] = draftEntity("a", pendingSync = true)
        dao.store["b"] = draftEntity("b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesTheCursor() = runTest {
        remote.serverRows += draftDto("a", amount = BigDecimal("75.00"), serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.amount).isEqualTo(BigDecimal("75.00"))
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.TRANSACTION_DRAFTS)).isEqualTo(12)
    }

    /** Row-level LWW is the intended resolution for a draft — an unfinished form, not a ledger row. */
    @Test
    fun pull_keepsTheNewerLocalEdit() = runTest {
        dao.store["a"] = draftEntity(
            "a",
            note = "local edit",
            updatedAt = Instant.ofEpochMilli(9_000),
            pendingSync = true,
        )
        remote.serverRows += draftDto(
            "a",
            note = "older remote",
            updatedAt = Instant.ofEpochMilli(1_000),
            serverRev = 5,
        )

        syncer.pull()

        assertThat(dao.store.getValue("a").note).isEqualTo("local edit")
    }

    /** A tombstone from another device retires the draft here too (ADR-0010). */
    @Test
    fun pull_appliesARemoteTombstone() = runTest {
        dao.store["a"] = draftEntity("a", updatedAt = Instant.ofEpochMilli(1_000))
        remote.serverRows += draftDto(
            "a",
            isDeleted = true,
            updatedAt = Instant.ofEpochMilli(9_000),
            serverRev = 7,
        )

        syncer.pull()

        assertThat(dao.store.getValue("a").isDeleted).isTrue()
    }
}
