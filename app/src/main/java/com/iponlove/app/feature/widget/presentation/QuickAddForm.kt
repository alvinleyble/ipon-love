package com.iponlove.app.feature.widget.presentation

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import java.math.BigDecimal
import java.time.Instant

/**
 * The widget sheet's editable form (v1.7.3 Item 14, ADR-0067 decision 3).
 *
 * Deliberately **not** [TransactionEditorState][com.iponlove.app.feature.transactions.presentation.TransactionEditorState]:
 * that state carries date picking, transfer destination, private/shared toggling, `isAdjustment`
 * and manual receipt attach, none of which Quick Add has — pulling it in would let the "quick"
 * sheet regrow into a second copy of the full form. Quick Add composes with the same *use cases*
 * instead, which is where the behaviour that must not diverge actually lives.
 *
 * [id] is pre-generated at sheet creation rather than minted at save, so a scanned receipt has
 * something to key its [TransactionImage] against from the moment capture succeeds — and so the
 * parked draft's id is already the future transaction's id, which is what makes promotion need
 * ordering rather than atomicity (ADR-0066 decision 5).
 */
data class QuickAddForm(
    val id: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    /**
     * The single scanned receipt held before save. Quick Add's only receipt path is the two OCR
     * doors — there is no manual attach and no strip — so one image, not a list.
     */
    val image: TransactionImage? = null,
    /** Transient — recomputed on every Save attempt, never mirrored into saved state. */
    val errors: Set<TransactionError> = emptySet(),
)

/**
 * Whether there is anything worth parking. Read off the **form**, never the resolved
 * [QuickAddUiState], because the sheet defaults `accountId` to the first account for display: an
 * untouched form would otherwise look like it had content and could mint an empty queue row.
 *
 * Same rule the full form applies through
 * [TransactionEditorReducer.hasDraftContent][com.iponlove.app.feature.transactions.presentation.TransactionEditorReducer.hasDraftContent],
 * minus the fields Quick Add doesn't have.
 */
fun QuickAddForm.hasDraftContent(): Boolean =
    amountText.isNotBlank() ||
        note.isNotBlank() ||
        categoryId != null ||
        accountId != null ||
        image != null

/**
 * The form as a parked draft — the `Save as draft` exit (ADR-0067 decision 2).
 *
 * Nothing is validated: a draft that could pass `TransactionValidator` would not need to be a
 * draft. [accountId] is passed in resolved (what the sheet actually showed selected) rather than
 * read off the form, so settling the draft later reopens the same account the user saw.
 */
fun QuickAddForm.toDraft(
    accountId: String?,
    parkedAt: Instant,
): TransactionDraft = TransactionDraft(
    id = id,
    type = type,
    amount = amountText.trim().toBigDecimalOrNull(),
    categoryId = categoryId,
    accountId = accountId,
    toAccountId = null, // Quick Add has no TRANSFER leg.
    note = note.trim().ifBlank { null },
    date = parkedAt,
    isPrivate = false,
    receiptCount = if (image != null) 1 else 0,
    localImageIds = listOfNotNull(image?.id),
    parkedAt = parkedAt,
)

/**
 * The validated form as the transaction to save. Carries the pre-generated [QuickAddForm.id], so a
 * receipt keyed against it before save still points at the right row, and a draft parked earlier
 * in this session retires against that same id.
 *
 * Callers validate first (`TransactionValidator`); this only assembles.
 */
fun QuickAddForm.toTransaction(
    amount: BigDecimal,
    accountId: String,
    date: Instant,
): Transaction = Transaction(
    id = id,
    type = type,
    amount = amount,
    accountId = accountId,
    toAccountId = null,
    categoryId = categoryId,
    note = note.trim().ifBlank { null },
    date = date,
    isPrivate = false,
)
