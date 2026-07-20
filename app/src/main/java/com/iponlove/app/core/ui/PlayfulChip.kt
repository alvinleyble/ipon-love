package com.iponlove.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.core.ui.theme.LocalReducedMotion

/**
 * A Playful Pop leaf-pill chip/tab (v1.6.7 Item 8): active = accent fill with on-accent ink;
 * inactive = a hairline outline with secondary text. Corner radius + colors animate on select
 * (snap under reduced-motion). Touch target is kept ≥44dp tall regardless of the compact visual.
 */
@Composable
fun PlayfulChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bigCorner: Dp = 16.dp,
    smallCorner: Dp = 6.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPlayfulColors.current
    val reduced = LocalReducedMotion.current
    val dur = if (reduced) 0 else 200

    // Active leans one way, inactive the mirror — a subtle radius morph on select.
    val big by animateDpAsState(if (selected) bigCorner else smallCorner, tween(dur), label = "chipBig")
    val small by animateDpAsState(if (selected) smallCorner else bigCorner, tween(dur), label = "chipSmall")
    val bg by animateColorAsState(if (selected) colors.accent else Color.Transparent, tween(dur), label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.onAccent else colors.textSecondary, tween(dur), label = "chipFg")

    val shape = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .then(if (!selected) Modifier.border(1.5.dp, colors.hairline, shape) else Modifier)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
            )
            trailing?.invoke()
        }
    }
}
