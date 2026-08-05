package com.iponlove.app.feature.transactions.domain.usecase

import android.content.Context
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Startup sweep for `filesDir/receipts` files that were compressed at pick time
 * ([CompressReceiptUseCase]) but never got a `transaction_images` row — abandoning the editor
 * before save, or picking then removing a receipt (Item 14). Deletes any file whose id isn't on
 * record, uploaded or not; a pending-upload row's file is left alone (the uploader owns it).
 *
 * Age-guarded like [ReceiptScanFileStore.sweep][com.iponlove.app.feature.transactions.data.ReceiptScanFileStore.sweep]
 * and for the same reason (v1.7.3 Item 2, ADR-0062 decision 9): an unsaved draft holds its
 * compressed receipts by path in `SavedStateHandle` with no row behind them yet, so to this sweep
 * they look exactly like orphans. The camera hand-off makes a process death mid-draft routine —
 * without the guard, a restarted app would delete the restored draft's own photo. A file young
 * enough to belong to a live draft is left for the next cold start after it ages out.
 */
class CleanupOrphanedReceiptsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionImageRepository,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()) {
        val dir = File(context.filesDir, "receipts")
        val files = dir.listFiles() ?: return
        val knownIds = repository.allImageIds().toHashSet()
        val diskIds = files.filter { isOldEnough(it.lastModified(), now) }
            .associateBy { it.nameWithoutExtension }
        for (id in orphanIds(diskIds.keys, knownIds)) {
            diskIds.getValue(id).delete()
        }
    }

    companion object {
        /** A draft that outlives this is gone with its process; anything younger may still be a
         *  live editor's unsaved receipt. */
        const val MIN_AGE_MS = 24 * 60 * 60 * 1000L

        /** Pure orphan predicate: a file id with no matching row at all — deleted or not, uploaded
         *  or not — is unreachable by every other cleanup path, so it's safe to delete. */
        fun orphanIds(diskIds: Set<String>, knownIds: Set<String>): Set<String> = diskIds - knownIds

        /** Pure age predicate, kept separate so it's JVM-unit-testable without a filesystem. */
        fun isOldEnough(lastModifiedMs: Long, nowMs: Long, minAgeMs: Long = MIN_AGE_MS): Boolean =
            nowMs - lastModifiedMs >= minAgeMs
    }
}
