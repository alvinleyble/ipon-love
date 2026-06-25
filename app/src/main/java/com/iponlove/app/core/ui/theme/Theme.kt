package com.iponlove.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.iponlove.app.feature.settings.domain.model.ThemePreferences

@Composable
fun IponTheme(
    themePreferences: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = paletteColorScheme(themePreferences.palette, themePreferences.isDark),
        typography = IponTypography,
        content = content,
    )
}
