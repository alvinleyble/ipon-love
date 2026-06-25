package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.remote.NoteDto
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.remote.PartnerNoteDto
import com.iponlove.app.feature.notes.data.sync.NoteTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class NoteTableSyncerTest {

    private class FakeRemote : NoteRemoteSource {
        val pushed = mutableListOf<NoteDto>()
        val serverRows = mutableListOf<NoteDto>()

        override suspend fun push(rows: List<NoteDto>): List<String> {
            pushed += rows
            return rows.map { it.id }
        }

        override suspend fun pull(cursor: Long, limit: Int): List<NoteDto> =
            serverRows.filter { (it.serverRev ?: 0L) > cursor }.sortedBy { it.serverRev }.take(limit)

        override suspend fun pullPartner(cursor: Long, limit: Int): List<PartnerNoteDto> = emptyList()
    }

    private val dao = FakeNoteDao()
    private val remote = FakeRemote()
    private val cursors = InMemoryCursorStore()
    private val clock = SyncClock(now = { Instant.ofEpochMilli(50_000) })
    private val syncer = NoteTableSyncer(dao, remote, clock, cursors, ConflictResolver())

    @Test
    fun usesNotesTable() {
        assertThat(syncer.table).isEqualTo(SyncTable.NOTES)
    }

    @Test
    fun push_mapsDirtyRowsToDto_andClearsAckedFlag() = runTest {
        dao.store["a"] = noteEntity(id = "a", pendingSync = true)
        dao.store["b"] = noteEntity(id = "b", pendingSync = false)

        syncer.push()

        assertThat(remote.pushed.map { it.id }).containsExactly("a")
        assertThat(dao.store.getValue("a").pendingSync).isFalse()
    }

    @Test
    fun pull_mapsRemoteRowsToEntities_andAdvancesCursor() = runTest {
        remote.serverRows += noteDto(id = "a", content = "<p>pulled</p>", serverRev = 12)

        syncer.pull()

        val row = dao.store.getValue("a")
        assertThat(row.content).isEqualTo("<p>pulled</p>")
        assertThat(row.pendingSync).isFalse()
        assertThat(cursors.cursor(SyncTable.NOTES)).isEqualTo(12)
    }

    @Test
    fun pull_sharedNote_conflictCopy_forksLocalEditsBeforeTakingRemote() = runTest {
        // Shared note: local has unpushed edits (updatedAt = 1000ms, pendingSync = true)
        // Remote arrives newer (updatedAt = 2000ms, serverRev = 5)
        dao.store["n"] = noteEntity(
            id = "n",
            title = "My Edited Title",
            content = "<p>local edits</p>",
            isShared = true,
            coupleId = "couple-1",
            updatedAt = Instant.ofEpochMilli(1_000),
            pendingSync = true,
        )
        remote.serverRows += noteDto(
            id = "n",
            title = "Partner Edit",
            content = "<p>partner version</p>",
            isShared = true,
            coupleId = "couple-1",
            updatedAt = Instant.ofEpochMilli(2_000),
            serverRev = 5,
        )

        syncer.pull()

        // The canonical note is now the remote version.
        val canonical = dao.store.getValue("n")
        assertThat(canonical.title).isEqualTo("Partner Edit")
        assertThat(canonical.isConflictCopy).isFalse()

        // A conflict-copy note was created with a new id, preserving the local edits.
        val conflictCopies = dao.store.values.filter { it.isConflictCopy }
        assertThat(conflictCopies).hasSize(1)
        val copy = conflictCopies.single()
        assertThat(copy.title).startsWith("[Conflict Copy]")
        assertThat(copy.content).isEqualTo("<p>local edits</p>")
        assertThat(copy.isShared).isFalse()
        assertThat(copy.coupleId).isNull()
        assertThat(copy.pendingSync).isTrue()
    }

    @Test
    fun pull_privateNote_noConflictCopy_localEditsLostToLww() = runTest {
        // Private note: dirty local, but not shared — standard LWW, no fork.
        dao.store["n"] = noteEntity(
            id = "n",
            title = "Local Draft",
            isShared = false,
            updatedAt = Instant.ofEpochMilli(1_000),
            pendingSync = true,
        )
        remote.serverRows += noteDto(
            id = "n",
            title = "Server Version",
            isShared = false,
            updatedAt = Instant.ofEpochMilli(2_000),
            serverRev = 3,
        )

        syncer.pull()

        assertThat(dao.store.getValue("n").title).isEqualTo("Server Version")
        assertThat(dao.store.values.none { it.isConflictCopy }).isTrue()
    }
}
