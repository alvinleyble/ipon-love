package com.iponlove.app.feature.tutorial.domain.usecase

import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import javax.inject.Inject

/**
 * The first-run tutorial gate (ADR-0034): show the walkthrough whenever it hasn't been seen on
 * this install. Unlike the onboarding gate ([com.iponlove.app.feature.onboarding.domain.usecase.ShouldShowOnboardingUseCase]),
 * this reads *only* the local flag — no sync/emptiness check — because the tutorial teaches where
 * things are, which a reinstalling user with real synced data still needs to relearn.
 */
class ShouldShowTutorialUseCase @Inject constructor(
    private val tutorialRepository: TutorialRepository,
) {
    suspend operator fun invoke(): Boolean = !tutorialRepository.isTutorialSeen()
}
