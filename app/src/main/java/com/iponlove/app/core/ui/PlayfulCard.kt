package com.iponlove.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors

/** The three Playful Pop surface treatments. */
enum class PlayfulSurface { Blush, DeepPlum, Glass }

/**
 * A Playful Pop card (v1.6.7 Item 8): a leaf-squircle [shape] filled per [surface], optionally
 * tilted by [tiltDegrees], with content padding. Colors come from the derived [LocalPlayfulColors]
 * so the card re-tints with the active palette and light/dark mode.
 */
@Composable
fun PlayfulCard(
    modifier: Modifier = Modifier,
    surface: PlayfulSurface = PlayfulSurface.Glass,
    shape: RoundedCornerShape,
    tiltDegrees: Float = 0f,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val bg = when (surface) {
        PlayfulSurface.Blush -> colors.blush
        PlayfulSurface.DeepPlum -> colors.deepPlum
        PlayfulSurface.Glass -> colors.glass
    }
    Box(
        modifier = modifier
            .then(if (tiltDegrees != 0f) Modifier.rotate(tiltDegrees) else Modifier)
            .clip(shape)
            .background(bg)
            .padding(contentPadding),
        content = content,
    )
}

/** The primary ink color to use on a given [PlayfulSurface]. */
@Composable
fun onPlayfulSurface(surface: PlayfulSurface): Color {
    val colors = LocalPlayfulColors.current
    return when (surface) {
        PlayfulSurface.Blush -> colors.onBlush
        PlayfulSurface.DeepPlum -> colors.onDeepPlum
        PlayfulSurface.Glass -> colors.textPrimary
    }
}
