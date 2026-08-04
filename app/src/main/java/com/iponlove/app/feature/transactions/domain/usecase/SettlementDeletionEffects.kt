package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction

/**
 * Reconciliation hook fired whenever a transaction is soft-deleted (ADR-0065), so a feature
 * built on top of a settlement-flagged transaction can retire its own linked rows without
 * `transactions` depending back on it. The transactions domain knows nothing about partner
 * debts — it only knows [Transaction.isSettlement] — so partner-debt implements this and Hilt
 * binds it, keeping the dependency pointing the same way it already does (partner-debt →
 * transactions via [UpsertTransactionUseCase]).
 */
fun interface SettlementDeletionEffects {
    suspend fun onTransactionDeleted(transaction: Transaction)

    companion object {
        val NONE = SettlementDeletionEffects { }
    }
}
