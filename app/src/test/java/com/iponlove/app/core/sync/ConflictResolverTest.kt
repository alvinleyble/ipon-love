package com.iponlove.app.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** ADR-0003: row-level LWW by updated_at, with shared-note conflict-copy. */
class ConflictResolverTest {

    private val resolver = ConflictResolver()

    private fun resolve(local: FakeRow?, remote: FakeRow, sharedNote: Boolean = false) =
        resolver.resolve(local, remote, isSharedNote = sharedNote)

    @Test
    fun unknownRow_takesRemote() {
        val remote = FakeRow(id = "a", updatedAt = at(100), serverRev = 1)
        assertThat(resolve(local = null, remote = remote)).isEqualTo(SyncResolution.TakeRemote)
    }

    @Test
    fun cleanLocal_remoteNewer_takesRemote() {
        val local = FakeRow("a", updatedAt = at(100), pendingSync = false)
        val remote = FakeRow("a", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, remote)).isEqualTo(SyncResolution.TakeRemote)
    }

    @Test
    fun cleanLocal_remoteOlderOrEqual_keepsLocal() {
        val local = FakeRow("a", updatedAt = at(200), pendingSync = false)
        val older = FakeRow("a", updatedAt = at(100), serverRev = 9)
        val equal = FakeRow("a", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, older)).isEqualTo(SyncResolution.KeepLocal)
        assertThat(resolve(local, equal)).isEqualTo(SyncResolution.KeepLocal)
    }

    @Test
    fun dirtyLocal_remoteNewer_nonNote_takesRemoteDiscardingLocal() {
        val local = FakeRow("a", updatedAt = at(100), pendingSync = true)
        val remote = FakeRow("a", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, remote, sharedNote = false)).isEqualTo(SyncResolution.TakeRemote)
    }

    @Test
    fun dirtyLocal_remoteNewer_sharedNote_conflictCopies() {
        val local = FakeRow("note", updatedAt = at(100), pendingSync = true)
        val remote = FakeRow("note", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, remote, sharedNote = true)).isEqualTo(SyncResolution.ConflictCopy)
    }

    @Test
    fun dirtyLocal_remoteOlder_keepsLocal() {
        val local = FakeRow("a", updatedAt = at(300), pendingSync = true)
        val remote = FakeRow("a", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, remote)).isEqualTo(SyncResolution.KeepLocal)
    }

    @Test
    fun dirtyLocal_remoteEqual_keepsLocal_favoringUnpushedEdit() {
        // Tie favors the local unpushed edit even for a shared note (no needless fork).
        val local = FakeRow("note", updatedAt = at(200), pendingSync = true)
        val remote = FakeRow("note", updatedAt = at(200), serverRev = 9)
        assertThat(resolve(local, remote, sharedNote = true)).isEqualTo(SyncResolution.KeepLocal)
    }
}
