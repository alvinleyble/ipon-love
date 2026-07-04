package com.iponlove.app.feature.analysis.domain.model

/**
 * Granularity the Analysis tab aggregates over. The user steps backward/forward one of
 * these units at a time — all steppable calendar buckets, not crypto-style trailing
 * windows (ADR-0030). ALL_TIME is the one exception: it has nothing to step to.
 */
enum class AnalysisPeriod {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    SEMI_ANNUAL,
    ANNUAL,
    ALL_TIME,
}
