package com.iponlove.app.feature.settings.presentation

import com.iponlove.app.feature.settings.domain.model.ThemePalette

data class PersonalizeUiState(
    val draftPalette: ThemePalette = ThemePalette.ROSE,
    val draftIsDark: Boolean = false,
    val saved: Boolean = false,
    /**
     * Whether to show the Premium Settings row (paywall S5 / Item 12). Driven by the remote
     * enforcement kill-switch: the entry stays **hidden while dormant** (enforcement OFF, the
     * ship default) and appears only once enforcement is flipped ON — which is Alvin's post-beta
     * "explicit go" (§10.7). No separate flag needed; enforcement *is* the go.
     */
    val showPremiumEntry: Boolean = false,
    /** Labels the Premium row "active" vs "upgrade" — the user's own cached entitlement. */
    val isPremium: Boolean = false,
    /**
     * Whether the Premium palettes are locked right now (S9 allowlist gate): enforcement ON and
     * no premium. Drives the greyed swatch + lock badge; a locked swatch routes to the paywall
     * instead of selecting. False while dormant, so nothing changes until the enforcement flip.
     */
    val paletteLocked: Boolean = false,
    /** Global Privacy mode (Item 15) — masks money app-wide when on. Persists instantly on
     *  toggle, unlike the palette/mode draft above (no Save/Apply gate). */
    val privacyModeEnabled: Boolean = false,
)
