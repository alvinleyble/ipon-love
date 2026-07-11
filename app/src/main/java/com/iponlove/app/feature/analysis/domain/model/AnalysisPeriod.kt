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
    ALL_TIME;

    /**
     * True for the longer ranges gated behind Premium (`ANALYSIS_EXTENDED_RANGES`, paywall S10):
     * 3M/6M/12M/ALL. The three short ranges (1D/1W/1M) stay free. This is a pure classification —
     * the actual lock is `PremiumGate.observeLocked` (enforcement + entitlement); a locked tap on
     * one of these routes to the paywall instead of switching the range.
     */
    val isExtendedRange: Boolean
        get() = this == QUARTER || this == SEMI_ANNUAL || this == ANNUAL || this == ALL_TIME
}
