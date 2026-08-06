package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import javax.inject.Inject

/** Loads one parked draft to hydrate back into the New transaction form. */
class GetDraftUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
) {
    suspend operator fun invoke(id: String): TransactionDraft? = repository.getDraft(id)
}
