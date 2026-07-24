package com.iponlove.app.core.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iponlove.app.feature.settings.domain.model.ThemeMode
import com.iponlove.app.feature.settings.domain.model.ThemePreferences

// Global M3 shapes stay conservative (Playful Pop's asymmetric leaf-squircles are applied via
// bespoke components — see LeafShapes — so dialogs/sheets/text-fields don't get warped corners).
private val IponShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Whether the OS is in a reduced-motion state (animations globally disabled). Playful Pop keeps
 * its static tilts always, but *animations* (chip morphs, progress-heart springs, FAB press) snap
 * instead when this is true. Read once in [IponTheme].
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun IponTheme(
    themePreferences: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit,
) {
    val isDark = when (themePreferences.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = paletteColorScheme(themePreferences.palette, isDark)
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
    val playfulColors = remember(colorScheme, isDark) {
        playfulColorsFrom(colorScheme, isDark)
    }
    CompositionLocalProvider(
        LocalPlayfulColors provides playfulColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IponTypography,
            shapes = IponShapes,
            content = content,
        )
    }
}
