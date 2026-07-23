package com.iponlove.app.feature.export

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.export.domain.ExportFilename
import com.iponlove.app.feature.export.domain.model.ExportRanges
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pure-function tests for the export filename builder (v1.7.0 Item 6, re-grilled 2026-07-24,
 * decision 6): a single bare shape, day-precise, no scope word — `LoveIpon-{from}_{to}.ext`,
 * collapsing to a single date when From == To.
 */
class ExportFilenameTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `day range produces a from_to suffix`() {
        val range = ExportRanges.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 24), zone)
        assertThat(ExportFilename.build(range, "csv"))
            .isEqualTo("LoveIpon-2026-07-01_2026-07-24.csv")
    }

    @Test
    fun `a single-day range collapses to one date`() {
        val range = ExportRanges.of(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 20), zone)
        assertThat(ExportFilename.build(range, "csv"))
            .isEqualTo("LoveIpon-2026-07-20.csv")
    }

    @Test
    fun `extension is passed through untouched`() {
        val range = ExportRanges.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 24), zone)
        assertThat(ExportFilename.build(range, "pdf")).endsWith(".pdf")
        assertThat(ExportFilename.build(range, "zip")).endsWith(".zip")
    }

    @Test
    fun `a cross-month range spans both months in the suffix`() {
        val range = ExportRanges.of(LocalDate.of(2026, 5, 15), LocalDate.of(2026, 7, 24), zone)
        assertThat(ExportFilename.build(range, "csv"))
            .isEqualTo("LoveIpon-2026-05-15_2026-07-24.csv")
    }
}
