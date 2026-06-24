package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A fully in-memory [BaseTableSyncer] so the generic push/pull algorithm (ADR-0002,
 * ADR-0009) can be exercised without Room or Supabase.
 */
private class TestSyncer(
    cursors: SyncCursorStore,
    resolver: ConflictResolver,
    pageSize: Int = 500,
    val local: MutableMap<String, FakeRow> = mutableMapOf(),
    val serverRows: MutableList<FakeRow> = mutableListOf(),
    private val sharedNoteIds: Set<String> = emptySet(),
) : BaseTableSyncer<FakeRow>(SyncTable.TRANSACTIONS, cursors, resolver, pageSize) {

    val pushedBatches = mutableListOf<List<FakeRow>>()
    val clearedIds = mutableListOf<String>()
    val conflictCopied = mutableListOf<FakeRow>()
    var ack: (List<FakeRow>) -> List<String> = { rows -> rows.map { it.id } }
    var failApplyOnce = false

    override suspend fun dirtyRows(): List<FakeRow> = local.values.filter { it.pendingSync }

    override suspend fun clearPending(ids: List<String>) {
        clearedIds += ids
        ids.forEach { id -> local[id]?.let { local[id] = it.copy(pendingSync = false) } }
    }

    override suspend fun localRow(id: String): FakeRow? = local[id]

    override suspend fun remotePush(rows: List<FakeRow>): List<String> {
        pushedBatches += rows
        return ack(rows)
    }

    override suspend fun remotePull(cursor: Long, limit: Int): List<FakeRow> =
        serverRows.filter { (it.serverRev ?: 0L) > cursor }
            .sortedBy { it.serverRev }
            .take(limit)

    override suspend fun applyPullBatch(rows: List<FakeRow>) {
        if (failApplyOnce) {
            failApplyOnce = false
            throw IllegalStateException("simulated commit failure")
        }
        rows.forEach { local[it.id] = it }
    }

    override fun isSharedNote(row: FakeRow): Boolean = row.id in sharedNoteIds

    override suspend fun conflictCopy(local: FakeRow) {
        conflictCopied += local
    }
}

class BaseTableSyncerTest {

    private val resolver = ConflictResolver()

    @Test
    fun push_sendsOnlyDirtyRows_andClearsAckedOnes() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver).apply {
            local["clean"] = FakeRow("clean", at(1), serverRev = 1, pendingSync = false)
            local["dirty1"] = FakeRow("dirty1", at(2), pendingSync = true)
            local["dirty2"] = FakeRow("dirty2", at(3), pendingSync = true)
        }

        syncer.push()

        assertThat(syncer.pushedBatches.single().map { it.id })
            .containsExactly("dirty1", "dirty2")
        assertThat(syncer.local.values.none { it.pendingSync }).isTrue()
    }

    @Test
    fun push_clearsOnlyAckedRows_leavingUnackedDirty() = runTest {
        val syncer = TestSyncer(InMemoryCursorStore(), resolver).apply {
            local["a"] = FakeRow("a", at(1), pendingSync = true)
            local["b"] = FakeRow("b", at(2), pendingSync = true)
            ack = { rows -> rows.filter { it.id == "a" }.map { it.id } }
        }

        syncer.push()

        assertThat(syncer.clearedIds).containsExactly("a")
        assertThat(syncer.local.getValue("a").pendingSync).isFalse()
        assertThat(syncer.local.getValue("b").pendingSync).isTrue()
    }

    @Test
    fun push_noDirtyRows_isNoOp() = runTest {
        val syncer = TestSyncer(InMemoryCursorStore(), resolver).apply {
            local["a"] = FakeRow("a", at(1), serverRev = 1, pendingSync = false)
        }

        syncer.push()

        assertThat(syncer.pushedBatches).isEmpty()
        assertThat(syncer.clearedIds).isEmpty()
    }

    @Test
    fun pull_appliesRemoteRows_andAdvancesCursorToMaxServerRev() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver).apply {
            serverRows += FakeRow("a", at(10), serverRev = 5)
            serverRows += FakeRow("b", at(11), serverRev = 7)
        }

        syncer.pull()

        assertThat(syncer.local.keys).containsExactly("a", "b")
        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(7)
    }

    @Test
    fun pull_isIdempotent_secondRunPullsNothing() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver).apply {
            serverRows += FakeRow("a", at(10), serverRev = 5)
        }

        syncer.pull()
        syncer.pull()

        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(5)
    }

    @Test
    fun pull_paginates_andResumesAcrossPages() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver, pageSize = 2).apply {
            for (i in 1..5) serverRows += FakeRow("r$i", at(i.toLong()), serverRev = i.toLong())
        }

        syncer.pull()

        assertThat(syncer.local.keys).containsExactly("r1", "r2", "r3", "r4", "r5")
        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(5)
    }

    @Test
    fun pull_doesNotAdvanceCursor_whenCommitFails() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver).apply {
            serverRows += FakeRow("a", at(10), serverRev = 5)
            failApplyOnce = true
        }

        runCatching { syncer.pull() }

        // Commit threw before the cursor advanced — next run re-pulls (ADR-0009).
        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(0)
    }

    @Test
    fun pull_dirtyLocal_olderRemote_keepsLocalAndDoesNotApply() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver).apply {
            local["a"] = FakeRow("a", at(300), pendingSync = true)
            serverRows += FakeRow("a", at(100), serverRev = 5)
        }

        syncer.pull()

        // Local edit survived; cursor still advances past the seen rev.
        assertThat(syncer.local.getValue("a").updatedAt).isEqualTo(at(300))
        assertThat(syncer.local.getValue("a").pendingSync).isTrue()
        assertThat(cursors.cursor(SyncTable.TRANSACTIONS)).isEqualTo(5)
    }

    @Test
    fun pull_dirtySharedNote_newerRemote_conflictCopiesThenTakesRemote() = runTest {
        val cursors = InMemoryCursorStore()
        val syncer = TestSyncer(cursors, resolver, sharedNoteIds = setOf("note")).apply {
            local["note"] = FakeRow("note", at(100), pendingSync = true)
            serverRows += FakeRow("note", at(200), serverRev = 5)
        }

        syncer.pull()

        assertThat(syncer.conflictCopied.map { it.id }).containsExactly("note")
        assertThat(syncer.local.getValue("note").updatedAt).isEqualTo(at(200))
        assertThat(syncer.local.getValue("note").pendingSync).isFalse()
    }
}
