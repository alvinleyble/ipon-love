package com.iponlove.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.iponlove.app.feature.settings.domain.model.ThemePalette

fun paletteColorScheme(palette: ThemePalette, isDark: Boolean): ColorScheme =
    if (isDark) darkScheme(palette) else lightScheme(palette)

private fun lightScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.ROSE -> lightColorScheme(
        primary = RoseLight, onPrimary = RoseLightOnPrimary,
        primaryContainer = RoseLightContainer, onPrimaryContainer = RoseLightOnContainer,
        background = RoseLightBackground,
        surface = RoseLightSurface, surfaceVariant = RoseLightSurfaceVariant,
    )
    ThemePalette.MAUVE -> lightColorScheme(
        primary = MauveLight, onPrimary = MauveLightOnPrimary,
        primaryContainer = MauveLightContainer, onPrimaryContainer = MauveLightOnContainer,
        background = MauveLightBackground,
        surface = MauveLightSurface, surfaceVariant = MauveLightSurfaceVariant,
    )
    ThemePalette.LAVENDER -> lightColorScheme(
        primary = LavenderLight, onPrimary = LavenderLightOnPrimary,
        primaryContainer = LavenderLightContainer, onPrimaryContainer = LavenderLightOnContainer,
        background = LavenderLightBackground,
        surface = LavenderLightSurface, surfaceVariant = LavenderLightSurfaceVariant,
    )
    ThemePalette.PEACH -> lightColorScheme(
        primary = PeachLight, onPrimary = PeachLightOnPrimary,
        primaryContainer = PeachLightContainer, onPrimaryContainer = PeachLightOnContainer,
        background = PeachLightBackground,
        surface = PeachLightSurface, surfaceVariant = PeachLightSurfaceVariant,
    )
    ThemePalette.SAGE -> lightColorScheme(
        primary = SageLight, onPrimary = SageLightOnPrimary,
        primaryContainer = SageLightContainer, onPrimaryContainer = SageLightOnContainer,
        background = SageLightBackground,
        surface = SageLightSurface, surfaceVariant = SageLightSurfaceVariant,
    )
    ThemePalette.MOCHA -> lightColorScheme(
        primary = MochaLight, onPrimary = MochaLightOnPrimary,
        primaryContainer = MochaLightContainer, onPrimaryContainer = MochaLightOnContainer,
        background = MochaLightBackground,
        surface = MochaLightSurface, surfaceVariant = MochaLightSurfaceVariant,
    )
}

private fun darkScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.ROSE -> darkColorScheme(
        primary = RoseDark, onPrimary = RoseDarkOnPrimary,
        primaryContainer = RoseDarkContainer, onPrimaryContainer = RoseDarkOnContainer,
        background = RoseDarkBackground,
        surface = RoseDarkSurface, surfaceVariant = RoseDarkSurfaceVariant,
    )
    ThemePalette.MAUVE -> darkColorScheme(
        primary = MauveDark, onPrimary = MauveDarkOnPrimary,
        primaryContainer = MauveDarkContainer, onPrimaryContainer = MauveDarkOnContainer,
        background = MauveDarkBackground,
        surface = MauveDarkSurface, surfaceVariant = MauveDarkSurfaceVariant,
    )
    ThemePalette.LAVENDER -> darkColorScheme(
        primary = LavenderDark, onPrimary = LavenderDarkOnPrimary,
        primaryContainer = LavenderDarkContainer, onPrimaryContainer = LavenderDarkOnContainer,
        background = LavenderDarkBackground,
        surface = LavenderDarkSurface, surfaceVariant = LavenderDarkSurfaceVariant,
    )
    ThemePalette.PEACH -> darkColorScheme(
        primary = PeachDark, onPrimary = PeachDarkOnPrimary,
        primaryContainer = PeachDarkContainer, onPrimaryContainer = PeachDarkOnContainer,
        background = PeachDarkBackground,
        surface = PeachDarkSurface, surfaceVariant = PeachDarkSurfaceVariant,
    )
    ThemePalette.SAGE -> darkColorScheme(
        primary = SageDark, onPrimary = SageDarkOnPrimary,
        primaryContainer = SageDarkContainer, onPrimaryContainer = SageDarkOnContainer,
        background = SageDarkBackground,
        surface = SageDarkSurface, surfaceVariant = SageDarkSurfaceVariant,
    )
    ThemePalette.MOCHA -> darkColorScheme(
        primary = MochaDark, onPrimary = MochaDarkOnPrimary,
        primaryContainer = MochaDarkContainer, onPrimaryContainer = MochaDarkOnContainer,
        background = MochaDarkBackground,
        surface = MochaDarkSurface, surfaceVariant = MochaDarkSurfaceVariant,
    )
}
