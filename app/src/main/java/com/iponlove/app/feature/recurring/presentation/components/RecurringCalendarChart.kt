package com.iponlove.app.feature.recurring.presentation.components

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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iponlove.app.feature.recurring.presentation.RecurringRuleListItem
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.min

private val IncomeGreen = Color(0xFF2E7D32)

/**
 * Month grid showing dots on days when recurring rules fire. Dots are green for income
 * rules and red for expense rules, up to [MAX_DOTS] per cell with a "+N" overflow label.
 * Tap a day to select it; tap again to deselect. Today's cell gets [primaryContainer]
 * background; selected (non-today) gets [secondaryContainer].
 *
 * Pure Canvas — no third-party chart library.
 */
@Composable
fun RecurringCalendarChart(
    month: YearMonth,
    firingsByDay: Map<Int, List<RecurringRuleListItem>>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error

    // Java DayOfWeek: Mon=1…Sun=7. Map to Sun=0…Sat=6.
    val firstWeekdayOffset = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val today = LocalDate.now()

    val totalCells = firstWeekdayOffset + daysInMonth
    val rowCount = ceil(totalCells / 7.0).toInt()

    val cellHeightDp = 56
    val headerHeightDp = 22
    val totalHeightDp = headerHeightDp + rowCount * cellHeightDp

    val density = LocalDensity.current
    val headerHeightPx = with(density) { headerHeightDp.dp.toPx() }
    val cellHeightPx = with(density) { cellHeightDp.dp.toPx() }

    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp)
            .pointerInput(month, firingsByDay) {
                detectTapGestures { offset ->
                    if (offset.y < headerHeightPx) return@detectTapGestures
                    val cellW = size.width / 7f
                    val col = (offset.x / cellW).toInt().coerceIn(0, 6)
                    val row = ((offset.y - headerHeightPx) / cellHeightPx).toInt()
                    val gridIndex = row * 7 + col
                    val dayOfMonth = gridIndex - firstWeekdayOffset + 1
                    if (dayOfMonth in 1..daysInMonth) onDayClick(dayOfMonth)
                }
            },
    ) {
        val cellW = size.width / 7f
        val headerH = headerHeightDp.dp.toPx()
        val cellH = cellHeightDp.dp.toPx()

        val headerStyle = TextStyle(fontSize = 10.sp, color = onSurfaceColor.copy(alpha = 0.5f))
        val dayNumStyle = TextStyle(fontSize = 9.sp, color = onSurfaceColor.copy(alpha = 0.55f))
        val overflowStyle = TextStyle(fontSize = 7.sp, color = onSurfaceColor.copy(alpha = 0.5f))

        // Header row
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

        // Day cells
        for (dayOfMonth in 1..daysInMonth) {
            val gridIndex = firstWeekdayOffset + dayOfMonth - 1
            val col = gridIndex % 7
            val row = gridIndex / 7
            val cellX = col * cellW
            val cellY = headerH + row * cellH

            val isToday = month.year == today.year &&
                month.monthValue == today.monthValue &&
                dayOfMonth == today.dayOfMonth
            val bgColor: Color? = when {
                isToday -> primaryContainerColor
                dayOfMonth == selectedDay -> secondaryContainerColor
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

            // Day number (top-left)
            val dayNumMeasured = textMeasurer.measure("$dayOfMonth", dayNumStyle)
            drawText(
                textLayoutResult = dayNumMeasured,
                topLeft = Offset(cellX + 4.dp.toPx(), cellY + 4.dp.toPx()),
            )

            // Dots for firing rules
            val firingRules = firingsByDay[dayOfMonth]
            if (!firingRules.isNullOrEmpty()) {
                val dotRadius = 4.dp.toPx()
                val dotGap = 3.dp.toPx()
                val dotDiameter = dotRadius * 2
                val dotsToShow = min(firingRules.size, MAX_DOTS)
                val hasOverflow = firingRules.size > MAX_DOTS
                val dotCount = if (hasOverflow) dotsToShow - 1 else dotsToShow

                // Center dots horizontally in cell
                val totalDotsWidth = dotCount * dotDiameter + (dotCount - 1) * dotGap
                val dotStartX = cellX + (cellW - totalDotsWidth) / 2f
                val dotY = cellY + cellH - dotRadius - 6.dp.toPx()

                for (i in 0 until dotCount) {
                    val dotColor = if (firingRules[i].type == TransactionType.INCOME) IncomeGreen else errorColor
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(dotStartX + i * (dotDiameter + dotGap) + dotRadius, dotY),
                    )
                }

                if (hasOverflow) {
                    val overflowText = "+${firingRules.size - (MAX_DOTS - 1)}"
                    val overflowMeasured = textMeasurer.measure(overflowText, overflowStyle)
                    val overflowX = dotStartX + dotCount * (dotDiameter + dotGap)
                    drawText(
                        textLayoutResult = overflowMeasured,
                        topLeft = Offset(overflowX, dotY - overflowMeasured.size.height / 2f),
                    )
                }
            }
        }
    }
}

private const val MAX_DOTS = 4
