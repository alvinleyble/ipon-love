package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class ArchiveAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(id: String, archived: Boolean) =
        repository.setArchived(id, archived)
}
