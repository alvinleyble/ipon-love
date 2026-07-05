package com.iponlove.app.feature.tutorial.presentation

import com.iponlove.app.core.ui.CoachMarkStep

/**
 * Drives the single shell coach-mark overlay (ADR-0038). At most one tour is active at a time;
 * [steps] is the pairing-filtered, runtime-labeled step list for [activeTourId], and [stepIndex]
 * selects the visible one.
 */
data class TutorialUiState(
    val activeTourId: String? = null,
    val stepIndex: Int = 0,
    val steps: List<CoachMarkStep> = emptyList(),
) {
    val active: Boolean get() = activeTourId != null

    val currentStep: CoachMarkStep? get() = steps.getOrNull(stepIndex)
}
