package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * How many active transactions reference a category — drives the category delete-confirm's
 * count + archive-steer (v1.6.7 Item 5). Kept as a UseCase (not a raw ViewModel query) so the
 * data access stays in the domain layer per the scalability principle.
 */
class CountTransactionsForCategoryUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(categoryId: String): Int = repository.countByCategory(categoryId)
}
