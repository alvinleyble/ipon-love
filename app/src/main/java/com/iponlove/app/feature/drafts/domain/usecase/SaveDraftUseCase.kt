package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import javax.inject.Inject

/**
 * Parks (or re-parks) a draft — the `Save as draft` exit from the New transaction form.
 *
 * There is deliberately **no validation**: a draft that could pass `TransactionValidator` would
 * not need to be a draft. Amount, account and category may all be missing, which is exactly why
 * drafts live in their own table (ADR-0066 decision 1).
 */
class SaveDraftUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
) {
    suspend operator fun invoke(draft: TransactionDraft) = repository.saveDraft(draft)
}
