package com.iponlove.app.feature.transactions.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One recognised text line, reduced to just what the parser needs from ML Kit's `Text.Line`:
 * the string plus the bounding-box figures merchant detection (ADR-0062 decision 4 — "the largest
 * text near the top") and the split-row merge (below) use.
 *
 * Deliberately a plain domain type, not ML Kit's own: it keeps
 * [ReceiptParser][com.iponlove.app.feature.transactions.domain.usecase.ReceiptParser] pure Kotlin
 * and JVM-testable, so the OCR call itself never has to be mocked. The Android-side adapter that
 * builds these lives in `data/ReceiptTextRecognizer`.
 *
 * [top], [height], and [left] are in source-image pixels; only their relative values matter.
 */
data class RecognizedLine(
    val text: String,
    val top: Int,
    val height: Int,
    val left: Int = 0,
)

/**
 * What a scan could read off the receipt. Every field is nullable because a **partial read is not
 * a failure** (ADR-0062 decision 8) — whatever was found fills in and the rest stays blank.
 *
 * `Type` is absent by design: it is forced to `EXPENSE` at the call site (a receipt is never
 * income), not parsed. `Category` and `Account` are absent too — they are *inferred from history*
 * in Slice 2, never read off the paper (decision 5).
 */
data class ReceiptScanResult(
    /** The total that left the wallet, or null when no total line was found and no fallback held. */
    val amount: BigDecimal? = null,
    /** The transaction date, already sanity-bounded; null means "keep the form's default (today)". */
    val date: LocalDate? = null,
    /**
     * True when the parsed date could have been either `MM/dd` or `dd/MM` (both parts ≤ 12) and
     * month-first was assumed per PH convention. Decision 4 marks such a date like an inferred
     * field, since it was guessed rather than read.
     */
    val dateIsAmbiguous: Boolean = false,
    /** The cleaned merchant name destined for `Note`, or null when nothing near the top qualified. */
    val merchant: String? = null,
) {
    /** True when nothing at all could be pulled off the receipt — the form opens blank. */
    val isEmpty: Boolean get() = amount == null && date == null && merchant == null
}
