package com.iponlove.app.feature.analysis.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * How the Flow tab groups the selected window into cumulative-curve buckets (Item 3A).
 * Short ranges (1D/1W/1M/3M) bucket per day; long ranges (6M/12M/ALL) per month, so the
 * curve stays readable instead of drawing hundreds of daily points.
 */
enum class FlowBucketMode { DAILY, MONTHLY }

/**
 * Bucket-by-bucket cumulative expense for the selected Analysis window (Item 3A, grilled
 * 2026-07-09 — generalized from the old month-only shape). Pure domain model — derived from
 * the transaction ledger on the fly; never stored or synced (ADR-0007).
 *
 * [cumulativeByBucket]: index 0 = first bucket, last = final bucket. Each value is the running
 * total of expenses from the first bucket through that one (monotonically non-decreasing).
 * [bucketStartDates]: parallel to [cumulativeByBucket] — the start date of each bucket, used to
 * build axis labels.
 * [bucketMode]: DAILY or MONTHLY — decides the avg unit (per-day vs per-month) and label format.
 * [currentBucketIndex]: index of the bucket containing today, or null when the window is a past
 * (completed) period — drives the "today" marker.
 */
data class ExpenseFlowData(
    val cumulativeByBucket: List<BigDecimal>,
    val bucketStartDates: List<LocalDate>,
    val bucketMode: FlowBucketMode,
    val currentBucketIndex: Int?,
)
