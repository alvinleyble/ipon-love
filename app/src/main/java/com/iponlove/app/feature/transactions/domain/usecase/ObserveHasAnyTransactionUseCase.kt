package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Whether the current user has ever recorded a transaction — distinguishes Records' "no
 * transactions yet" (brand new) empty state from "no transactions this month" (ADR-0032).
 */
class ObserveHasAnyTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeHasAnyTransaction()
}
