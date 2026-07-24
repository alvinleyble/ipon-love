package com.iponlove.app.feature.settings.domain.model

data class ThemePreferences(
    val palette: ThemePalette = ThemePalette.ROSE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)
