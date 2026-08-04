package com.iponlove.app.feature.partnerdebt.data

import com.iponlove.app.feature.partnerdebt.domain.repository.PartnerDebtRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.SettlementDeletionEffects
import javax.inject.Inject

/**
 * Reverses a settlement's ledger effect when its transaction is deleted (ADR-0065). Neither
 * [com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtsUseCase] nor
 * [com.iponlove.app.feature.partnerdebt.domain.usecase.AddSettlementIncomeUseCase] had an
 * inverse, so deleting a settlement leg left its `DebtPayment` rows fully credited while the
 * money that paid them was gone from the ledger — the debt read as settled with nothing behind
 * it. This is the transactions-domain seam ([SettlementDeletionEffects]) that partner-debt
 * implements to close that gap without `transactions` depending back on this feature.
 */
class PartnerDebtSettlementDeletionEffects @Inject constructor(
    private val repository: PartnerDebtRepository,
) : SettlementDeletionEffects {

    override suspend fun onTransactionDeleted(transaction: Transaction) {
        if (!transaction.isSettlement) return
        when (transaction.type) {
            TransactionType.EXPENSE -> repository.retirePaymentsForPayorTxn(transaction.id)
            TransactionType.INCOME -> repository.clearReceiverStamp(transaction.id)
            TransactionType.TRANSFER -> Unit
        }
    }
}
