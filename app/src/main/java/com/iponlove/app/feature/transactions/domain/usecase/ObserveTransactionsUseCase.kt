package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

class ObserveTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    /** All active transactions, unbounded — Analysis/Budgets do their own windowing over the full stream. */
    operator fun invoke(): Flow<List<Transaction>> = repository.observeTransactions()

    /** Records' month-windowed view (ADR-0032) — filtered at the query layer, not in memory. */
    operator fun invoke(startInclusive: Instant, endExclusive: Instant): Flow<List<Transaction>> =
        repository.observeTransactions(startInclusive, endExclusive)
}
