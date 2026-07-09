package com.iponlove.app.feature.analysis.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Compares the selected window's total expense against the prior same-length window (Item 3A
 * look-back — "vs last month/quarter/…"). Pure, live-recomputed, no storage (the grill's
 * free-comparison note): any past window's total is re-derived from the ledger on demand.
 *
 * ALL_TIME has no "previous" period, so callers skip it.
 */
object FlowComparisonCalculator {

    /**
     * @param percentChange signed % change vs [previous]; null when [previous] is zero (no
     *   baseline to divide by — the UI shows "New" instead). Clamped to ±9999 so a tiny prior
     *   base can't render an absurd number.
     * @param deltaSign sign of (current − previous): -1 spent less, 0 unchanged, +1 spent more.
     */
    data class Result(
        val percentChange: Int?,
        val deltaSign: Int,
    )

    private val HUNDRED = BigDecimal(100)

    /** Returns null when both windows are empty — there's nothing to compare. */
    fun calculate(current: BigDecimal, previous: BigDecimal): Result? {
        if (current.signum() == 0 && previous.signum() == 0) return null
        val delta = current - previous
        val percent: Int? =
            if (previous.signum() > 0) {
                delta.multiply(HUNDRED).divide(previous, 0, RoundingMode.HALF_UP).toInt().coerceIn(-9999, 9999)
            } else null
        return Result(percentChange = percent, deltaSign = delta.signum())
    }
}
