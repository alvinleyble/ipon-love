package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.core.sync.LocalTransactionRunner
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. A
 * transfer's fee is a cascading linked expense (ADR-0031, [SaveTransferUseCase]): deleting
 * the transfer also retires its fee, so it can't keep affecting balance/Analysis. A plain
 * expense/income (including the linked fee row itself) has no
 * [com.iponlove.app.feature.transactions.domain.model.Transaction.transferFeeTransactionId],
 * so this never cascades the other way.
 *
 * Runs inside one [LocalTransactionRunner.run] because deleting a settlement leg is now a
 * multi-row write (ADR-0065): [settlementEffects] retires the linked `DebtPayment` group (or
 * clears its receiver stamp) in the same atomic pass as the tombstone, so a mid-write failure
 * can't leave the transaction deleted with its debt-ledger effect only half reversed. This is
 * the single choke point every transaction delete funnels through — the per-row kebab today,
 * and any future bulk delete (v1.7.3 Item 7) for free.
 */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
    private val transactionRunner: LocalTransactionRunner = LocalTransactionRunner { block -> block() },
    private val settlementEffects: SettlementDeletionEffects = SettlementDeletionEffects.NONE,
) {
    suspend operator fun invoke(id: String) {
        transactionRunner.run {
            val transaction = repository.getTransaction(id)
            if (transaction != null) {
                transaction.transferFeeTransactionId?.let { feeId -> deleteOne(feeId) }
                deleteOne(id, transaction)
            }
        }
    }

    private suspend fun deleteOne(id: String, existing: Transaction? = null) {
        val transaction = existing ?: repository.getTransaction(id) ?: return
        repository.deleteTransaction(id)
        settlementEffects.onTransactionDeleted(transaction)
    }
}
