package com.iponlove.app.feature.export.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A resolved export window (v1.7.0 Item 6, re-grilled 2026-07-24): a [startInclusive, endExclusive)
 * instant span for the query, a human [label] for the sheet, and a day-precise [filenameSuffix].
 * Always an explicit From/To day range now — no month presets (superseded decision, see the doc).
 */
data class ExportDateRange(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val label: String,
    val filenameSuffix: String,
)

/** Factory for the export sheet's date range. Pure java.time — JVM-testable, no Android. */
object ExportRanges {
    private val DAY_LABEL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val DAY_SUFFIX = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /** An inclusive [startDate]..[endDate] day range. Collapses the label/suffix to a single date
     *  when they're equal. */
    fun of(startDate: LocalDate, endDate: LocalDate, zone: ZoneId): ExportDateRange {
        val label = if (startDate == endDate) {
            startDate.format(DAY_LABEL)
        } else {
            "${startDate.format(DAY_LABEL)} – ${endDate.format(DAY_LABEL)}"
        }
        val suffix = if (startDate == endDate) {
            startDate.format(DAY_SUFFIX)
        } else {
            "${startDate.format(DAY_SUFFIX)}_${endDate.format(DAY_SUFFIX)}"
        }
        return ExportDateRange(
            startInclusive = startDate.atStartOfDay(zone).toInstant(),
            endExclusive = endDate.plusDays(1).atStartOfDay(zone).toInstant(),
            label = label,
            filenameSuffix = suffix,
        )
    }

    /** The export sheet's default: 1st of [today]'s month → [today] (month-to-date). */
    fun monthToDate(today: LocalDate, zone: ZoneId): ExportDateRange =
        of(YearMonth.from(today).atDay(1), today, zone)
}
