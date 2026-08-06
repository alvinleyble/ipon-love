package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The parking area's contents, oldest first — a queue is worked off the front. */
class ObserveDraftsUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
) {
    operator fun invoke(): Flow<List<TransactionDraft>> = repository.observeDrafts()
}
