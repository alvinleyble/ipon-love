package com.iponlove.app.feature.drafts.domain.usecase

import com.iponlove.app.feature.drafts.domain.repository.TransactionDraftRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Drives the pinned "Drafts (N)" card on Records — the anti-graveyard reminder (decision 10). */
class ObserveDraftCountUseCase @Inject constructor(
    private val repository: TransactionDraftRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeDraftCount()
}
