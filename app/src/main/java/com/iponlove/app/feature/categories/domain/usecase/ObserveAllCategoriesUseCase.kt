package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Every member's categories (both owners) — for resolving names in the combined view. */
class ObserveAllCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = repository.observeAllCategories()
}
