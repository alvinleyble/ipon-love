package com.iponlove.app.feature.drafts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.drafts.domain.usecase.DeleteDraftUseCase
import com.iponlove.app.feature.drafts.domain.usecase.ObserveDraftsUseCase
import com.iponlove.app.feature.transactions.data.ReceiptFileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Backs the drafts list — the parking area inside Records (ADR-0066). */
@HiltViewModel
class DraftsViewModel @Inject constructor(
    observeDrafts: ObserveDraftsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeAccounts: ObserveAccountsUseCase,
    private val deleteDraft: DeleteDraftUseCase,
    private val receiptFiles: ReceiptFileStore,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<DraftRow?>(null)

    val uiState: StateFlow<DraftsUiState> = combine(
        observeDrafts(),
        observeCategories(),
        observeAccounts(),
        pendingDelete,
    ) { drafts, categories, accounts, pending ->
        val rows = draftRows(
            drafts = drafts,
            categoryNames = categories.associate { it.id to it.name },
            accountNames = accounts.associate { it.id to it.name },
            now = Instant.now(),
            localPathFor = receiptFiles::pathIfPresent,
        )
        DraftsUiState(
            rows = rows,
            isLoading = false,
            // Re-resolved against the live list so a row deleted on another device can't leave a
            // dialog up naming a draft that no longer exists.
            pendingDelete = pending?.let { row -> rows.firstOrNull { it.id == row.id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DraftsUiState(),
    )

    fun requestDelete(row: DraftRow) {
        pendingDelete.value = row
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    /** The only thing that ever retires a draft besides settling it — never a timer (decision 10). */
    fun confirmDelete() {
        val row = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { deleteDraft(row.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
