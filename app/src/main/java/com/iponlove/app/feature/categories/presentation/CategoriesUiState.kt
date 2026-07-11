package com.iponlove.app.feature.categories.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType

/** Which categories the list is showing. */
enum class CategoryFilter { ALL, INCOME, EXPENSE }

/** Screen state for the Categories tab. [categories] is already filtered by [filter]. */
data class CategoriesUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val filter: CategoryFilter = CategoryFilter.ALL,
    /** When true the list also renders archived categories (so they can be unarchived); default off. */
    val showArchived: Boolean = false,
    /** Whether any archived category exists in the current filter — gates the "Show archived" toggle. */
    val hasArchived: Boolean = false,
    /** Whether the user is paired — gates the "Share with partner" action (ADR-0018). */
    val isPaired: Boolean = false,
    val editor: CategoryEditorState? = null,
    /** Non-null while the count-cap upsell sheet is showing (S7; only ever set under enforcement). */
    val upsell: UpsellPrompt? = null,
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
    val icon: String? = null,
    val color: String? = null,
    val nameError: Boolean = false,
) {
    val isEditing: Boolean get() = source != null
}
