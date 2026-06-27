package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The ledger for deriving account balances, including couple-owned shared accounts (ADR-0018):
 * the user's own transactions plus every member's non-private ones. Pair with the accounts'
 * opening balances in [AccountBalanceCalculator] so a shared account sums both partners' spend.
 */
class ObserveBalanceLedgerUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<List<Transaction>> = repository.observeBalanceLedger()
}
