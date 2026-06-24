package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. */
class DeleteCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteCategory(id)
}
