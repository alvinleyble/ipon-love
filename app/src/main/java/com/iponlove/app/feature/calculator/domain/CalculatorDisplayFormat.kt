package com.iponlove.app.feature.calculator.domain

/**
 * Thousands-grouping for the collapsed pill (ADR-0058 decision 3). The pill exists to be *read at
 * a glance* while the user works on another screen, and `1590` vs `1,590` is the whole difference
 * at a glance — but [CalculatorEngine] deliberately keeps its display raw so the value stays
 * parseable (and copyable) as a number. Grouping is therefore a read-time presentation concern,
 * applied only to the pill, never to what the engine holds or the clipboard receives.
 *
 * Pure and Android-free: no `NumberFormat`, so it is locale-stable and JVM-testable. Anything that
 * isn't a plain number ("Error", a bare "-") passes through untouched.
 */
object CalculatorDisplayFormat {

    fun grouped(display: String): String {
        val negative = display.startsWith("-")
        val unsigned = if (negative) display.drop(1) else display
        val dot = unsigned.indexOf('.')
        val integerPart = if (dot >= 0) unsigned.take(dot) else unsigned
        // Keep the fraction verbatim, including a lone trailing "." mid-entry ("12." stays "12.").
        val rest = if (dot >= 0) unsigned.substring(dot) else ""
        if (integerPart.isEmpty() || integerPart.any { !it.isDigit() }) return display

        val grouped = integerPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + grouped + rest
    }
}
