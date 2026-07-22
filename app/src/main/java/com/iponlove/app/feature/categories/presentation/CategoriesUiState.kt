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
    /** Non-null while the delete-confirm dialog is open (v1.6.7 Item 5). */
    val pendingDelete: PendingCategoryDelete? = null,
)

/**
 * A category the user has asked to delete, with how many active transactions reference it
 * ([transactionCount]). >0 shows the archive-steering confirm (deleting orphans those rows to
 * "Uncategorized"); 0 shows the plain "can't be undone" confirm. (v1.6.7 Item 5)
 */
data class PendingCategoryDelete(
    val id: String,
    val name: String,
    val transactionCount: Int,
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
    /** Pass-through toggle (ADR-0049): excludes this category's transactions from Analysis/Budgets/Combined. */
    val excludeFromAnalysis: Boolean = false,
    val nameError: Boolean = false,
) {
    val isEditing: Boolean get() = source != null
}
