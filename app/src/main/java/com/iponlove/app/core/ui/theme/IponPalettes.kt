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
    )
    ThemePalette.MAUVE -> lightColorScheme(
        primary = MauveLight, onPrimary = MauveLightOnPrimary,
        primaryContainer = MauveLightContainer, onPrimaryContainer = MauveLightOnContainer,
    )
    ThemePalette.LAVENDER -> lightColorScheme(
        primary = LavenderLight, onPrimary = LavenderLightOnPrimary,
        primaryContainer = LavenderLightContainer, onPrimaryContainer = LavenderLightOnContainer,
    )
    ThemePalette.PEACH -> lightColorScheme(
        primary = PeachLight, onPrimary = PeachLightOnPrimary,
        primaryContainer = PeachLightContainer, onPrimaryContainer = PeachLightOnContainer,
    )
    ThemePalette.SAGE -> lightColorScheme(
        primary = SageLight, onPrimary = SageLightOnPrimary,
        primaryContainer = SageLightContainer, onPrimaryContainer = SageLightOnContainer,
    )
    ThemePalette.MOCHA -> lightColorScheme(
        primary = MochaLight, onPrimary = MochaLightOnPrimary,
        primaryContainer = MochaLightContainer, onPrimaryContainer = MochaLightOnContainer,
    )
}

private fun darkScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.ROSE -> darkColorScheme(
        primary = RoseDark, onPrimary = RoseDarkOnPrimary,
        primaryContainer = RoseDarkContainer, onPrimaryContainer = RoseDarkOnContainer,
    )
    ThemePalette.MAUVE -> darkColorScheme(
        primary = MauveDark, onPrimary = MauveDarkOnPrimary,
        primaryContainer = MauveDarkContainer, onPrimaryContainer = MauveDarkOnContainer,
    )
    ThemePalette.LAVENDER -> darkColorScheme(
        primary = LavenderDark, onPrimary = LavenderDarkOnPrimary,
        primaryContainer = LavenderDarkContainer, onPrimaryContainer = LavenderDarkOnContainer,
    )
    ThemePalette.PEACH -> darkColorScheme(
        primary = PeachDark, onPrimary = PeachDarkOnPrimary,
        primaryContainer = PeachDarkContainer, onPrimaryContainer = PeachDarkOnContainer,
    )
    ThemePalette.SAGE -> darkColorScheme(
        primary = SageDark, onPrimary = SageDarkOnPrimary,
        primaryContainer = SageDarkContainer, onPrimaryContainer = SageDarkOnContainer,
    )
    ThemePalette.MOCHA -> darkColorScheme(
        primary = MochaDark, onPrimary = MochaDarkOnPrimary,
        primaryContainer = MochaDarkContainer, onPrimaryContainer = MochaDarkOnContainer,
    )
}
