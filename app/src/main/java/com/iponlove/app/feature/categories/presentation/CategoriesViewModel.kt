package com.iponlove.app.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.DeleteCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ReorderCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ShareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UnshareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UpsertCategoryUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    private val upsertCategory: UpsertCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val shareCategory: ShareCategoryUseCase,
    private val unshareCategory: UnshareCategoryUseCase,
    private val reorderCategories: ReorderCategoriesUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditorState?>(null)
    private val filter = MutableStateFlow(CategoryFilter.ALL)

    // Couple id captured for the share action; null when not paired.
    private var coupleId: String? = null

    val uiState: StateFlow<CategoriesUiState> =
        combine(observeCategories(), observeCoupleMembers(), filter, editor) { all, members, activeFilter, editorState ->
            coupleId = members?.me?.coupleId
            val visible = when (activeFilter) {
                CategoryFilter.ALL -> all
                CategoryFilter.INCOME -> all.filter { it.type == CategoryType.INCOME }
                CategoryFilter.EXPENSE -> all.filter { it.type == CategoryType.EXPENSE }
            }
            CategoriesUiState(
                isLoading = false,
                categories = visible,
                filter = activeFilter,
                isPaired = members != null,
                editor = editorState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CategoriesUiState(),
        )

    fun setFilter(value: CategoryFilter) {
        filter.value = value
    }

    fun startCreate() {
        // Default the new category's type to match the active filter, when it implies one.
        val defaultType = when (filter.value) {
            CategoryFilter.INCOME -> CategoryType.INCOME
            else -> CategoryType.EXPENSE
        }
        editor.value = CategoryEditorState(type = defaultType)
    }

    fun startEdit(category: Category) {
        editor.value = CategoryEditorState(
            source = category,
            name = category.name,
            type = category.type,
            icon = category.icon,
            color = category.color,
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onNameChange(value: String) = editor.update { it?.copy(name = value, nameError = false) }

    fun onTypeChange(value: CategoryType) = editor.update { it?.copy(type = value) }

    fun onIconChange(value: String?) = editor.update { it?.copy(icon = value) }

    fun onColorChange(value: String?) = editor.update { it?.copy(color = value) }

    fun save() {
        val state = editor.value ?: return
        if (state.name.isBlank()) {
            editor.value = state.copy(nameError = true)
            return
        }
        val category = state.source?.copy(
            name = state.name.trim(),
            type = state.type,
            icon = state.icon,
            color = state.color,
        ) ?: Category(
            id = UUID.randomUUID().toString(),
            name = state.name.trim(),
            type = state.type,
            icon = state.icon,
            color = state.color,
        )
        viewModelScope.launch {
            upsertCategory(category)
            editor.value = null
        }
    }

    /** Make a personal category couple-owned (shared). No-op if not paired. */
    fun share(id: String) {
        val couple = coupleId ?: return
        viewModelScope.launch { shareCategory(id, couple) }
    }

    /** Revert a shared category to its creator's personal category (ADR-0018). */
    fun unshare(id: String) {
        viewModelScope.launch { unshareCategory(id) }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { archiveCategory(id, archived) }
    }

    /** Persist a drag-handle reorder from the Manage tab (item 9b) — [orderedIds] top-to-bottom. */
    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { reorderCategories(orderedIds) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteCategory(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
