package com.iponlove.app.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.DeleteCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.UpsertCategoryUseCase
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
    private val upsertCategory: UpsertCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditorState?>(null)
    private val filter = MutableStateFlow(CategoryFilter.ALL)

    val uiState: StateFlow<CategoriesUiState> =
        combine(observeCategories(), filter, editor) { all, activeFilter, editorState ->
            val visible = when (activeFilter) {
                CategoryFilter.ALL -> all
                CategoryFilter.INCOME -> all.filter { it.type == CategoryType.INCOME }
                CategoryFilter.EXPENSE -> all.filter { it.type == CategoryType.EXPENSE }
            }
            CategoriesUiState(
                isLoading = false,
                categories = visible,
                filter = activeFilter,
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
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onNameChange(value: String) = editor.update { it?.copy(name = value, nameError = false) }

    fun onTypeChange(value: CategoryType) = editor.update { it?.copy(type = value) }

    fun save() {
        val state = editor.value ?: return
        if (state.name.isBlank()) {
            editor.value = state.copy(nameError = true)
            return
        }
        val category = state.source?.copy(
            name = state.name.trim(),
            type = state.type,
        ) ?: Category(
            id = UUID.randomUUID().toString(),
            name = state.name.trim(),
            type = state.type,
        )
        viewModelScope.launch {
            upsertCategory(category)
            editor.value = null
        }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { archiveCategory(id, archived) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteCategory(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
