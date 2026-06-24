package com.iponlove.app.feature.analysis.domain.model

import java.time.Instant

/**
 * The half-open time window [startInclusive, endExclusive) the Analysis tab is currently
 * showing, plus the [period] granularity that produced it. Built by
 * [com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange] from an anchor
 * date in a specific zone, so all range math stays deterministic and testable.
 */
data class AnalysisWindow(
    val period: AnalysisPeriod,
    val startInclusive: Instant,
    val endExclusive: Instant,
)
