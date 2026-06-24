package com.iponlove.app.feature.categories.presentation

import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType

/** Which categories the list is showing. */
enum class CategoryFilter { ALL, INCOME, EXPENSE }

/** Screen state for the Categories tab. [categories] is already filtered by [filter]. */
data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val filter: CategoryFilter = CategoryFilter.ALL,
    val editor: CategoryEditorState? = null,
)

/**
 * Editor form state. [source] is the category being edited (null for a new one); it is
 * kept so a save preserves the fields the form doesn't touch (position, icon, color,
 * archived).
 */
data class CategoryEditorState(
    val source: Category? = null,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val nameError: Boolean = false,
) {
    val isEditing: Boolean get() = source != null
}
