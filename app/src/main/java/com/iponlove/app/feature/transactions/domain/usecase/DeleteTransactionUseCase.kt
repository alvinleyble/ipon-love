package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteTransaction(id)
}
