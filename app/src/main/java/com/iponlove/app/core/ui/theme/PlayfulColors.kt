package com.iponlove.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The "Playful Pop" derived color layer (v1.6.7 Item 8).
 *
 * The Playful Pop redesign leans on a handful of surfaces that don't map 1:1 to a single M3
 * role — a *bright* blush card that pops on the screen, a *strong* deep-plum card, faint glass
 * rows, plus fixed money-semantic colors. Rather than hand-tuning ~24 raw constants per palette,
 * every Playful surface is **derived** from the active [ColorScheme] + `isDark` here (see
 * [playfulColorsFrom]). One formula adapts all six palettes × light/dark; the palettes stay the
 * single source of truth, so ADR-0014's live-preview/Apply flow keeps working untouched.
 *
 * The load-bearing rule: [blush] is always the *lighter/softer* accent card and [deepPlum] the
 * *stronger/darker* one — that relationship holds in both modes, so a Wifey-blush / Hubby-plum
 * pairing keeps its contrast whether the app is light or dark.
 */
data class PlayfulColors(
    // Accent (pink in the sample) — chips, FAB, progress fills, section markers, heart glyphs.
    val accent: Color,
    val onAccent: Color,
    // Bright "blush" card — the light pop card (Analysis hero, Wifey card, shared goal).
    val blush: Color,
    val onBlush: Color,
    // Secondary ink on a blush card (the "plum label", e.g. "Spent · February", "Income …").
    val onBlushSecondary: Color,
    // Strong "deep plum" card — Hubby card, goal-icon squircles, Shared badge.
    val deepPlum: Color,
    val onDeepPlum: Color,
    // Faint translucent "glass" fill for list rows / category cards.
    val glass: Color,
    // Hairline border for inactive/outlined chips & dividers.
    val hairline: Color,
    // Screen background gradient (top → bottom).
    val backgroundTop: Color,
    val backgroundBottom: Color,
    // Opaque bottom-nav surface + its inactive item tint.
    val navSurface: Color,
    val navInactive: Color,
    // Text ramp on the screen background.
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // Fixed, mode-aware money semantics (green == income on every palette).
    val semantic: SemanticColors,
)

/**
 * Money-semantic colors — fixed across all six palettes (green means income whether you're on
 * Rose or Sage), but mode-aware for contrast. The *OnBlush variants are for amounts rendered on a
 * blush/light card (which is light in both modes), so they never invert with app theme.
 */
data class SemanticColors(
    val negative: Color,
    val positive: Color,
    val income: Color,
    val negativeOnBlush: Color,
    val positiveOnBlush: Color,
)

private fun semanticColors(isDark: Boolean): SemanticColors =
    if (isDark) {
        SemanticColors(
            negative = Color(0xFFFF97A8),
            positive = Color(0xFF8FD8A8),
            income = Color(0xFF8FD8A8),
            negativeOnBlush = Color(0xFFC62828),
            positiveOnBlush = Color(0xFF2E7D4F),
        )
    } else {
        SemanticColors(
            negative = Color(0xFFC62828),
            positive = Color(0xFF2E7D4F),
            income = Color(0xFF2E7D4F),
            negativeOnBlush = Color(0xFFC62828),
            positiveOnBlush = Color(0xFF2E7D4F),
        )
    }

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

/**
 * Derive the Playful surfaces from a resolved [ColorScheme]. See the grilled mapping table in
 * `docs/build/v1.6.7.md` Item 8. Called once inside `IponTheme` and provided via
 * [LocalPlayfulColors].
 */
fun playfulColorsFrom(scheme: ColorScheme, isDark: Boolean): PlayfulColors {
    val blush = if (isDark) lerp(scheme.primaryContainer, White, 0.85f) else scheme.primaryContainer
    val onBlush = if (isDark) scheme.onPrimary else scheme.onPrimaryContainer
    val deepPlum = if (isDark) scheme.primaryContainer else scheme.primary
    val onDeepPlum = if (isDark) scheme.onPrimaryContainer else scheme.onPrimary
    // The "plum label" ink on a blush card == the deep-plum accent color used as text.
    val onBlushSecondary = if (isDark) scheme.primaryContainer else scheme.primary

    val backgroundTop = if (isDark) {
        lerp(scheme.background, scheme.primary, 0.12f)
    } else {
        lerp(scheme.background, scheme.primaryContainer, 0.10f)
    }
    val navSurface = if (isDark) {
        lerp(lerp(scheme.background, scheme.primary, 0.10f), Black, 0.10f)
    } else {
        lerp(scheme.surface, scheme.primaryContainer, 0.30f)
    }

    return PlayfulColors(
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        blush = blush,
        onBlush = onBlush,
        onBlushSecondary = onBlushSecondary,
        deepPlum = deepPlum,
        onDeepPlum = onDeepPlum,
        glass = scheme.primary.copy(alpha = if (isDark) 0.08f else 0.06f),
        hairline = scheme.onBackground.copy(alpha = 0.22f),
        backgroundTop = backgroundTop,
        backgroundBottom = scheme.background,
        navSurface = navSurface,
        navInactive = lerp(scheme.onSurfaceVariant, scheme.primary, 0.25f),
        textPrimary = scheme.onBackground,
        textSecondary = scheme.onBackground.copy(alpha = 0.62f),
        textTertiary = scheme.onBackground.copy(alpha = 0.45f),
        semantic = semanticColors(isDark),
    )
}

/**
 * The active [PlayfulColors]. Provided by `IponTheme`; the default is a throwaway light-Rose-ish
 * derivation so a preview/composable read outside the theme doesn't crash (it's always overridden
 * in-app).
 */
val LocalPlayfulColors = staticCompositionLocalOf {
    playfulColorsFrom(androidx.compose.material3.lightColorScheme(), isDark = false)
}
