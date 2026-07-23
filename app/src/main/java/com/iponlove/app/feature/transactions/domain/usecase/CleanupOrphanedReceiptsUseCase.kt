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
 */
class CleanupOrphanedReceiptsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionImageRepository,
) {
    suspend operator fun invoke() {
        val dir = File(context.filesDir, "receipts")
        val files = dir.listFiles() ?: return
        val knownIds = repository.allImageIds().toHashSet()
        val diskIds = files.associateBy { it.nameWithoutExtension }
        for (id in orphanIds(diskIds.keys, knownIds)) {
            diskIds.getValue(id).delete()
        }
    }

    companion object {
        /** Pure orphan predicate: a file id with no matching row at all — deleted or not, uploaded
         *  or not — is unreachable by every other cleanup path, so it's safe to delete. */
        fun orphanIds(diskIds: Set<String>, knownIds: Set<String>): Set<String> = diskIds - knownIds
    }
}
