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
 * Cumulative expense line chart for one calendar month (Slice G).
 *
 * Draws: actual cumulative spending polyline, optional budget ceiling (dashed), today
 * marker (dotted vertical), and day-of-month labels at the bottom — all on a single
 * Canvas with no third-party chart library (CLAUDE.md).
 *
 * The spending line turns [errorColor] when today's cumulative exceeds [ExpenseFlowUi.budgetTotal].
 */
@Composable
fun ExpenseFlowChart(
    flow: ExpenseFlowUi,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.5f))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        val labelAreaH = 20.dp.toPx()
        val chartH = size.height - labelAreaH
        val w = size.width

        val maxValue = maxOf(
            flow.budgetTotal,
            flow.cumulativeByDay.maxOrNull() ?: 0f,
            1f,
        )

        fun xPos(dayIdx: Int): Float =
            if (flow.daysInMonth <= 1) w / 2f
            else dayIdx.toFloat() / (flow.daysInMonth - 1).toFloat() * w

        fun yPos(value: Float): Float = chartH * (1f - value / maxValue)

        // --- budget ceiling (dashed horizontal) ---
        if (flow.budgetTotal > 0f) {
            val budgetY = yPos(flow.budgetTotal)
            drawLine(
                color = secondaryColor.copy(alpha = 0.6f),
                start = Offset(0f, budgetY),
                end = Offset(w, budgetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
            )
        }

        // --- today marker (dotted vertical) ---
        flow.todayDayOfMonth?.let { todayDay ->
            val todayX = xPos(todayDay - 1)
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.18f),
                start = Offset(todayX, 0f),
                end = Offset(todayX, chartH),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }

        // --- spending polyline ---
        if (flow.cumulativeByDay.isNotEmpty()) {
            val todayIdx = (flow.todayDayOfMonth?.minus(1)) ?: (flow.daysInMonth - 1)
            val currentSpend = flow.cumulativeByDay.getOrElse(todayIdx) { 0f }
            val lineColor =
                if (flow.budgetTotal > 0f && currentSpend > flow.budgetTotal) errorColor
                else primaryColor

            val path = Path()
            flow.cumulativeByDay.forEachIndexed { idx, value ->
                val x = xPos(idx)
                val y = yPos(value)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // --- day labels ---
        val labelDays = buildList {
            add(1)
            listOf(5, 10, 15, 20, 25).forEach { if (it < flow.daysInMonth) add(it) }
            if (flow.daysInMonth !in listOf(1, 5, 10, 15, 20, 25)) add(flow.daysInMonth)
        }
        labelDays.forEach { day ->
            val x = xPos(day - 1)
            val measured = textMeasurer.measure("$day", labelStyle)
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
