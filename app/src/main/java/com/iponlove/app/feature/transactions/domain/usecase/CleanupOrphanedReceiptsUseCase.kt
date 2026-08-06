package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import com.iponlove.app.feature.transactions.data.ReceiptFileStore
import com.iponlove.app.feature.transactions.domain.repository.TransactionImageRepository
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
 *
 * **Parked drafts are learned about here, in the caller, not in the predicate** (ADR-0066
 * decision 6): a parked draft has no `transaction_images` row — it has no transaction — but it is
 * a genuine record pointing at the file, so its local image ids simply join `knownIds`. The pure
 * predicate below is untouched and its documented contract ("a file id with no matching row *at
 * all*") still holds exactly. A draft pulled from another device carries no local ids and
 * contributes nothing, which is correct: its files are not on this device either.
 */
class CleanupOrphanedReceiptsUseCase @Inject constructor(
    private val receiptFiles: ReceiptFileStore,
    private val repository: TransactionImageRepository,
    private val draftRepository: TransactionDraftRepository,
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()) {
        val files = receiptFiles.dir().listFiles() ?: return
        val knownIds = (repository.allImageIds() + draftRepository.allLocalImageIds()).toHashSet()
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
