package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. */
class DeleteAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteAccount(id)
}
