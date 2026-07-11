package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import org.junit.Test

/**
 * The free-vs-extended range split behind `ANALYSIS_EXTENDED_RANGES` (paywall S10). The three
 * short ranges (1D/1W/1M) stay free; 3M/6M/12M/ALL are the premium-gated extended ranges.
 */
class AnalysisPeriodTest {

    @Test
    fun shortRanges_areFree() {
        assertThat(AnalysisPeriod.DAY.isExtendedRange).isFalse()
        assertThat(AnalysisPeriod.WEEK.isExtendedRange).isFalse()
        assertThat(AnalysisPeriod.MONTH.isExtendedRange).isFalse()
    }

    @Test
    fun longRanges_areExtended() {
        assertThat(AnalysisPeriod.QUARTER.isExtendedRange).isTrue()
        assertThat(AnalysisPeriod.SEMI_ANNUAL.isExtendedRange).isTrue()
        assertThat(AnalysisPeriod.ANNUAL.isExtendedRange).isTrue()
        assertThat(AnalysisPeriod.ALL_TIME.isExtendedRange).isTrue()
    }

    @Test
    fun exactlyThreeRangesAreFree() {
        // Guards the free tier if a new granularity is ever added — it must be classified explicitly.
        assertThat(AnalysisPeriod.entries.count { !it.isExtendedRange }).isEqualTo(3)
    }
}
