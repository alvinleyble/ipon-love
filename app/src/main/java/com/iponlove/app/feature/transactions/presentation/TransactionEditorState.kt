package com.iponlove.app.feature.transactions.presentation

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
    /** Always pre-generated so the receipt file can be named before save. */
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
    val errors: Set<TransactionError> = emptySet(),
    /** Local path of a receipt picked this session, pending upload. */
    val attachmentLocalPath: String? = null,
    /** Existing server URL loaded when editing a transaction that already has a receipt. */
    val attachmentUrl: String? = null,
    /** "Paid for partner" toggle: on save, also creates a partner debt for [amountOwedText]. */
    val paidForPartner: Boolean = false,
    /** What the partner owes; defaults to the full amount, editable down. Blank = full amount. */
    val amountOwedText: String = "",
    val amountOwedError: Boolean = false,
    /** Optional fee on a TRANSFER, recorded as a linked expense (ADR-0031). Blank = no fee. */
    val transferFeeText: String = "",
    val transferFeeError: Boolean = false,
)
