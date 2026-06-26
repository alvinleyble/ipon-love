package com.iponlove.app.feature.analysis.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.feature.analysis.presentation.CalendarNetUi
import kotlin.math.ceil

private val IncomeGreen = Color(0xFF2E7D32)

/**
 * Monthly calendar grid showing daily income and expense in two rows per cell (V1.2 item 2).
 *
 * Layout: Sun–Sat header row + one cell per day. Empty cells before day 1 align the grid.
 * Each cell shows "+₱X" (green) and/or "-₱X" (red). Zero-row rule: only the nonzero row is
 * shown, vertically centered. Today's cell gets a [primaryContainer] background; a tapped
 * [selectedDay] cell gets a [secondaryContainer] background (today takes priority when both).
 * Cell height is ~64dp.
 *
 * Pure Canvas — no third-party chart library (CLAUDE.md).
 */
@Composable
fun DailyNetCalendarChart(
    calendarNet: CalendarNetUi,
    selectedDay: Int? = null,
    onDayClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val totalCells = calendarNet.firstWeekdayOffset + calendarNet.daysInMonth
    val rowCount = ceil(totalCells / 7.0).toInt()

    val cellHeightDp = 64
    val headerHeightDp = 22
    val totalHeightDp = headerHeightDp + rowCount * cellHeightDp

    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    // Capture pixel sizes in composable scope so they're accessible inside pointerInput.
    val density = LocalDensity.current
    val headerHeightPx = with(density) { headerHeightDp.dp.toPx() }
    val cellHeightPx = with(density) { cellHeightDp.dp.toPx() }

    val tapModifier = if (onDayClick != null) {
        Modifier.pointerInput(calendarNet) {
            detectTapGestures { offset ->
                if (offset.y < headerHeightPx) return@detectTapGestures
                val cellW = size.width / 7f
                val col = (offset.x / cellW).toInt().coerceIn(0, 6)
                val row = ((offset.y - headerHeightPx) / cellHeightPx).toInt()
                val gridIndex = row * 7 + col
                val dayOfMonth = gridIndex - calendarNet.firstWeekdayOffset + 1
                if (dayOfMonth in 1..calendarNet.daysInMonth) {
                    onDayClick(dayOfMonth)
                }
            }
        }
    } else Modifier

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp)
            .then(tapModifier),
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
        val amountStyle = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )

        // Content zone starts below the day-number area.
        // Day number sits at +4dp; with 9sp text (~12dp) plus 2dp gap = 18dp reserved.
        val contentStartDp = 18f
        // Two equal slots in the remaining (64 - 18 = 46dp) space.
        val halfSlotDp = (cellHeightDp - contentStartDp) / 2f  // 23dp

        // --- header row (Sun … Sat) ---
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

            // Today gets primary highlight; selected-but-not-today gets secondary.
            val bgColor: Color? = when {
                day.isToday -> primaryContainerColor
                day.dayOfMonth == selectedDay -> secondaryContainerColor
                else -> null
            }
            if (bgColor != null) {
                drawRoundRect(
                    color = bgColor,
                    topLeft = Offset(cellX + 2.dp.toPx(), cellY + 2.dp.toPx()),
                    size = Size(cellW - 4.dp.toPx(), cellH - 4.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }

            // day number (top-left)
            val dayNumMeasured = textMeasurer.measure("${day.dayOfMonth}", dayNumStyle)
            drawText(
                textLayoutResult = dayNumMeasured,
                topLeft = Offset(
                    x = cellX + 4.dp.toPx(),
                    y = cellY + 4.dp.toPx(),
                ),
            )

            val hasIncome = day.incomeFloat > 0f
            val hasExpense = day.expenseFloat > 0f

            when {
                hasIncome && hasExpense -> {
                    // Income row: centered in upper half of content zone.
                    val incomeLabel = compactAmount(day.incomeFloat, "+")
                    val incomeMeasured = textMeasurer.measure(incomeLabel, amountStyle.copy(color = IncomeGreen))
                    val incomeRowCenterPx = cellY + (contentStartDp + halfSlotDp * 0.5f).dp.toPx()
                    drawText(
                        textLayoutResult = incomeMeasured,
                        topLeft = Offset(
                            x = (cellX + (cellW - incomeMeasured.size.width) / 2f)
                                .coerceIn(cellX, cellX + cellW - incomeMeasured.size.width),
                            y = incomeRowCenterPx - incomeMeasured.size.height / 2f,
                        ),
                    )
                    // Expense row: centered in lower half.
                    val expenseLabel = compactAmount(day.expenseFloat, "-")
                    val expenseMeasured = textMeasurer.measure(expenseLabel, amountStyle.copy(color = errorColor))
                    val expenseRowCenterPx = cellY + (contentStartDp + halfSlotDp * 1.5f).dp.toPx()
                    drawText(
                        textLayoutResult = expenseMeasured,
                        topLeft = Offset(
                            x = (cellX + (cellW - expenseMeasured.size.width) / 2f)
                                .coerceIn(cellX, cellX + cellW - expenseMeasured.size.width),
                            y = expenseRowCenterPx - expenseMeasured.size.height / 2f,
                        ),
                    )
                }
                hasIncome -> {
                    // Single income row, vertically centered in the full content zone.
                    val incomeLabel = compactAmount(day.incomeFloat, "+")
                    val incomeMeasured = textMeasurer.measure(incomeLabel, amountStyle.copy(color = IncomeGreen))
                    val centerPx = cellY + (contentStartDp + halfSlotDp).dp.toPx()
                    drawText(
                        textLayoutResult = incomeMeasured,
                        topLeft = Offset(
                            x = (cellX + (cellW - incomeMeasured.size.width) / 2f)
                                .coerceIn(cellX, cellX + cellW - incomeMeasured.size.width),
                            y = centerPx - incomeMeasured.size.height / 2f,
                        ),
                    )
                }
                hasExpense -> {
                    // Single expense row, vertically centered in the full content zone.
                    val expenseLabel = compactAmount(day.expenseFloat, "-")
                    val expenseMeasured = textMeasurer.measure(expenseLabel, amountStyle.copy(color = errorColor))
                    val centerPx = cellY + (contentStartDp + halfSlotDp).dp.toPx()
                    drawText(
                        textLayoutResult = expenseMeasured,
                        topLeft = Offset(
                            x = (cellX + (cellW - expenseMeasured.size.width) / 2f)
                                .coerceIn(cellX, cellX + cellW - expenseMeasured.size.width),
                            y = centerPx - expenseMeasured.size.height / 2f,
                        ),
                    )
                }
                // else: no activity — empty cell
            }
        }
    }
}

/**
 * Compact PHP amount for a small calendar cell.
 *
 * Format spec: <1k→integer; 1k–9k→2dp k; 10k–999k→1dp k; ≥1M→2dp M.
 * sign is "+" for income rows and "-" for expense rows.
 */
private fun compactAmount(absVal: Float, sign: String): String = when {
    absVal >= 1_000_000f -> "$sign₱${String.format("%.2f", absVal / 1_000_000f)}M"
    absVal >= 10_000f    -> "$sign₱${String.format("%.1f", absVal / 1_000f)}k"
    absVal >= 1_000f     -> "$sign₱${String.format("%.2f", absVal / 1_000f)}k"
    else                 -> "$sign₱${absVal.toInt()}"
}
