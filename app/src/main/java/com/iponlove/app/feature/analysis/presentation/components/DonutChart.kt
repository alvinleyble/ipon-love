package com.iponlove.app.feature.analysis.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One ring segment: its share of the whole [0f..1f] and the color to paint it. */
data class DonutSlice(val fraction: Float, val color: Color)

/**
 * A simple donut/ring chart drawn with [Canvas] (no chart dependency — CLAUDE.md keeps the
 * stack to Compose + Material 3). Slices are painted clockwise from the top. [center]
 * renders inside the hole, e.g. the period's total.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 200.dp,
    thickness: Dp = 34.dp,
    trackColor: Color = Color.LightGray.copy(alpha = 0.3f),
    center: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Background track so a partial ring still reads as a full circle.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            // A tiny gap between slices keeps adjacent segments visually distinct.
            val gap = if (slices.size > 1) 2f else 0f
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                }
                startAngle += sweep
            }
        }
        center()
    }
}
