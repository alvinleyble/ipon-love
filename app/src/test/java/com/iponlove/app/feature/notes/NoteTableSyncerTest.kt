package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.sync.ConflictResolver
import com.iponlove.app.core.sync.InMemoryCursorStore
import com.iponlove.app.core.sync.SyncTable
import com.iponlove.app.feature.notes.data.remote.NoteDto
import com.iponlove.app.feature.notes.data.remote.NoteRemoteSource
import com.iponlove.app.feature.notes.data.sync.NoteTableSyncer
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
    }

    private val dao = FakeNoteDao()
    private val remote = FakeRemote()
    private val cursors = InMemoryCursorStore()
    private val syncer = NoteTableSyncer(dao, remote, cursors, ConflictResolver())

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
}
