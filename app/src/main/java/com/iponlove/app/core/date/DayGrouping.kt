package com.iponlove.app.core.date

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Groups a month-windowed list into sticky-header day buckets (ADR-0032), shared by Records
 * and Combined so neither reimplements the relative-label rule: "Today"/"Yesterday" only when
 * the viewed month is the actual current month, a plain date everywhere else (including other
 * days within the current month, or any day in a past/future month).
 */
object DayGrouping {

    private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

    data class DayGroup<T>(val label: String, val items: List<T>)

    /**
     * [items] is assumed pre-sorted (e.g. by the query, date desc); each day's relative order
     * is preserved and buckets appear in the order their first item is encountered.
     */
    fun <T> groupByDay(
        items: List<T>,
        dateOf: (T) -> Instant,
        zone: ZoneId,
        today: LocalDate,
        isCurrentMonth: Boolean,
    ): List<DayGroup<T>> {
        val buckets = LinkedHashMap<LocalDate, MutableList<T>>()
        for (item in items) {
            val day = dateOf(item).atZone(zone).toLocalDate()
            buckets.getOrPut(day) { mutableListOf() }.add(item)
        }
        return buckets.map { (day, dayItems) ->
            DayGroup(label = dayHeaderLabel(day, today, isCurrentMonth), items = dayItems)
        }
    }

    fun dayHeaderLabel(day: LocalDate, today: LocalDate, isCurrentMonth: Boolean): String {
        if (isCurrentMonth) {
            if (day == today) return "Today"
            if (day == today.minusDays(1)) return "Yesterday"
        }
        return day.format(DAY_HEADER_FORMAT)
    }
}
