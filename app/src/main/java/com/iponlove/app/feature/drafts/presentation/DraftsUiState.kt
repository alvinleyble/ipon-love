package com.iponlove.app.feature.drafts.presentation

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/** Screen state for the drafts list — one list, never two tabs (ADR-0066 decision 9). */
data class DraftsUiState(
    val rows: List<DraftRow> = emptyList(),
    val isLoading: Boolean = true,
    /** The draft the user has asked to delete; drives the confirmation dialog. */
    val pendingDelete: DraftRow? = null,
)

/** One parked draft, rendered. */
data class DraftRow(
    val id: String,
    /** The note (usually the scanned merchant), falling back to the category, then a placeholder. */
    val title: String,
    /** Null when the draft never got an amount — rendered as "No amount yet", not as ₱0. */
    val amount: BigDecimal?,
    val type: TransactionType?,
    /** Category · Account, with whichever is missing named as missing. */
    val subtitle: String,
    /** "parked 12 days ago" (decision 10). */
    val ageLabel: String,
    /** How many receipt photos the draft carries, wherever they live. */
    val receiptCount: Int,
    /** A receipt photo held on THIS device, if any — the row thumbnail that replaced tab two. */
    val thumbnailPath: String?,
    /**
     * True when the draft says it has receipts but this device holds none: it was parked on
     * another phone, and the photos don't cross until promotion (decision 4's known weak point).
     */
    val receiptsOnOtherDevice: Boolean,
)

/**
 * Pure draft → row projection, kept out of the ViewModel so it's JVM-unit-testable.
 *
 * [localPathFor] resolves an image id to a file **that actually exists on this device** — a draft
 * pulled from another phone resolves nothing, which is what raises [DraftRow.receiptsOnOtherDevice]
 * rather than showing a broken thumbnail.
 *
 * A category or account that has since been deleted degrades to "No category" / "No account",
 * exactly as a historical transaction degrades to Uncategorized — the draft table carries no FK
 * precisely so this stays a display concern (ADR-0066 decision 1).
 */
fun draftRows(
    drafts: List<TransactionDraft>,
    categoryNames: Map<String, String>,
    accountNames: Map<String, String>,
    now: Instant,
    localPathFor: (String) -> String?,
): List<DraftRow> = drafts.map { draft ->
    val categoryName = draft.categoryId?.let(categoryNames::get)
    val accountName = draft.accountId?.let(accountNames::get)
    val thumbnail = draft.localImageIds.firstNotNullOfOrNull(localPathFor)
    DraftRow(
        id = draft.id,
        title = draft.note?.takeIf { it.isNotBlank() }
            ?: categoryName
            ?: "Untitled draft",
        amount = draft.amount,
        type = draft.type,
        subtitle = listOf(
            categoryName ?: "No category",
            accountName ?: "No account",
        ).joinToString(" · "),
        ageLabel = draftAgeLabel(draft.parkedAt, now),
        receiptCount = draft.receiptCount,
        thumbnailPath = thumbnail,
        // "On your other device" is claimed only for the shape that actually means it: a count
        // with NO local ids at all, which is exactly how a draft pulled from another phone
        // arrives. A draft that holds ids whose files have since gone simply shows no thumbnail —
        // pointing the user at a device that hasn't got it either would be worse than silence.
        receiptsOnOtherDevice = draft.receiptCount > 0 && draft.localImageIds.isEmpty(),
    )
}
