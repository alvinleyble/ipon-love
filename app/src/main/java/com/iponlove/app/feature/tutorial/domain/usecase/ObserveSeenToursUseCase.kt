package com.iponlove.app.feature.tutorial.domain.usecase

import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the set of tour IDs already seen on this install (ADR-0038). Backs every tour's gate. */
class ObserveSeenToursUseCase @Inject constructor(
    private val tutorialRepository: TutorialRepository,
) {
    operator fun invoke(): Flow<Set<String>> = tutorialRepository.observeSeenTours()
}
