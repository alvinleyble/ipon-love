package com.iponlove.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.iponlove.app.feature.settings.domain.model.ThemePalette

fun paletteColorScheme(palette: ThemePalette, isDark: Boolean): ColorScheme =
    if (isDark) darkScheme(palette) else lightScheme(palette)

private data class SurfaceRamp(
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
)

// Light: ramp from near-white → surfaceVariant using M3 tonal steps.
private fun lightSurfaceRamp(surfaceVariant: Color): SurfaceRamp {
    val white = Color(0xFFFFFFFF)
    return SurfaceRamp(
        lowest    = lerp(white, surfaceVariant, 0.05f),
        low       = lerp(white, surfaceVariant, 0.15f),
        container = lerp(white, surfaceVariant, 0.28f),
        high      = lerp(white, surfaceVariant, 0.42f),
        highest   = lerp(white, surfaceVariant, 0.58f),
    )
}

// Dark: ramp from tinted-charcoal (slightly deeper than background) → surfaceVariant.
private fun darkSurfaceRamp(background: Color, surfaceVariant: Color): SurfaceRamp {
    val deeper = lerp(background, Color.Black, 0.30f)
    return SurfaceRamp(
        lowest    = deeper,
        low       = lerp(background, surfaceVariant, 0.12f),
        container = lerp(background, surfaceVariant, 0.28f),
        high      = lerp(background, surfaceVariant, 0.45f),
        highest   = lerp(background, surfaceVariant, 0.62f),
    )
}

private fun lightScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.ROSE -> {
        val r = lightSurfaceRamp(RoseLightSurfaceVariant)
        lightColorScheme(
            primary = RoseLight, onPrimary = RoseLightOnPrimary,
            primaryContainer = RoseLightContainer, onPrimaryContainer = RoseLightOnContainer,
            background = RoseLightBackground,
            surface = RoseLightSurface, surfaceVariant = RoseLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.MAUVE -> {
        val r = lightSurfaceRamp(MauveLightSurfaceVariant)
        lightColorScheme(
            primary = MauveLight, onPrimary = MauveLightOnPrimary,
            primaryContainer = MauveLightContainer, onPrimaryContainer = MauveLightOnContainer,
            background = MauveLightBackground,
            surface = MauveLightSurface, surfaceVariant = MauveLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.LAVENDER -> {
        val r = lightSurfaceRamp(LavenderLightSurfaceVariant)
        lightColorScheme(
            primary = LavenderLight, onPrimary = LavenderLightOnPrimary,
            primaryContainer = LavenderLightContainer, onPrimaryContainer = LavenderLightOnContainer,
            background = LavenderLightBackground,
            surface = LavenderLightSurface, surfaceVariant = LavenderLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.PEACH -> {
        val r = lightSurfaceRamp(PeachLightSurfaceVariant)
        lightColorScheme(
            primary = PeachLight, onPrimary = PeachLightOnPrimary,
            primaryContainer = PeachLightContainer, onPrimaryContainer = PeachLightOnContainer,
            background = PeachLightBackground,
            surface = PeachLightSurface, surfaceVariant = PeachLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.SAGE -> {
        val r = lightSurfaceRamp(SageLightSurfaceVariant)
        lightColorScheme(
            primary = SageLight, onPrimary = SageLightOnPrimary,
            primaryContainer = SageLightContainer, onPrimaryContainer = SageLightOnContainer,
            background = SageLightBackground,
            surface = SageLightSurface, surfaceVariant = SageLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.MOCHA -> {
        val r = lightSurfaceRamp(MochaLightSurfaceVariant)
        lightColorScheme(
            primary = MochaLight, onPrimary = MochaLightOnPrimary,
            primaryContainer = MochaLightContainer, onPrimaryContainer = MochaLightOnContainer,
            background = MochaLightBackground,
            surface = MochaLightSurface, surfaceVariant = MochaLightSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
}

private fun darkScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.ROSE -> {
        val r = darkSurfaceRamp(RoseDarkBackground, RoseDarkSurfaceVariant)
        darkColorScheme(
            primary = RoseDark, onPrimary = RoseDarkOnPrimary,
            primaryContainer = RoseDarkContainer, onPrimaryContainer = RoseDarkOnContainer,
            background = RoseDarkBackground,
            surface = RoseDarkSurface, surfaceVariant = RoseDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.MAUVE -> {
        val r = darkSurfaceRamp(MauveDarkBackground, MauveDarkSurfaceVariant)
        darkColorScheme(
            primary = MauveDark, onPrimary = MauveDarkOnPrimary,
            primaryContainer = MauveDarkContainer, onPrimaryContainer = MauveDarkOnContainer,
            background = MauveDarkBackground,
            surface = MauveDarkSurface, surfaceVariant = MauveDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.LAVENDER -> {
        val r = darkSurfaceRamp(LavenderDarkBackground, LavenderDarkSurfaceVariant)
        darkColorScheme(
            primary = LavenderDark, onPrimary = LavenderDarkOnPrimary,
            primaryContainer = LavenderDarkContainer, onPrimaryContainer = LavenderDarkOnContainer,
            background = LavenderDarkBackground,
            surface = LavenderDarkSurface, surfaceVariant = LavenderDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.PEACH -> {
        val r = darkSurfaceRamp(PeachDarkBackground, PeachDarkSurfaceVariant)
        darkColorScheme(
            primary = PeachDark, onPrimary = PeachDarkOnPrimary,
            primaryContainer = PeachDarkContainer, onPrimaryContainer = PeachDarkOnContainer,
            background = PeachDarkBackground,
            surface = PeachDarkSurface, surfaceVariant = PeachDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.SAGE -> {
        val r = darkSurfaceRamp(SageDarkBackground, SageDarkSurfaceVariant)
        darkColorScheme(
            primary = SageDark, onPrimary = SageDarkOnPrimary,
            primaryContainer = SageDarkContainer, onPrimaryContainer = SageDarkOnContainer,
            background = SageDarkBackground,
            surface = SageDarkSurface, surfaceVariant = SageDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
    ThemePalette.MOCHA -> {
        val r = darkSurfaceRamp(MochaDarkBackground, MochaDarkSurfaceVariant)
        darkColorScheme(
            primary = MochaDark, onPrimary = MochaDarkOnPrimary,
            primaryContainer = MochaDarkContainer, onPrimaryContainer = MochaDarkOnContainer,
            background = MochaDarkBackground,
            surface = MochaDarkSurface, surfaceVariant = MochaDarkSurfaceVariant,
            surfaceContainerLowest = r.lowest,
            surfaceContainerLow = r.low,
            surfaceContainer = r.container,
            surfaceContainerHigh = r.high,
            surfaceContainerHighest = r.highest,
        )
    }
}
