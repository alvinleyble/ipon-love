package com.iponlove.app.feature.categories.domain.usecase

import com.iponlove.app.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

class ArchiveCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: String, archived: Boolean) =
        repository.setArchived(id, archived)
}
