package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * Revert a shared account to its creator's personal account (revert-to-creator, ADR-0018).
 * The creator keeps the account with its balance/history; the other partner's replica is
 * demoted to a read-only partner account automatically via the redacting view.
 */
class UnshareAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(id: String) = repository.unshareAccount(id)
}
