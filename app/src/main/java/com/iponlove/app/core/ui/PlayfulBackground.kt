package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/**
 * The Playful Pop screen backdrop (v1.6.7 Item 8) — a vertical gradient derived from the active
 * palette. In dark mode it's a plum-lifted top fading to the base background; in light mode a
 * barely-there tint. Apply to a full-screen container behind a redesigned screen's content.
 */
@Composable
fun Modifier.playfulBackground(): Modifier {
    val colors = LocalPlayfulColors.current
    return this.background(
        Brush.verticalGradient(
            0f to colors.backgroundTop,
            0.6f to colors.backgroundBottom,
            1f to colors.backgroundBottom,
        ),
    )
}
