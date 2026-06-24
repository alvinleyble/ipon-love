package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAccountsUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Account>> =
        repository.observeAccounts(includeArchived)
}
