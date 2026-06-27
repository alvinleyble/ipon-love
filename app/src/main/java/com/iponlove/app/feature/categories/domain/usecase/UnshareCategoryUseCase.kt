package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * Revert a shared category to its creator's personal category (revert-to-creator, ADR-0018).
 * The other partner's replica is demoted to a read-only partner category via the redacting view.
 */
class UnshareCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: String) = repository.unshareCategory(id)
}
