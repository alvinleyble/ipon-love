package com.iponlove.app.feature.settings.presentation

import com.iponlove.app.feature.settings.domain.model.ThemeMode
import com.iponlove.app.feature.settings.domain.model.ThemePalette

/**
 * Appearance sub-screen (v1.6.5 Item 34, split out of Personalize): the palette + light/dark/system
 * live-preview draft. ADR-0014 semantics are unchanged — `draft*` is local ViewModel state that
 * previews on tap but only persists on Apply.
 */
data class AppearanceUiState(
    val draftPalette: ThemePalette = ThemePalette.ROSE,
    val draftMode: ThemeMode = ThemeMode.SYSTEM,
    val saved: Boolean = false,
    /**
     * Whether the Premium palettes are locked right now (S9 allowlist gate): enforcement ON and
     * no premium. Drives the greyed swatch + lock badge; a locked swatch routes to the paywall
     * instead of selecting. False while dormant, so nothing changes until the enforcement flip.
     */
    val paletteLocked: Boolean = false,
)
