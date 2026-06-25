package com.iponlove.app.feature.analysis.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.feature.analysis.presentation.CalendarNetUi
import kotlin.math.ceil
import kotlin.math.abs

private val IncomeGreen = Color(0xFF2E7D32)

/**
 * Monthly calendar grid showing daily net (income − expense) per day cell (Slice H).
 *
 * Layout: Mon–Sun header row + one cell per day of the month. Empty cells are drawn before
 * day 1 to align with the correct weekday column. Today's cell gets a [primaryContainer]
 * background. Net positive → green; net negative → error red; zero/no activity → muted.
 *
 * Pure Canvas — no third-party chart library (CLAUDE.md).
 */
@Composable
fun DailyNetCalendarChart(
    calendarNet: CalendarNetUi,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val totalCells = calendarNet.firstWeekdayOffset + calendarNet.daysInMonth
    val rowCount = ceil(totalCells / 7.0).toInt()

    // Fixed cell height + header row height
    val cellHeightDp = 52
    val headerHeightDp = 22
    val totalHeightDp = headerHeightDp + rowCount * cellHeightDp

    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp),
    ) {
        val cellW = size.width / 7f
        val headerH = headerHeightDp.dp.toPx()
        val cellH = cellHeightDp.dp.toPx()

        val headerStyle = TextStyle(
            fontSize = 10.sp,
            color = onSurfaceColor.copy(alpha = 0.5f),
        )
        val dayNumStyle = TextStyle(
            fontSize = 9.sp,
            color = onSurfaceColor.copy(alpha = 0.55f),
        )
        val netStyleBase = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )

        // --- header row (Mon … Sun) ---
        dayHeaders.forEachIndexed { col, label ->
            val measured = textMeasurer.measure(label, headerStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = col * cellW + (cellW - measured.size.width) / 2f,
                    y = (headerH - measured.size.height) / 2f,
                ),
            )
        }

        // --- day cells ---
        calendarNet.days.forEach { day ->
            val gridIndex = calendarNet.firstWeekdayOffset + day.dayOfMonth - 1
            val col = gridIndex % 7
            val row = gridIndex / 7
            val cellX = col * cellW
            val cellY = headerH + row * cellH

            // today highlight background
            if (day.isToday) {
                drawRoundRect(
                    color = primaryContainerColor,
                    topLeft = Offset(cellX + 2.dp.toPx(), cellY + 2.dp.toPx()),
                    size = Size(cellW - 4.dp.toPx(), cellH - 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }

            // day number (top-left of cell)
            val dayNumMeasured = textMeasurer.measure("${day.dayOfMonth}", dayNumStyle)
            drawText(
                textLayoutResult = dayNumMeasured,
                topLeft = Offset(
                    x = cellX + 4.dp.toPx(),
                    y = cellY + 4.dp.toPx(),
                ),
            )

            // net amount (centred vertically, filling cell width)
            if (day.netFloat != 0f) {
                val netColor: Color = when {
                    day.isToday -> onPrimaryContainerColor
                    day.netFloat > 0f -> IncomeGreen
                    else -> errorColor
                }
                val netLabel = compactAmount(day.netFloat)
                val netMeasured = textMeasurer.measure(netLabel, netStyleBase.copy(color = netColor))
                drawText(
                    textLayoutResult = netMeasured,
                    topLeft = Offset(
                        x = (cellX + (cellW - netMeasured.size.width) / 2f)
                            .coerceIn(cellX, cellX + cellW - netMeasured.size.width),
                        y = cellY + cellH / 2f - netMeasured.size.height / 2f + 4.dp.toPx(),
                    ),
                )
            }
        }
    }
}

/** Formats a PHP net amount compactly for a small calendar cell (no ₱ prefix on negatives). */
private fun compactAmount(value: Float): String {
    val sign = if (value < 0f) "-" else "+"
    val absVal = abs(value)
    return when {
        absVal >= 1_000_000f -> "$sign₱${String.format("%.1f", absVal / 1_000_000f)}M"
        absVal >= 1_000f -> "$sign₱${String.format("%.1f", absVal / 1_000f)}k"
        else -> "$sign₱${absVal.toInt()}"
    }
}
