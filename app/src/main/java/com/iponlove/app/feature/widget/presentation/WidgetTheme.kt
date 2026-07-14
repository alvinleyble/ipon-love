package com.iponlove.app.feature.widget.presentation

import androidx.glance.material3.ColorProviders
import com.iponlove.app.core.ui.theme.paletteColorScheme
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
