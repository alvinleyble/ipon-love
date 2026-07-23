package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.export.domain.model.ExportDateRange

/**
 * Builds the export filename (v1.7.0 Item 6, re-grilled 2026-07-24): a single fixed shape,
 * `LoveIpon-2026-07-01_2026-07-24.csv` (or `LoveIpon-2026-07-20.csv` for a single-day range) —
 * bare, predictable, no category/scope word folded in (superseded decision; Alvin's call).
 */
object ExportFilename {
    fun build(range: ExportDateRange, extension: String): String =
        "LoveIpon-${range.filenameSuffix}.$extension"
}
