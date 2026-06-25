package com.iponlove.app.feature.settings.presentation

import com.iponlove.app.feature.settings.domain.model.ThemePalette

data class PersonalizeUiState(
    val draftPalette: ThemePalette = ThemePalette.ROSE,
    val draftIsDark: Boolean = false,
    val saved: Boolean = false,
)
