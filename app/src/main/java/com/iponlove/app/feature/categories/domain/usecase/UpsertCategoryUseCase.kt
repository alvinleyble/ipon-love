package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/** Create or edit a category; trims the name and rejects a blank one. */
class UpsertCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(category: Category) {
        val name = category.name.trim()
        require(name.isNotEmpty()) { "Category name must not be blank" }
        repository.upsertCategory(category.copy(name = name))
    }
}
