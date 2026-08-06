package com.iponlove.app.feature.drafts.domain.model

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * A parked, unfinished transaction form (ADR-0066) — the queue a busy user sits down to settle.
 *
 * **Every content field is nullable, because a draft is a partial form.** That is the whole
 * reason drafts get their own table instead of an `is_draft` flag on `transactions`, whose
 * `type`/`amount`/`account_id` are `not null` and whose validator additionally demands
 * amount > 0 + an account + a category — all four of which a draft may legitimately fail.
 *
 * [id] **is the future transaction's id** (the editor pre-generates it, `TransactionEditorState.id`).
 * That identity is what makes promotion need *ordering* rather than atomicity: write the
 * transaction first, retire the draft second, and a re-run is an idempotent upsert of the same
 * id, so money can never double (decision 5).
 *
 * Not carried, by design: `paidForPartner` / `amountOwed` / `transferFee`. Each spawns linked
 * rows in another feature (ADR-0019, ADR-0031) and none is meaningful until the transaction is
 * real, so a draft round-trips them blank.
 */
data class TransactionDraft(
    val id: String,
    val type: TransactionType? = null,
    val amount: BigDecimal? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val note: String? = null,
    val date: Instant? = null,
    val isPrivate: Boolean = false,
    /**
     * How many receipt photos this draft holds. Syncs; the photos themselves do not (decision 4),
     * so on a second device this is what renders "📷 1 receipt — on your other device".
     */
    val receiptCount: Int = 0,
    /**
     * The `filesDir/receipts` image ids this draft's photos live under — **local-only**, never on
     * the wire, the same treatment `pendingSync` gets. Empty on a draft pulled from another
     * device (its files are not on this device either), which is correct: it then contributes
     * nothing to the orphaned-receipt sweep's known-id set (decision 6).
     */
    val localImageIds: List<String> = emptyList(),
    /** When the draft was first parked — the "parked 12 days ago" age label's subject. */
    val parkedAt: Instant,
)
