package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * Make a personal account couple-owned (shared) under [coupleId] (ADR-0018): both partners
 * then log against it and see its joint balance. Only meaningful while paired.
 */
class ShareAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend operator fun invoke(id: String, coupleId: String) = repository.shareAccount(id, coupleId)
}
