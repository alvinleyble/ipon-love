package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * Create or edit an account, after validating the inputs that the UI can't fully
 * guarantee. Trims the name and rejects a blank one.
 */
class UpsertAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(account: Account) {
        val name = account.name.trim()
        require(name.isNotEmpty()) { "Account name must not be blank" }
        repository.upsertAccount(account.copy(name = name))
    }
}
