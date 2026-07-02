package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/** Loads a single transaction by id, for the edit path of the add/edit screen. */
class GetTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(id: String): Transaction? = repository.getTransaction(id)
}
