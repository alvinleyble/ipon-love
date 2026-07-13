package com.iponlove.app.feature.onboarding.presentation

import com.iponlove.app.feature.settings.domain.model.CurrencySymbol

/** ₱ pre-selected — a tap-through keeps peso, the PH-market default (Item 27). */
data class OnboardingCurrencyUiState(
    val selected: CurrencySymbol = CurrencySymbol.DEFAULT,
)
