package com.iponlove.app.feature.widget.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val errors: Set<TransactionError> = emptySet(),
)
