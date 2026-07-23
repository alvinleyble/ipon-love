package com.iponlove.app.feature.export.domain.model

/**
 * A fully-resolved export payload (v1.7.0 Item 6): the filtered, name-resolved [rows] plus the
 * [range] the CSV writer and filename builder need. Produced reactively by
 * [com.iponlove.app.feature.export.presentation.ExportViewModel] from (filter × range).
 */
data class ExportData(
    val rows: List<ExportRow>,
    val range: ExportDateRange,
)
