package com.iponlove.app.feature.tutorial.domain.usecase

import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import javax.inject.Inject

/** The "Replay tutorial" reset: clears every seen tour ID so all tours re-arm (ADR-0038 dec. 5). */
class ClearAllToursUseCase @Inject constructor(
    private val tutorialRepository: TutorialRepository,
) {
    suspend operator fun invoke() = tutorialRepository.clearAllTours()
}
