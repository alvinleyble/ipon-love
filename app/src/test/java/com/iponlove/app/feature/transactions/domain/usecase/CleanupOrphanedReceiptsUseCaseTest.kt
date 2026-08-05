package com.iponlove.app.feature.transactions.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase.Companion.MIN_AGE_MS
import com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase.Companion.isOldEnough
import com.iponlove.app.feature.transactions.domain.usecase.CleanupOrphanedReceiptsUseCase.Companion.orphanIds
import org.junit.Test

/**
 * The pure orphan predicate behind Item 14's startup sweep: a file id with no matching
 * `transaction_images` row at all is unreachable by every other cleanup path, so it's safe to
 * delete. Deleted-but-known and uploaded-but-known rows are never candidates — they're excluded
 * simply by being present in [knownIds], regardless of their upload/deletion state.
 */
class CleanupOrphanedReceiptsUseCaseTest {

    @Test
    fun `file with no row at all is an orphan`() {
        assertThat(orphanIds(diskIds = setOf("abc"), knownIds = emptySet()))
            .containsExactly("abc")
    }

    @Test
    fun `file whose row exists and is already uploaded is not an orphan`() {
        // localPath = null, file absent is the normal post-upload case — but even if a stray
        // file happened to remain, its id is on record, so the sweep leaves it alone.
        assertThat(orphanIds(diskIds = setOf("uploaded-id"), knownIds = setOf("uploaded-id")))
            .isEmpty()
    }

    @Test
    fun `soft-deleted row pending upload is not an orphan`() {
        // The uploader owns this file's eventual cleanup, not the sweep — its id being on
        // record (deleted or not) is enough to exempt it.
        assertThat(orphanIds(diskIds = setOf("pending-delete-id"), knownIds = setOf("pending-delete-id")))
            .isEmpty()
    }

    @Test
    fun `mixed set only flags the unknown ids`() {
        assertThat(
            orphanIds(
                diskIds = setOf("orphan-1", "known-1", "orphan-2"),
                knownIds = setOf("known-1"),
            ),
        ).containsExactly("orphan-1", "orphan-2")
    }

    @Test
    fun `no files on disk yields no orphans`() {
        assertThat(orphanIds(diskIds = emptySet(), knownIds = setOf("known-1"))).isEmpty()
    }

    // --- Age guard (v1.7.3 Item 2, ADR-0062 decision 9) ------------------------------------------

    @Test
    fun `a receipt just attached to an unsaved draft is too young to sweep`() {
        // The camera hand-off case: the draft holds this path with no row behind it yet, so it
        // looks exactly like an orphan to the id predicate.
        assertThat(isOldEnough(lastModifiedMs = NOW - 60_000L, nowMs = NOW)).isFalse()
    }

    @Test
    fun `a file older than the guard is swept`() {
        assertThat(isOldEnough(lastModifiedMs = NOW - MIN_AGE_MS - 1, nowMs = NOW)).isTrue()
    }

    @Test
    fun `a file exactly at the guard is swept`() {
        assertThat(isOldEnough(lastModifiedMs = NOW - MIN_AGE_MS, nowMs = NOW)).isTrue()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
