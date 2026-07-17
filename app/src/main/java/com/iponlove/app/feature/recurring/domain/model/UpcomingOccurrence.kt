package com.iponlove.app.feature.recurring.domain.model

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A future scheduled occurrence of a recurring rule (Item 37, Slice 2 — the premium forecast
 * layer). Purely **derived** — like [PendingConfirmation], nothing is stored; it's the forward
 * view of money coming in / going out, computed live from each rule's schedule.
 *
 * Distinct from [PendingConfirmation] in two ways:
 *  - it covers **future** dates only (strictly after today), so it never overlaps the ledger
 *    (auto-post rules materialize on/before today) or the "To confirm" card (which surfaces
 *    due-but-unrecorded occurrences up to today);
 *  - it includes **both** auto-post and confirm-on-arrival rules — it's a forecast of everything
 *    scheduled, not just the things awaiting a confirm tap.
 *
 * Feeds the Records "Coming up" preview and the Analysis projected-Net figure (both premium,
 * gated on `RECURRING_FORECAST`). Read-only — there are no actions on an upcoming occurrence.
 */
data class UpcomingOccurrence(
    val ruleId: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String,
    val categoryName: String,
    val note: String?,
)
