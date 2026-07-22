package com.iponlove.app.feature.onboarding.presentation

import com.iponlove.app.core.ui.AvatarMotif

/** Heart pre-selected (the stored default — [AvatarMotif.Default]); a tap-through keeps it (Item 42). */
data class OnboardingMotifUiState(
    val selected: String = AvatarMotif.Default.key,
    val accentColor: String? = null,
)
