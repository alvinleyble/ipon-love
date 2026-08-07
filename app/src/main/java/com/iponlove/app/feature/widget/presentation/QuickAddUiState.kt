package com.iponlove.app.feature.widget.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.presentation.ReceiptScanUiState

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    /** Free-text note (v1.7.3 Item 14). Was hardcoded to null before — the sheet had no way to jot
     *  anything down at all. Prefilled by a scan's merchant name, editable either way. */
    val note: String = "",
    /** The one scanned receipt held before save; drives the preview. */
    val image: TransactionImage? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: Set<TransactionError> = emptySet(),
    /**
     * Receipt-scan state, the same type the full form uses — Quick Add runs the same
     * `ScanReceiptUseCase` read + infer pipeline, not a lighter capture-only mode (ADR-0067
     * decision 1), so it has the same states to render.
     */
    val scan: ReceiptScanUiState = ReceiptScanUiState(),
    /**
     * Whether `Save as draft` is enabled. The action is **always visible** (ADR-0067 decision 2 —
     * not conditional on having scanned), but stays disabled until there is something worth
     * parking, so an untouched sheet can't mint an empty queue row. Same rule the full form's
     * `canSaveAsDraft` applies.
     */
    val canSaveAsDraft: Boolean = false,
)
