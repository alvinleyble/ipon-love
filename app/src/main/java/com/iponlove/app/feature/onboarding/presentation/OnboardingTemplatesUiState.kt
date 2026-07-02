package com.iponlove.app.feature.onboarding.presentation

import com.iponlove.app.feature.onboarding.domain.model.StarterBundle

/** All four bundles pre-checked by default — à-la-carte, fully skippable (ADR-0024). */
data class OnboardingTemplatesUiState(
    val selectedBundles: Set<StarterBundle> = StarterBundle.entries.toSet(),
    val isSaving: Boolean = false,
)
