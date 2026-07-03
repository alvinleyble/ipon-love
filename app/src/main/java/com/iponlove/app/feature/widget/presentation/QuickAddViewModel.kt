package com.iponlove.app.feature.widget.presentation

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.domain.usecase.TransactionValidator
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val upsertTransaction: UpsertTransactionUseCase,
) : ViewModel() {

    private val form = MutableStateFlow(QuickAddForm())

    val uiState = combine(
        observeAccounts(),
        observeCategories(),
        form,
    ) { accounts, categories, f ->
        QuickAddUiState(
            type = f.type,
            amountText = f.amountText,
            accountId = f.accountId ?: accounts.firstOrNull()?.id,
            categoryId = f.categoryId,
            accounts = accounts,
            categories = categories.filter { it.type == f.type.toCategoryType() },
            errors = f.errors,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = QuickAddUiState(),
    )

    fun onTypeChange(type: TransactionType) = form.update {
        it.copy(type = type, categoryId = null, errors = emptySet())
    }

    fun onAmountChange(value: String) = form.update { it.copy(amountText = value, errors = emptySet()) }

    fun onAccountChange(id: String) = form.update { it.copy(accountId = id, errors = emptySet()) }

    fun onCategoryChange(id: String) = form.update { it.copy(categoryId = id, errors = emptySet()) }

    fun save(onSaved: () -> Unit) {
        val s = uiState.value
        val amount = form.value.amountText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        val errors = TransactionValidator.validate(
            type = s.type,
            amount = amount,
            accountId = s.accountId,
            toAccountId = null,
            categoryId = s.categoryId,
        )
        if (errors.isNotEmpty()) {
            form.update { it.copy(errors = errors.toSet()) }
            return
        }
        viewModelScope.launch {
            upsertTransaction(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    type = s.type,
                    amount = amount,
                    accountId = s.accountId!!,
                    toAccountId = null,
                    categoryId = s.categoryId,
                    note = null,
                    date = Instant.now(),
                    isPrivate = false,
                ),
            )
            AddTransactionWidget().updateAll(context)
            onSaved()
        }
    }
}

private data class QuickAddForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val errors: Set<TransactionError> = emptySet(),
)

private fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    else -> CategoryType.EXPENSE
}
