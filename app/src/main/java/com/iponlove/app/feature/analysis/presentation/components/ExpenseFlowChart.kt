package com.iponlove.app.feature.analysis.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.feature.analysis.presentation.ExpenseFlowUi

/**
 * Cumulative expense line chart for any Analysis range (Item 3A, generalized 2026-07-09).
 *
 * Draws the cumulative-spend polyline, a dotted "today" marker on the current bucket, and the
 * pre-computed x-axis labels — all on a single Canvas with no third-party chart library
 * (CLAUDE.md). The buckets are days (short ranges) or months (6M/12M/ALL); the chart is agnostic
 * about which. The budget ceiling line was dropped (grill 2026-07-09).
 */
@Composable
fun ExpenseFlowChart(
    flow: ExpenseFlowUi,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.5f))

    val bucketCount = flow.cumulativeByBucket.size

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val labelAreaH = 20.dp.toPx()
        val chartH = size.height - labelAreaH
        val w = size.width

        val maxValue = maxOf(flow.cumulativeByBucket.maxOrNull() ?: 0f, 1f)

        fun xPos(idx: Int): Float =
            if (bucketCount <= 1) w / 2f
            else idx.toFloat() / (bucketCount - 1).toFloat() * w

        fun yPos(value: Float): Float = chartH * (1f - value / maxValue)

        // --- today marker (dotted vertical) ---
        flow.currentBucketIndex?.let { idx ->
            val todayX = xPos(idx)
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.18f),
                start = Offset(todayX, 0f),
                end = Offset(todayX, chartH),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }

        // --- spending polyline ---
        if (flow.cumulativeByBucket.isNotEmpty()) {
            val path = Path()
            flow.cumulativeByBucket.forEachIndexed { idx, value ->
                val x = xPos(idx)
                val y = yPos(value)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // --- axis labels (pre-computed in the ViewModel) ---
        flow.axisLabels.forEach { label ->
            val x = xPos(label.bucketIndex)
            val measured = textMeasurer.measure(label.text, labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (x - measured.size.width / 2f).coerceIn(0f, w - measured.size.width),
                    y = chartH + 4.dp.toPx(),
                ),
            )
        }
    }
}
