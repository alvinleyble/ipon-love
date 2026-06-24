package com.iponlove.app.feature.analysis.domain.model

/**
 * Granularity the Analysis tab aggregates over. The user steps backward/forward one of
 * these units at a time (ADR-free — Analysis is a pure read over the ledger, no new
 * entity or sync). PRD §6.2 "navigate by day / week / month".
 */
enum class AnalysisPeriod {
    DAY,
    WEEK,
    MONTH,
}
