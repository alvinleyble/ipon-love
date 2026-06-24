package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * In-memory [BasePartnerTableSyncer] exercising the partner-replica pull (ADR-0005):
 * push is a no-op, a redacted row is purged, a visible row is upserted, and the cursor
 * advances over the whole batch regardless of which way each row resolves.
 *
 * The test drives [shouldPurge] off [FakeRow.isDeleted] for simplicity — the real syncers
 * use the same shape (a per-row predicate over the entity's redaction flags).
 */
private class TestPartnerSyncer(
    cursors: SyncCursorStore,
    pageSize: Int = 500,
    val serverRows: MutableList<FakeRow> = mutableListOf(),
    val local: MutableMap<String, FakeRow> = mutableMapOf(),
) : BasePartnerTableSyncer<FakeRow>(SyncTable.PARTNER_TRANSACTIONS, cursors, pageSize) {

    val deletedIds = mutableListOf<String>()

    override suspend fun remotePullPartner(cursor: Long, limit: Int): List<FakeRow> =
        serverRows.filter { (it.serverRev ?: 0L) > cursor }
            .sortedBy { it.serverRev }
            .take(limit)

    override fun shouldPurge(row: FakeRow): Boolean = row.isDeleted

    override suspend fun hardDelete(id: String) {
        deletedIds += id
        local.remove(id)
    }

    override suspend fun applyPullBatch(rows: List<FakeRow>) {
        rows.forEach { local[it.id] = it }
    }
}

class BasePartnerTableSyncerTest {

    @Test
    fun push_isNoOp() = runTest {
        val syncer = TestPartnerSyncer(InMemoryCursorStore()).apply {
            local["mine"] = FakeRow("mine", at(1))
        }

        syncer.push()

        assertThat(syncer.local.keys).containsExactly("mine")
        assertThat(syncer.deletedIds).isEmpty()
    }

    @Test
    fun pull_visibleRow_isUpserted_andCursorAdvances() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestPartnerSyncer(cursors).apply {
            serverRows += FakeRow("a", at(10), serverRev = 5, isDeleted = false)
        }

        syncer.pull()

        assertThat(syncer.local.keys).containsExactly("a")
        assertThat(syncer.deletedIds).isEmpty()
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(5)
    }

    @Test
    fun pull_redactedRow_isPurged_notUpserted() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestPartnerSyncer(cursors).apply {
            local["a"] = FakeRow("a", at(1))                       // a stale visible copy
            serverRows += FakeRow("a", at(20), serverRev = 9, isDeleted = true)
        }

        syncer.pull()

        // The now-hidden row is removed locally, and the cursor still advances past it.
        assertThat(syncer.deletedIds).containsExactly("a")
        assertThat(syncer.local).doesNotContainKey("a")
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(9)
    }

    @Test
    fun pull_mixedBatch_upsertsVisible_purgesRedacted() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestPartnerSyncer(cursors).apply {
            serverRows += FakeRow("keep", at(10), serverRev = 1, isDeleted = false)
            serverRows += FakeRow("drop", at(11), serverRev = 2, isDeleted = true)
        }

        syncer.pull()

        assertThat(syncer.local.keys).containsExactly("keep")
        assertThat(syncer.deletedIds).containsExactly("drop")
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(2)
    }

    @Test
    fun pull_allRedacted_stillAdvancesCursor() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestPartnerSyncer(cursors).apply {
            serverRows += FakeRow("a", at(10), serverRev = 3, isDeleted = true)
            serverRows += FakeRow("b", at(11), serverRev = 4, isDeleted = true)
        }

        syncer.pull()

        assertThat(syncer.local).isEmpty()
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(4)
    }

    @Test
    fun pull_paginates_acrossPages() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestPartnerSyncer(cursors, pageSize = 2).apply {
            for (i in 1..5) serverRows += FakeRow("r$i", at(i.toLong()), serverRev = i.toLong())
        }

        syncer.pull()

        assertThat(syncer.local.keys).containsExactly("r1", "r2", "r3", "r4", "r5")
        assertThat(cursors.cursor(SyncTable.PARTNER_TRANSACTIONS)).isEqualTo(5)
    }
}
