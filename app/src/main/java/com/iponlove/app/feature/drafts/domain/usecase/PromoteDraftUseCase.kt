package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import javax.inject.Inject

/**
 * Retires the draft a just-saved transaction came from — the second half of promotion.
 *
 * **Ordering is a rule, not a preference (ADR-0066 decision 5).** Promotion writes three tables
 * (`transactions` insert, `transaction_drafts` soft-delete, `transaction_images` inserts), which
 * looks like it needs an all-or-nothing write and contract §9's `SECURITY DEFINER` RPC. It does
 * not, because **the draft's id *is* the transaction's id**:
 *
 *  - Transaction first, retire second → a failed retire leaves a stale row in the queue; the user
 *    re-settles it, which is an **idempotent upsert of the same id**. Money can never double.
 *  - The reverse order loses data outright (draft gone, transaction never written).
 *
 * So call this **only after the transaction write has succeeded**, and never inside a transaction
 * that also wraps the write — the web client has no such primitive and must take the same path
 * (ADR-0063), so anything Android depends on here silently diverges the two.
 *
 * A no-op when [transactionId] names no active draft, which is the ordinary case for a
 * transaction that was never parked, and what makes re-promotion safe.
 */
class PromoteDraftUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
) {
    suspend operator fun invoke(transactionId: String) = repository.retireDraft(transactionId)
}
