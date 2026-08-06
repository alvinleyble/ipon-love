package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import java.math.BigDecimal
import java.time.Instant

/** Screen state for the full-screen add/edit-transaction route. */
data class AddTransactionUiState(
    /** Editor is null until the draft is hydrated (fresh, restored, or loaded from DB). */
    val editor: TransactionEditorState? = null,
    /** Picker sources, already sorted by position (DAO order). */
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** True only when creating an expense while paired with an available partner (ADR-0019 #12). */
    val canPayForPartner: Boolean = false,
    /** Partner's display name for the toggle label; "Partner" fallback. */
    val partnerName: String = "Partner",
    /** Whether the user is paired — drives the Private toggle's caption framing (ADR-0038 dec. 6). */
    val isPaired: Boolean = false,
    /** True when the id nav-arg pointed at a transaction that no longer exists. */
    val missing: Boolean = false,
    /** Set when a receipt-photo add is blocked by the free cap (S8); drives the upsell sheet. */
    val upsell: UpsellPrompt? = null,
    /** Receipt-scan state (v1.7.3 Item 2) — the two entry buttons, the review preview, and the
     *  failed-read prompt. */
    val scan: ReceiptScanUiState = ReceiptScanUiState(),
) {
    val loading: Boolean get() = editor == null && !missing
}

/** Screen state for the receipt-scan flow (v1.7.3 Item 2, ADR-0062). */
data class ReceiptScanUiState(
    /** True from the moment an image arrives until recognise + parse + compress finishes. */
    val inProgress: Boolean = false,
    /**
     * The full-resolution capture shown at the top of the form during review (decision 4) — what
     * makes the three *read* fields verifiable by looking, and therefore why they carry no
     * caption while the inferred pair (Slice 2) will.
     */
    val previewPath: String? = null,
    /** Non-null while the failed-read prompt is up (decision 8). */
    val failure: ReceiptScanFailure? = null,
    /**
     * True when text was read but no total was found — a *partial* read, which decision 8 is
     * explicit is not a failure: the form opens with whatever was found. This only drives an
     * inline framing hint (Item 5 gap 5), never a block.
     */
    val amountNotFound: Boolean = false,
    /**
     * True when the parsed date was ambiguous (`07/08/2026` — both parts ≤ 12) and month-first
     * was assumed. Decision 4 marks such a date like an inferred field, since it was guessed
     * rather than read off the paper.
     */
    val dateGuessed: Boolean = false,
    /**
     * `Feature.RECEIPT_SCANNING` soft gate (ADR-0062 decision 6, reversed 2026-08-03). **Ships
     * dormant** — false on today's builds, so both buttons behave exactly as if ungated until the
     * enforcement flip.
     */
    val locked: Boolean = false,
    /**
     * The Settings → Finance "Save scans to gallery" toggle (ADR-0062 decision 7). Read by the
     * screen on API 26–28 only, where the copy needs `WRITE_EXTERNAL_STORAGE`: with the toggle off
     * there is no copy to write, so there is no permission worth asking for.
     */
    val galleryCopyEnabled: Boolean = false,
    /**
     * The merchant whose history filled `Category` / `Account` — the caption's subject ("From your
     * last SM Supermarket visit"). Null when nothing was inferred (ADR-0062 decision 5, Slice 2).
     */
    val inferredFrom: String? = null,
    /** True while the inferred `Category` is still the one on the form — cleared the moment the
     *  user picks their own, since the caption would then be describing a value that's gone. */
    val categoryInferred: Boolean = false,
    /** True while the inferred `Account` is still the one on the form. A wrong account corrupts a
     *  balance where a wrong category only misfiles a chart, which is why both are captioned at
     *  all rather than arriving silently (ADR-0062 Consequences). */
    val accountInferred: Boolean = false,
    /**
     * An already-recorded transaction that looks like this scan (same amount, ±1 day). Drives an
     * inline warning only — **Save is never blocked** (ADR-0062 Consequences): a legitimate second
     * same-day expense is common, and a false positive that refused Save would be worse than the
     * duplicate it prevented.
     */
    val duplicate: DuplicateScanWarning? = null,
)

/** The existing transaction a duplicate warning points at — enough to name it, nothing more. */
data class DuplicateScanWarning(
    val amount: BigDecimal,
    val date: Instant,
)

/**
 * Why a scan produced nothing usable. Split copy per Item 5 gap 5 — delivered at the only moment
 * the user is motivated to read it, since handing off to the system camera rules out any
 * in-viewfinder guidance.
 */
enum class ReceiptScanFailure {
    /** Nothing was recognised at all — almost always a bad frame (glare, angle, motion). */
    NO_TEXT,

    /** Text came back, but nothing usable could be pulled from it. */
    NOTHING_USABLE,
}
