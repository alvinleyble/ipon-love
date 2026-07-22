package com.iponlove.app.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.CheckCategoryCapUseCase
import com.iponlove.app.feature.categories.domain.usecase.DeleteCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ReorderCategoriesUseCase
import com.iponlove.app.feature.categories.domain.usecase.ShareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UnshareCategoryUseCase
import com.iponlove.app.feature.categories.domain.usecase.UpsertCategoryUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.usecase.CountTransactionsForCategoryUseCase
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
    private val checkCategoryCap: CheckCategoryCapUseCase,
    private val countTransactionsForCategory: CountTransactionsForCategoryUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditorState?>(null)
    private val filter = MutableStateFlow(CategoryFilter.ALL)
    private val upsell = MutableStateFlow<UpsellPrompt?>(null)
    private val showArchived = MutableStateFlow(false)
    private val pendingDelete = MutableStateFlow<PendingCategoryDelete?>(null)

    // Which cap raised the current upsell — the analytics source for its "Get Premium" tap.
    private var upsellSource: String? = null

    // Couple id captured for the share action; null when not paired.
    private var coupleId: String? = null

    val uiState: StateFlow<CategoriesUiState> =
        combine(
            // Pull archived rows too; the [showArchived] toggle decides what the list renders.
            observeCategories(includeArchived = true),
            observeCoupleMembers(),
            filter,
            editor,
            // Pack the archived toggle + pending-delete alongside the upsell flow to stay within
            // combine's 5-arg arity.
            combine(upsell, showArchived, pendingDelete) { u, s, p -> Triple(u, s, p) },
        ) { all, members, activeFilter, editorState, (upsellState, showArch, pendingDeleteState) ->
            coupleId = members?.me?.coupleId
            val typeFiltered = when (activeFilter) {
                CategoryFilter.ALL -> all
                CategoryFilter.INCOME -> all.filter { it.type == CategoryType.INCOME }
                CategoryFilter.EXPENSE -> all.filter { it.type == CategoryType.EXPENSE }
            }
            val visible = if (showArch) typeFiltered else typeFiltered.filterNot { it.isArchived }
            CategoriesUiState(
                isLoading = false,
                categories = visible,
                filter = activeFilter,
                showArchived = showArch,
                hasArchived = typeFiltered.any { it.isArchived },
                isPaired = members != null,
                editor = editorState,
                upsell = upsellState,
                pendingDelete = pendingDeleteState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CategoriesUiState(),
        )

    fun setFilter(value: CategoryFilter) {
        filter.value = value
    }

    /** Toggle whether archived categories are listed (so their "Unarchive" action is reachable). */
    fun setShowArchived(value: Boolean) {
        showArchived.value = value
    }

    fun startCreate() {
        // Gate the personal-categories cap at create-intent (S7); Allowed with enforcement off.
        viewModelScope.launch {
            when (val check = checkCategoryCap(shared = false)) {
                CapCheck.Allowed -> {
                    // Default the new category's type to match the active filter, when it implies one.
                    val defaultType = when (filter.value) {
                        CategoryFilter.INCOME -> CategoryType.INCOME
                        else -> CategoryType.EXPENSE
                    }
                    editor.value = CategoryEditorState(type = defaultType)
                }
                is CapCheck.Blocked -> raiseUpsell("personal_categories", "categories", check)
            }
        }
    }

    fun startEdit(category: Category) {
        editor.value = CategoryEditorState(
            source = category,
            name = category.name,
            type = category.type,
            icon = category.icon,
            color = category.color,
            excludeFromAnalysis = category.excludeFromAnalysis,
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onNameChange(value: String) = editor.update { it?.copy(name = value, nameError = false) }

    fun onTypeChange(value: CategoryType) = editor.update { it?.copy(type = value) }

    fun onIconChange(value: String?) = editor.update { it?.copy(icon = value) }

    fun onColorChange(value: String?) = editor.update { it?.copy(color = value) }

    fun onExcludeFromAnalysisChange(value: Boolean) = editor.update { it?.copy(excludeFromAnalysis = value) }

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
            excludeFromAnalysis = state.excludeFromAnalysis,
        ) ?: Category(
            id = UUID.randomUUID().toString(),
            name = state.name.trim(),
            type = state.type,
            icon = state.icon,
            color = state.color,
            excludeFromAnalysis = state.excludeFromAnalysis,
        )
        viewModelScope.launch {
            upsertCategory(category)
            editor.value = null
        }
    }

    /** Make a personal category couple-owned (shared). No-op if not paired. Gated on the
     *  shared-categories cap (S7). */
    fun share(id: String) {
        val couple = coupleId ?: return
        viewModelScope.launch {
            when (val check = checkCategoryCap(shared = true)) {
                CapCheck.Allowed -> shareCategory(id, couple)
                is CapCheck.Blocked -> raiseUpsell("shared_categories", "shared categories", check)
            }
        }
    }

    private fun raiseUpsell(source: String, entityLabel: String, blocked: CapCheck.Blocked) {
        upsellSource = source
        upsell.value = UpsellPrompt(entityLabel, blocked.freeLimit, blocked.premiumMax)
    }

    fun dismissUpsell() {
        upsell.value = null
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before routing to paywall. */
    fun onUpsellUpgrade(): String {
        val source = upsellSource ?: "personal_categories"
        analytics.log("upsell_tap", source = source)
        upsell.value = null
        return source
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

    /**
     * A tap on "Delete" — count the referencing transactions, then open the adaptive confirm
     * ([PendingCategoryDelete]) instead of deleting outright (v1.6.7 Item 5). Delete is permanent
     * (soft-delete, no un-delete UI), so it's always gated behind a confirm.
     */
    fun requestDelete(category: Category) {
        viewModelScope.launch {
            val count = countTransactionsForCategory(category.id)
            pendingDelete.value = PendingCategoryDelete(category.id, category.name, count)
        }
    }

    /** Delete-anyway from the confirm — tombstones the category; its rows relabel to "Uncategorized". */
    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { deleteCategory(target.id) }
    }

    /** "Archive instead" from the confirm — the non-destructive twin; historical labels are kept. */
    fun archiveInstead() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { archiveCategory(target.id, true) }
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
