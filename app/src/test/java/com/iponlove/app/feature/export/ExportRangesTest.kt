package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.model.ExportRanges
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pure-function tests for [ExportRanges] (v1.7.0 Item 6, re-grilled 2026-07-24): the sheet's
 * month-to-date default resolves to the 1st of the current month through today, for any day of
 * the month — including the 1st itself, where From == To.
 */
class ExportRangesTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `month-to-date resolves from the 1st through today`() {
        val range = ExportRanges.monthToDate(LocalDate.of(2026, 7, 24), zone)
        assertThat(range.filenameSuffix).isEqualTo("2026-07-01_2026-07-24")
        assertThat(range.label).isEqualTo("1 Jul 2026 – 24 Jul 2026")
    }

    @Test
    fun `month-to-date on the 1st collapses to a single date`() {
        val range = ExportRanges.monthToDate(LocalDate.of(2026, 7, 1), zone)
        assertThat(range.filenameSuffix).isEqualTo("2026-07-01")
        assertThat(range.label).isEqualTo("1 Jul 2026")
    }

    @Test
    fun `the resolved instant window covers the full day range`() {
        val range = ExportRanges.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 24), zone)
        assertThat(range.startInclusive)
            .isEqualTo(LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant())
        // endExclusive is the start of the day AFTER the "to" date — so July 24's rows are included.
        assertThat(range.endExclusive)
            .isEqualTo(LocalDate.of(2026, 7, 25).atStartOfDay(zone).toInstant())
    }
}
