package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Both members' shared (non-private) transactions, **unbounded** and ownerless — the spend source
 * for the couple's shared budgets in the Budgets tab (Item 35). Unbounded because a shared budget's
 * rollover chain needs prior months' spend (mirrors [ObserveTransactionsUseCase]'s no-arg overload
 * for personal budgets); ownerless because a spend total doesn't need attribution.
 */
class ObserveCombinedTransactionsForBudgetsUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<List<Transaction>> = repository.observeCombinedTransactionsUnbounded()
}
