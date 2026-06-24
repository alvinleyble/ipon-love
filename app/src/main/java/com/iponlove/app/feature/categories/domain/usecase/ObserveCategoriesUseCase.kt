package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Category>> =
        repository.observeCategories(includeArchived)
}
