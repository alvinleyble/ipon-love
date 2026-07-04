package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. A
 * transfer's fee is a cascading linked expense (ADR-0031, [SaveTransferUseCase]): deleting
 * the transfer also retires its fee, so it can't keep affecting balance/Analysis. A plain
 * expense/income (including the linked fee row itself) has no
 * [com.iponlove.app.feature.transactions.domain.model.Transaction.transferFeeTransactionId],
 * so this never cascades the other way.
 */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(id: String) {
        repository.getTransaction(id)?.transferFeeTransactionId?.let { repository.deleteTransaction(it) }
        repository.deleteTransaction(id)
    }
}
