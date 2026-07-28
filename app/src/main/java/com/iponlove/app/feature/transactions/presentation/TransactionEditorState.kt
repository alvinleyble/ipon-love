package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import java.time.Instant

/**
 * Editor form state for the add/edit-transaction screen. [isEditing] true means updating an
 * existing transaction. The persistable fields are mirrored into `SavedStateHandle` by
 * [AddTransactionViewModel] so an in-progress draft survives process death; [errors] and
 * [amountOwedError] are transient (recomputed on save) and are not persisted.
 */
data class TransactionEditorState(
    /** Always pre-generated so receipt image files can be keyed to this transaction before save. */
    val id: String,
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val isPrivate: Boolean = false,
    val date: Instant = Instant.now(),
    /**
     * True when this is a manual balance-correction row (ADR-0057) loaded for edit — never set
     * by anything in this editor itself (there is no "mark as adjustment" toggle; only
     * [com.iponlove.app.feature.accounts.domain.usecase.AdjustAccountBalanceUseCase] creates one).
     * Carried through so re-saving an existing adjustment row doesn't silently strip the flag —
     * the same bug class v1.7.1 Item 15 fixed for `isSettlement`. Hides the category picker.
     */
    val isAdjustment: Boolean = false,
    val errors: Set<TransactionError> = emptySet(),
    /**
     * Receipt photos in the draft (up to [TransactionImage.MAX]). Each carries either a
     * `localPath` (picked this session, pending upload) or a `url` (loaded when editing an
     * existing transaction). Reconciled to `transaction_images` rows on save (deferred
     * persistence, unlike notes). Ordered as shown in the strip; `transactionId` == [id].
     */
    val images: List<TransactionImage> = emptyList(),
    /** "Paid for partner" toggle: on save, also creates a partner debt for [amountOwedText]. */
    val paidForPartner: Boolean = false,
    /** What the partner owes; defaults to the full amount, editable down. Blank = full amount. */
    val amountOwedText: String = "",
    val amountOwedError: Boolean = false,
    /** Optional fee on a TRANSFER, recorded as a linked expense (ADR-0031). Blank = no fee. */
    val transferFeeText: String = "",
    val transferFeeError: Boolean = false,
)
