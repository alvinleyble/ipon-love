package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Whether the couple has ever shared a transaction — distinguishes Combined's "no shared
 * activity yet" (never paired activity) empty state from "no shared activity this month"
 * (ADR-0032).
 */
class ObserveHasAnyCombinedTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeHasAnyCombinedTransaction()
}
