package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Create or edit a transaction. Normalises type-irrelevant fields (a transfer carries no
 * category; income/expense carries no destination), then validates and rejects bad input.
 */
class UpsertTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transaction: Transaction) {
        val normalized = when (transaction.type) {
            TransactionType.TRANSFER -> transaction.copy(categoryId = null)
            TransactionType.INCOME, TransactionType.EXPENSE -> transaction.copy(toAccountId = null)
        }

        val errors = TransactionValidator.validate(
            type = normalized.type,
            amount = normalized.amount,
            accountId = normalized.accountId,
            toAccountId = normalized.toAccountId,
            categoryId = normalized.categoryId,
            isSettlement = normalized.isSettlement,
        )
        require(errors.isEmpty()) { "Invalid transaction: $errors" }

        repository.upsertTransaction(normalized)
    }
}
