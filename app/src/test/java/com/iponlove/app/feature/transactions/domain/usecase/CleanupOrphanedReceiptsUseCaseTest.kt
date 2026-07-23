package com.iponlove.app.feature.transactions.domain.usecase

import com.google.common.truth.Truth.assertThat
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
}
