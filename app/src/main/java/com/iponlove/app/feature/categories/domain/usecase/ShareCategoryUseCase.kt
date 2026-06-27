package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * Make a personal category couple-owned (shared) under [coupleId] (ADR-0018): it then appears
 * in both partners' transaction pickers. Only meaningful while paired.
 */
class ShareCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: String, coupleId: String) = repository.shareCategory(id, coupleId)
}
