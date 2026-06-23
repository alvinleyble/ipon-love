package com.iponlove.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Plum40,
    secondary = Sand40,
    tertiary = Rose40,
)

private val DarkColors = darkColorScheme(
    primary = Plum80,
    secondary = Sand80,
    tertiary = Rose80,
)

/**
 * Root theme wrapper. Per ARCHITECTURE.md §8 this will later resolve the active
 * theme from DataStore via a CompositionLocal; for now it follows the system.
 */
@Composable
fun IponTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = IponTypography,
        content = content,
    )
}
