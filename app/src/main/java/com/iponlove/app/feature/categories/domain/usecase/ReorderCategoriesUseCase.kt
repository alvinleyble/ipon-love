package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/** Persist a Manage drag-handle reorder — [orderedIds] top-to-bottom (item 9b). */
class ReorderCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(orderedIds: List<String>) = repository.reorderCategories(orderedIds)
}
