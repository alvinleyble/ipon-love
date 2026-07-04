package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.core.session.CurrentUserProvider
import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

/**
 * Saves a TRANSFER together with its optional fee (ADR-0031): a non-zero [feeAmount] is
 * recorded as a second, linked EXPENSE under an auto-created "Transfer fees" category —
 * not a plain field — so it's real, groupable spend in Analysis (unlike a settlement leg,
 * which is deliberately excluded there).
 *
 * [transfer]'s own [Transaction.transferFeeTransactionId] is read as the *previous* linked
 * fee (null for a new transfer, or one that never had a fee). Every save retires that old
 * row (a safe no-op if it's null or already gone) and, if [feeAmount] is positive, creates a
 * fresh linked expense rather than editing the old one in place — simpler than tracking
 * whether the old row is still active, and idempotent under a retry.
 */
class SaveTransferUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val currentUser: CurrentUserProvider,
    private val upsertTransaction: UpsertTransactionUseCase,
) {
    suspend operator fun invoke(transfer: Transaction, feeAmount: BigDecimal) {
        require(transfer.type == TransactionType.TRANSFER) { "Only a transfer can carry a fee" }
        require(feeAmount.signum() >= 0) { "Transfer fee cannot be negative" }

        transfer.transferFeeTransactionId?.let { transactionRepository.deleteTransaction(it) }

        val linkedId = if (feeAmount.signum() > 0) {
            val feeId = UUID.randomUUID().toString()
            upsertTransaction(
                Transaction(
                    id = feeId,
                    type = TransactionType.EXPENSE,
                    amount = feeAmount,
                    accountId = transfer.accountId,
                    categoryId = ensureTransferFeeCategory(),
                    note = "Transfer fee",
                    date = transfer.date,
                    isPrivate = transfer.isPrivate,
                ),
            )
            feeId
        } else {
            null
        }

        upsertTransaction(transfer.copy(transferFeeTransactionId = linkedId))
    }

    /** Idempotent by construction (ADR-0031): same deterministic id every time, per user. */
    private suspend fun ensureTransferFeeCategory(): String {
        val id = DeterministicUuid.v5("builtin-category:transfer-fee:${currentUser.userId()}").toString()
        if (categoryRepository.getCategory(id) == null) {
            categoryRepository.upsertCategory(
                Category(id = id, name = "Transfer fees", type = CategoryType.EXPENSE, icon = "bills"),
            )
        }
        return id
    }
}
