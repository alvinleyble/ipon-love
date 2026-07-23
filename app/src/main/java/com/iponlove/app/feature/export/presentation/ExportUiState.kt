package com.iponlove.app.feature.export.presentation

import android.net.Uri
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.presentation.FilterOption
import java.time.LocalDate

/**
 * State for the export bottom sheet (v1.7.0 Item 6, re-grilled 2026-07-24). Export is fully
 * self-contained now — its own filter and date range, starting blank + month-to-date, independent
 * of Records' own applied filter and viewed month.
 */
data class ExportUiState(
    /** The "What to include" row label — "All transactions" / a single category's name / "Filtered". */
    val includeLabel: String = "All transactions",
    val transactionCount: Int = 0,
    val fromDate: LocalDate = LocalDate.now().withDayOfMonth(1),
    val toDate: LocalDate = LocalDate.now(),
    /** True when From is after To — disables Export with an inline note, mirrors Item 7's guard. */
    val rangeInvalid: Boolean = false,
    /** Export is enabled only once the payload has resolved, the range is valid, and the result
     *  isn't empty (decision 8 — a zero-match scope disables Export rather than shipping an empty file). */
    val ready: Boolean = false,
    val appliedFilter: TransactionFilter = TransactionFilter.NONE,
    val filterableCategories: List<FilterOption> = emptyList(),
    val filterableAccounts: List<FilterOption> = emptyList(),
)

/** One-shot side effect from the ViewModel — a finished export ready for the share sheet. */
sealed interface ExportEvent {
    data class Share(val uri: Uri, val mimeType: String) : ExportEvent
}
