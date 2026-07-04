package com.iponlove.app.feature.tutorial.domain.usecase

import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import javax.inject.Inject

/** Records that the tutorial has been completed or skipped, so the first-run gate won't re-fire. */
class MarkTutorialSeenUseCase @Inject constructor(
    private val tutorialRepository: TutorialRepository,
) {
    suspend operator fun invoke() = tutorialRepository.setTutorialSeen()
}
