package com.iponlove.app.feature.widget.presentation

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import com.iponlove.app.core.ui.theme.paletteColorScheme
import com.iponlove.app.core.ui.theme.playfulColorsFrom
import com.iponlove.app.feature.settings.domain.model.ThemePalette

/**
 * Brand colors for the home-screen widgets: the app's Rose palette (light + dark, following the
 * system night mode) instead of `GlanceTheme`'s default — which is Material You dynamic color on
 * Android 12+, i.e. a wallpaper-derived palette that painted the balance widget purple next to the
 * pink quick-add widget (Alvin, 2026-07-14). Static Rose, not the user's chosen in-app palette;
 * following the Personalize palette is a separate item if ever wanted.
 */
val IponWidgetColors = ColorProviders(
    light = paletteColorScheme(ThemePalette.ROSE, isDark = false),
    dark = paletteColorScheme(ThemePalette.ROSE, isDark = true),
)

/**
 * The "Playful Pop" (v1.6.7 Item 8, Slice 6j) surfaces, approximated for Glance/RemoteViews.
 *
 * Glance can't read the app's `LocalPlayfulColors` composition-local (it's not in the Compose tree),
 * so instead of hand-picking hexes we run the same [playfulColorsFrom] formula over the static Rose
 * scheme for both modes, then expose each surface as a day/night [ColorProvider] that follows the
 * system night mode — the same light/dark split [IponWidgetColors] uses. Path-based leaf squircles
 * and gradients aren't renderable in RemoteViews, so the widgets approximate the language with a
 * solid palette-tinted card + `GlanceModifier.cornerRadius` + the existing heart drawable.
 */
object PlayfulWidgetColors {
    private val light = playfulColorsFrom(paletteColorScheme(ThemePalette.ROSE, isDark = false), isDark = false)
    private val dark = playfulColorsFrom(paletteColorScheme(ThemePalette.ROSE, isDark = true), isDark = true)

    private fun dayNight(day: Color, night: Color) = ColorProvider(day = day, night = night)

    /** Soft palette-tinted surface — the balance widget's card (the app's opaque nav-surface tint). */
    val card = dayNight(light.navSurface, dark.navSurface)

    /** Strong accent fill — the quick-add widget's card (mirrors the in-app accent-squircle FAB). */
    val accentFill = dayNight(light.accent, dark.accent)

    /** The accent (rose) itself — heart glyphs / accented figures on the soft card. */
    val accent = dayNight(light.accent, dark.accent)

    /** Primary/secondary ink on the soft card. */
    val onCardPrimary = dayNight(light.textPrimary, dark.textPrimary)
    val onCardSecondary = dayNight(light.textSecondary, dark.textSecondary)

    /** Ink on the accent fill (quick-add). */
    val onAccent = dayNight(light.onAccent, dark.onAccent)
}
