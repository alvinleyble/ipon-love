package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.sync.SyncClock
import com.iponlove.app.feature.notes.data.NoteRepositoryImpl
import com.iponlove.app.feature.notes.domain.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class NoteRepositoryImplTest {

    private val dao = FakeNoteDao()
    private var now = Instant.ofEpochMilli(10_000)
    private val clock = SyncClock(now = { now })
    private val currentUser = CurrentUserProvider { "user-1" }
    private val repository = NoteRepositoryImpl(dao, clock, currentUser)

    @Test
    fun upsert_newNote_stampsOwnerAndSyncColumns() = runTest {
        repository.upsertNote(note("n", title = "Plans", contentHtml = "<p>hi</p>"))

        val row = dao.store.getValue("n")
        assertThat(row.userId).isEqualTo("user-1")
        assertThat(row.title).isEqualTo("Plans")
        assertThat(row.content).isEqualTo("<p>hi</p>")
        assertThat(row.isShared).isFalse()
        assertThat(row.pendingSync).isTrue()
        assertThat(row.serverRev).isNull()
        assertThat(row.updatedAt).isEqualTo(now)
    }

    @Test
    fun upsert_blankFields_storedAsNull() = runTest {
        repository.upsertNote(note("n", title = "   ", contentHtml = ""))

        val row = dao.store.getValue("n")
        assertThat(row.title).isNull()
        assertThat(row.content).isNull()
    }

    @Test
    fun upsert_existing_advancesUpdatedAtMonotonically_preservesProvenanceAndSharing() = runTest {
        dao.store["n"] = noteEntity(
            id = "n",
            userId = "owner-x",
            isShared = true,
            coupleId = "c-1",
            createdAt = Instant.ofEpochMilli(1_000),
            updatedAt = Instant.ofEpochMilli(10_000),
            serverRev = 55,
        )
        now = Instant.ofEpochMilli(10_000)

        repository.upsertNote(Note(id = "n", title = "Edited", contentHtml = "<p>new</p>"))

        val row = dao.store.getValue("n")
        assertThat(row.title).isEqualTo("Edited")
        assertThat(row.updatedAt).isEqualTo(Instant.ofEpochMilli(10_001))
        assertThat(row.pendingSync).isTrue()
        assertThat(row.userId).isEqualTo("owner-x")
        assertThat(row.createdAt).isEqualTo(Instant.ofEpochMilli(1_000))
        assertThat(row.serverRev).isEqualTo(55)
        // The editor never touches sharing — it must survive an edit.
        assertThat(row.isShared).isTrue()
        assertThat(row.coupleId).isEqualTo("c-1")
    }

    @Test
    fun upsert_existing_preservesPin() = runTest {
        dao.store["n"] = noteEntity(id = "n", isPinned = true, updatedAt = Instant.ofEpochMilli(1_000))

        repository.upsertNote(Note(id = "n", title = "Edited", contentHtml = "<p>new</p>"))

        // The editor never touches the pin — it must survive an edit.
        assertThat(dao.store.getValue("n").isPinned).isTrue()
    }

    @Test
    fun setPinned_setsFlag_marksDirty() = runTest {
        dao.store["n"] = noteEntity(id = "n", isPinned = false, updatedAt = Instant.ofEpochMilli(1_000))

        repository.setPinned("n", true)

        val row = dao.store.getValue("n")
        assertThat(row.isPinned).isTrue()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun setPinned_noOpWhenUnchanged_doesNotReDirty() = runTest {
        dao.store["n"] = noteEntity(id = "n", isPinned = true, pendingSync = false)

        repository.setPinned("n", true)

        // An idle re-tap must not stamp updated_at or re-dirty the row.
        assertThat(dao.store.getValue("n").pendingSync).isFalse()
    }

    @Test
    fun observeNotes_hoistsPinned_thenRecencyWithinGroup() = runTest {
        dao.store["old-pinned"] =
            noteEntity(id = "old-pinned", isPinned = true, updatedAt = Instant.ofEpochMilli(1_000))
        dao.store["recent-unpinned"] =
            noteEntity(id = "recent-unpinned", isPinned = false, updatedAt = Instant.ofEpochMilli(9_000))
        dao.store["mid-unpinned"] =
            noteEntity(id = "mid-unpinned", isPinned = false, updatedAt = Instant.ofEpochMilli(5_000))

        val ids = repository.observeNotes().first().map { it.id }

        // Pinned hoists above a more-recent unpinned note; unpinned group stays recency-ordered.
        assertThat(ids).containsExactly("old-pinned", "recent-unpinned", "mid-unpinned").inOrder()
    }

    @Test
    fun delete_isSoft_setsTombstoneAndMarksDirty() = runTest {
        dao.store["n"] = noteEntity(id = "n", serverRev = 3)

        repository.deleteNote("n")

        val row = dao.store.getValue("n")
        assertThat(row.isDeleted).isTrue()
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun shareNote_setsSharedFlagAndCoupleId_marksDirty() = runTest {
        dao.store["n"] = noteEntity(id = "n", isShared = false, coupleId = null)

        repository.shareNote("n", "couple-1")

        val row = dao.store.getValue("n")
        assertThat(row.isShared).isTrue()
        assertThat(row.coupleId).isEqualTo("couple-1")
        assertThat(row.pendingSync).isTrue()
    }

    @Test
    fun unshareNote_clearsSharedFlag_retainsCoupleId_marksDirty() = runTest {
        dao.store["n"] = noteEntity(id = "n", isShared = true, coupleId = "couple-1")

        repository.unshareNote("n")

        val row = dao.store.getValue("n")
        assertThat(row.isShared).isFalse()
        assertThat(row.coupleId).isEqualTo("couple-1") // retained so partner sees the un-share
        assertThat(row.pendingSync).isTrue()
    }
}
