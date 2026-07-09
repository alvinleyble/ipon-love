package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Derives lightweight spending-pace metrics for the Analysis Flow tab (Item 3A, generalized
 * 2026-07-09 from month-only to any range).
 *
 * avg is per-bucket: per-day on DAILY ranges, per-month on MONTHLY ones. On a current
 * in-progress period the denominator is only the elapsed buckets (so pace isn't diluted by
 * buckets that haven't happened yet); on a completed past period it's the full bucket count.
 *
 * The budget line + budget-remaining were dropped (grill 2026-07-09) — budget-vs-spend is the
 * Budgets tab's job.
 */
object FlowMetricsCalculator {

    data class Result(
        val avg: BigDecimal,
        /** true → avg/projected are per-month (MONTHLY ranges); false → per-day. */
        val perMonth: Boolean,
        /** Pace × full-period length; null on completed/past periods, ALL_TIME, or 1-bucket ranges. */
        val projected: BigDecimal?,
    )

    /**
     * @param currentBucketIndex index of the bucket containing today, or null for a past period.
     * @param allowProjection false for ALL_TIME (unbounded — no meaningful full-period length).
     */
    fun calculate(
        totalExpense: BigDecimal,
        bucketMode: FlowBucketMode,
        bucketCount: Int,
        currentBucketIndex: Int?,
        allowProjection: Boolean,
    ): Result {
        val elapsed = (currentBucketIndex?.plus(1) ?: bucketCount).coerceAtLeast(1)
        val avg = totalExpense.divide(BigDecimal(elapsed), 2, RoundingMode.HALF_UP)

        // Projection only makes sense for a still-running, bounded, multi-bucket period.
        val projected: BigDecimal? =
            if (allowProjection && currentBucketIndex != null && bucketCount > 1) {
                avg.multiply(BigDecimal(bucketCount)).setScale(2, RoundingMode.HALF_UP)
            } else null

        return Result(
            avg = avg,
            perMonth = bucketMode == FlowBucketMode.MONTHLY,
            projected = projected,
        )
    }
}
