package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.feature.drafts.domain.model.TransactionDraft
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.domain.usecase.TransactionValidator
import java.math.BigDecimal
import java.time.Instant

/**
 * Pure state transitions and the final build/validate step for the transaction editor, kept
 * free of Android so it can be unit-tested on the JVM. [AddTransactionViewModel] wires the
 * DB / couple context and the SavedStateHandle persistence around these functions.
 */
object TransactionEditorReducer {

    /** Result of turning the draft into something savable. */
    sealed interface BuildResult {
        /** Field-level validation failed; show [errors] inline. */
        data class Invalid(val errors: Set<TransactionError>) : BuildResult

        /** The "paid for partner" owed amount is missing or out of range (0 < owed ≤ amount). */
        data object OwedInvalid : BuildResult

        /** The transfer fee is not a valid non-negative amount (ADR-0031). */
        data object TransferFeeInvalid : BuildResult

        /**
         * The draft is valid. [amountOwed] is non-null only when a partner debt should also be
         * created (the "paid for partner" path). [transferFee] is non-null only for a TRANSFER
         * (zero meaning "no fee"), to be saved via [com.iponlove.app.feature.transactions.domain.usecase.SaveTransferUseCase]
         * instead of a plain transaction save.
         */
        data class Ready(
            val transaction: Transaction,
            val amountOwed: BigDecimal? = null,
            val transferFee: BigDecimal? = null,
        ) : BuildResult
    }

    /** Switching type normalises the fields that don't apply to the new type. */
    fun onType(state: TransactionEditorState, type: TransactionType): TransactionEditorState =
        state.copy(
            type = type,
            categoryId = if (type == TransactionType.TRANSFER) null else state.categoryId,
            toAccountId = if (type == TransactionType.TRANSFER) state.toAccountId else null,
            // "Paid for partner" only makes sense on an expense; clear it on any other type.
            paidForPartner = if (type == TransactionType.EXPENSE) state.paidForPartner else false,
            transferFeeText = if (type == TransactionType.TRANSFER) state.transferFeeText else "",
            errors = emptySet(),
        )

    // Spend touching a shared account must stay non-private (ADR-0018), so selecting one clears
    // the private flag — the UI also hides the toggle, this is the model-side guard.
    fun onAccount(
        state: TransactionEditorState,
        id: String,
        sharedAccountIds: Set<String>,
    ): TransactionEditorState = state.copy(
        accountId = id,
        isPrivate = if (id in sharedAccountIds || state.toAccountId in sharedAccountIds) false else state.isPrivate,
        errors = emptySet(),
    )

    fun onToAccount(
        state: TransactionEditorState,
        id: String,
        sharedAccountIds: Set<String>,
    ): TransactionEditorState = state.copy(
        toAccountId = id,
        isPrivate = if (id in sharedAccountIds || state.accountId in sharedAccountIds) false else state.isPrivate,
        errors = emptySet(),
    )

    fun onPaidForPartner(state: TransactionEditorState, value: Boolean): TransactionEditorState =
        state.copy(
            paidForPartner = value,
            // Default the owed amount to the full transaction amount when switching on; the user
            // can edit it down for a bill split. Clear the error either way.
            amountOwedText = if (value && state.amountOwedText.isBlank()) state.amountText else state.amountOwedText,
            amountOwedError = false,
        )

    fun onTransferFee(state: TransactionEditorState, value: String): TransactionEditorState =
        state.copy(transferFeeText = value, transferFeeError = false)

    /**
     * True when there is something worth parking (ADR-0066): any field the user could have filled
     * in, including a scanned receipt. An untouched form has nothing to park, so `Save as draft`
     * stays disabled rather than minting an empty row the user would then have to delete.
     *
     * The date is deliberately excluded — it is pre-filled with today on every fresh form, so
     * counting it would make every form look non-empty.
     */
    fun hasDraftContent(state: TransactionEditorState): Boolean =
        state.amountText.isNotBlank() ||
            state.note.isNotBlank() ||
            state.categoryId != null ||
            state.accountId != null ||
            state.toAccountId != null ||
            state.images.isNotEmpty()

    /**
     * The editor as a parked draft — the `Save as draft` exit (ADR-0066 decision 1). Unlike
     * [build] this **never validates**: a draft that could pass `TransactionValidator` would not
     * need to be a draft, so a blank amount / missing account / missing category all round-trip.
     *
     * [TransactionDraft.id] is the editor's pre-generated id, which is also the id the promoted
     * transaction will carry — the identity that makes promotion need ordering, not atomicity
     * (decision 5).
     *
     * Three fields are deliberately **not** persisted: `paidForPartner`, `amountOwedText` and
     * `transferFeeText`. Each spawns linked rows in another feature (ADR-0019, ADR-0031) and none
     * is meaningful until the transaction is real, so a draft round-trips them blank.
     */
    fun toDraft(state: TransactionEditorState, parkedAt: Instant): TransactionDraft {
        val toAccountId = if (state.type == TransactionType.TRANSFER) state.toAccountId else null
        val categoryId = if (state.type == TransactionType.TRANSFER) null else state.categoryId
        return TransactionDraft(
            id = state.id,
            type = state.type,
            amount = state.amountText.trim().toBigDecimalOrNull(),
            categoryId = categoryId,
            accountId = state.accountId,
            toAccountId = toAccountId,
            note = state.note.trim().ifBlank { null },
            date = state.date,
            isPrivate = state.isPrivate,
            receiptCount = state.images.size,
            localImageIds = state.images.map { it.id },
            parkedAt = parkedAt,
        )
    }

    /**
     * A parked draft back in the editor. [images] is resolved by the caller (only files still on
     * this device can be shown), and [isEditing] stays **false**: settling a draft creates a new
     * transaction, so `Save as draft` remains available to re-park it and the "paid for partner"
     * path stays open.
     */
    fun fromDraft(draft: TransactionDraft, images: List<TransactionImage>): TransactionEditorState =
        TransactionEditorState(
            id = draft.id,
            isEditing = false,
            type = draft.type ?: TransactionType.EXPENSE,
            amountText = draft.amount?.toPlainString().orEmpty(),
            accountId = draft.accountId,
            toAccountId = draft.toAccountId,
            categoryId = draft.categoryId,
            note = draft.note.orEmpty(),
            isPrivate = draft.isPrivate,
            date = draft.date ?: Instant.now(),
            images = images,
        )

    /**
     * Validate the draft and, if valid, produce the transaction to save. [canPayForPartner] gates
     * the debt-creation path (paired, both member ids known, and creating — not editing).
     */
    fun build(
        state: TransactionEditorState,
        sharedAccountIds: Set<String>,
        canPayForPartner: Boolean,
    ): BuildResult {
        val amount = state.amountText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        val categoryId = if (state.type == TransactionType.TRANSFER) null else state.categoryId
        val toAccountId = if (state.type == TransactionType.TRANSFER) state.toAccountId else null
        val touchesShared =
            state.accountId in sharedAccountIds || toAccountId in sharedAccountIds

        val errors = TransactionValidator.validate(
            type = state.type,
            amount = amount,
            accountId = state.accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            isSettlement = state.isSettlement,
            isAdjustment = state.isAdjustment,
            isPrivate = state.isPrivate,
            touchesSharedAccount = touchesShared,
        )
        if (errors.isNotEmpty()) return BuildResult.Invalid(errors.toSet())

        val transaction = Transaction(
            id = state.id,
            type = state.type,
            amount = amount,
            accountId = state.accountId!!,
            toAccountId = toAccountId,
            categoryId = categoryId,
            note = state.note.trim().ifBlank { null },
            date = state.date,
            isPrivate = state.isPrivate,
            isSettlement = state.isSettlement,
            isAdjustment = state.isAdjustment,
        )

        // Transfer fee (ADR-0031): blank = no fee (zero); otherwise must be >= 0.
        if (state.type == TransactionType.TRANSFER) {
            val fee = state.transferFeeText.trim().ifBlank { "0" }.toBigDecimalOrNull()
            if (fee == null || fee.signum() < 0) return BuildResult.TransferFeeInvalid
            return BuildResult.Ready(transaction, transferFee = fee)
        }

        // "Paid for partner" (ADR-0019 #12): only valid on an expense while paired.
        val payForPartner =
            state.paidForPartner && canPayForPartner && state.type == TransactionType.EXPENSE
        if (!payForPartner) return BuildResult.Ready(transaction, amountOwed = null)

        // Blank owed = full amount (the field defaults to it); otherwise 0 < owed ≤ amount.
        val owed = state.amountOwedText.trim().ifBlank { amount.toPlainString() }.toBigDecimalOrNull()
        if (owed == null || owed.signum() <= 0 || owed > amount) return BuildResult.OwedInvalid
        return BuildResult.Ready(transaction, amountOwed = owed)
    }
}
