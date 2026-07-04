package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

/**
 * Both members' shared (non-private) transactions, owner-tagged, month-windowed for the
 * combined view (ADR-0032).
 */
class ObserveCombinedTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(startInclusive: Instant, endExclusive: Instant): Flow<List<OwnedTransaction>> =
        repository.observeCombinedTransactions(startInclusive, endExclusive)
}
