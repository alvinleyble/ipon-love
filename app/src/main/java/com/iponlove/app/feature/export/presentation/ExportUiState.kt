package com.iponlove.app.feature.export.presentation

import android.net.Uri
import com.iponlove.app.feature.export.domain.AttachmentLimits
import com.iponlove.app.feature.export.domain.model.ExportFormat
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.presentation.FilterOption
import java.time.LocalDate

/**
 * State for the export bottom sheet (v1.7.0 Item 6). Export is fully self-contained — its own
 * filter and date range, starting blank + month-to-date, independent of Records' own applied filter
 * and viewed month.
 *
 * Slice 2 adds the attachment formats' three pre-tap guards ([attachmentsLocked], [offline],
 * [photoCapExceeded]) and the in-flight [progress]. All three guards are resolved *before* the tap
 * so PDF/ZIP are visibly refused with a reason, rather than starting work that will fail.
 */
data class ExportUiState(
    /** The "What to include" row label — "All transactions" / a single category's name / "Filtered". */
    val includeLabel: String = "All transactions",
    val transactionCount: Int = 0,
    /** Receipt photos in the current scope — drives the cap check and the progress denominator. */
    val photoCount: Int = 0,
    /** How many of [photoCount] would need downloading (the rest are still on local disk). */
    val downloadCount: Int = 0,
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
    /** `EXPORT_WITH_ATTACHMENTS` (decision 2). False while enforcement is dormant, so PDF/ZIP are
     *  open to everyone until the post-beta flip. */
    val attachmentsLocked: Boolean = false,
    val offline: Boolean = false,
    /** An export already running — the sheet shows numeric progress and a Cancel (decision 5). */
    val progress: ExportProgress? = null,
) {
    /** Decision 4 — refused before the tap, with a narrowing message. */
    val photoCapExceeded: Boolean get() = AttachmentLimits.exceeded(photoCount)

    /**
     * Decision 3a, refined: being offline blocks PDF/ZIP only when the scope actually contains a
     * photo that would have to be downloaded. A scope whose photos are all still on local disk (or
     * that has no photos at all) exports fine with no network, and blocking it would be a lie.
     */
    val offlineBlocked: Boolean get() = offline && downloadCount > 0

    /**
     * Whether an attachment-bearing format can be tapped right now. [attachmentsLocked] is
     * deliberately **not** part of this: a locked tap is a *paywall route*, not a refusal, so the
     * lock never disables the button — it only changes what the tap does. The real blockers are the
     * ones buying Premium wouldn't fix (an over-cap or offline scope, an empty result), and those
     * must keep the button dead so nobody is sold an upgrade that still can't run the export.
     */
    fun attachmentsAvailable(): Boolean =
        ready && progress == null && !photoCapExceeded && !offlineBlocked

    fun enabled(format: ExportFormat): Boolean =
        if (format.carriesAttachments) attachmentsAvailable() else ready && progress == null
}

/** Numeric, cancellable progress for an attachment export (decision 5) — foreground-only. */
data class ExportProgress(
    val format: ExportFormat,
    val done: Int,
    val total: Int,
) {
    /** True once every photo is in and the document itself is being assembled/written. */
    val assembling: Boolean get() = done >= total
}

/** One-shot side effects from the ViewModel. */
sealed interface ExportEvent {
    /** A finished export, ready for the share sheet. */
    data class Share(val uri: Uri, val mimeType: String) : ExportEvent

    /** A locked attachment format was tapped — route to the paywall with this funnel source. */
    data class OpenPaywall(val source: String) : ExportEvent

    /** The export failed (I/O, or every photo unreachable mid-run). */
    data object Failed : ExportEvent
}
