package com.iponlove.app.feature.tutorial.presentation

/** Drives the coach-mark overlay in the app shell. When [active], [stepIndex] selects the step. */
data class TutorialUiState(
    val active: Boolean = false,
    val stepIndex: Int = 0,
)
