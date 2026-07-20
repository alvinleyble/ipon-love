package com.iponlove.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.core.ui.theme.LocalReducedMotion

/**
 * A fully-rounded progress bar with a heart riding the fill tip (v1.6.7 Item 8). The heart springs
 * along the fill on load unless reduced-motion is set, and is clamped so it never exits the track
 * (see [HeartProgressMath]).
 */
@Composable
fun HeartTippedProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    heartSize: Dp = 14.dp,
    fillColor: Color = LocalPlayfulColors.current.accent,
    trackColor: Color = LocalPlayfulColors.current.accent.copy(alpha = 0.16f),
    heartColor: Color = Color(0xFFFFFFFF),
) {
    val target = HeartProgressMath.fraction(progress)
    val reduced = LocalReducedMotion.current
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) spring(stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "heartProgress",
    )
    val fraction = if (reduced) target else animated

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(heartSize)) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val heartPx = with(density) { heartSize.toPx() }
        val offsetX = with(density) {
            HeartProgressMath.heartOffsetPx(fraction, trackWidthPx, heartPx).toDp()
        }

        // Track + fill, vertically centered within the heart's taller box.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(99.dp))
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(height)
                    .clip(RoundedCornerShape(99.dp))
                    .background(fillColor),
            )
        }
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = heartColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = offsetX)
                .size(heartSize),
        )
    }
}
