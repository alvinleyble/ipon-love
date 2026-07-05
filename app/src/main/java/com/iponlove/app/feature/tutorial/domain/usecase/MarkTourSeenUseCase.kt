package com.iponlove.app.feature.tutorial.domain.usecase

import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import javax.inject.Inject

/** Records that a single tour was completed or skipped, so its first-visit gate won't re-fire. */
class MarkTourSeenUseCase @Inject constructor(
    private val tutorialRepository: TutorialRepository,
) {
    suspend operator fun invoke(tourId: String) = tutorialRepository.markTourSeen(tourId)
}
