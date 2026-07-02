package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/** Persist a Manage drag-handle reorder — [orderedIds] top-to-bottom (item 9b). */
class ReorderAccountsUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(orderedIds: List<String>) = repository.reorderAccounts(orderedIds)
}
