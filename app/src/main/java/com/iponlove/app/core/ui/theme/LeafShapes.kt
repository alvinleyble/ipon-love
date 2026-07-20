package com.iponlove.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The signature "leaf" squircle of Playful Pop (v1.6.7 Item 8): asymmetric corners where two
 * opposite corners are large and the other two small, giving a soft leaf/petal silhouette. Rows
 * and grids alternate the mirroring so adjacent items lean opposite ways.
 */
object LeafShapes {

    /** Large TL+BR, small TR+BL (the default lean). */
    fun leaf(big: Dp, small: Dp) = RoundedCornerShape(
        topStart = big, topEnd = small, bottomEnd = big, bottomStart = small,
    )

    /** Mirror: large TR+BL, small TL+BR. */
    fun leafMirrored(big: Dp, small: Dp) = RoundedCornerShape(
        topStart = small, topEnd = big, bottomEnd = small, bottomStart = big,
    )

    /**
     * The alternating shape for item [index] in a list/grid — even indices lean one way, odd the
     * other. Pure so it can be unit-tested (see `LeafShapesTest`).
     */
    fun leafFor(index: Int, big: Dp, small: Dp) =
        if (index % 2 == 0) leaf(big, small) else leafMirrored(big, small)

    // Named presets matching the handoff's radius scale (README §Shape language).
    val Hero = leaf(30.dp, 12.dp)
    val Card = leaf(22.dp, 9.dp)
    val Goal = leaf(28.dp, 11.dp)
    val Chip = leaf(16.dp, 6.dp)
    val SmallChip = leaf(14.dp, 5.dp)
    val Badge = leaf(10.dp, 4.dp)
    val IconSquircle = leaf(15.dp, 6.dp)
    val Fab = leaf(20.dp, 8.dp)
}
