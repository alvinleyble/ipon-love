package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import com.iponlove.app.feature.transactions.data.ReceiptFileStore
import javax.inject.Inject

/**
 * The user's explicit "delete this draft" — a soft delete (ADR-0010) that also **releases the
 * draft's receipt files**.
 *
 * That second half is load-bearing: the orphaned-receipt sweep only deletes a file no record
 * points at, and an active draft is now such a record (ADR-0066 decision 6). Retiring the row
 * without dropping the files would leave them referenced by nothing and swept later at best —
 * and would re-open the [v1.7.0 Item 14] storage leak through a new door at worst.
 *
 * Files are released here rather than in the repository so the data layer stays free of the
 * filesystem, matching how the editor owns its own unsaved receipts.
 */
class DeleteDraftUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
    private val receiptFiles: ReceiptFileStore,
) {
    suspend operator fun invoke(draftId: String) {
        val releasedImageIds = repository.deleteDraft(draftId)
        receiptFiles.delete(releasedImageIds)
    }
}
