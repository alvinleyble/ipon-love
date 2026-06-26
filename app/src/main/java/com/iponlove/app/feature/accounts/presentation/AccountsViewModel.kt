package com.iponlove.app.feature.accounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.DeleteAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.accounts.domain.usecase.UpsertAccountUseCase
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeAccounts: ObserveAccountsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    private val upsertAccount: UpsertAccountUseCase,
    private val archiveAccount: ArchiveAccountUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<AccountEditorState?>(null)

    val uiState: StateFlow<AccountsUiState> =
        combine(observeAccounts(), observeTransactions(), editor) { accounts, transactions, editorState ->
            // Current balance = opening_balance + ledger, derived locally (ADR-0007).
            val openingBalances = accounts.associate { it.id to it.openingBalance }
            val balances = AccountBalanceCalculator.balances(openingBalances, transactions)
            AccountsUiState(
                isLoading = false,
                accounts = accounts,
                balances = balances,
                editor = editorState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AccountsUiState(),
        )

    fun startCreate() {
        editor.value = AccountEditorState()
    }

    fun startEdit(account: Account) {
        editor.value = AccountEditorState(
            source = account,
            name = account.name,
            type = account.type,
            openingBalanceText = account.openingBalance.toPlainString(),
            icon = account.icon,
            color = account.color,
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onNameChange(value: String) = editor.update { it?.copy(name = value, nameError = false) }

    fun onTypeChange(value: AccountType) = editor.update { it?.copy(type = value) }

    fun onOpeningBalanceChange(value: String) = editor.update { it?.copy(openingBalanceText = value) }

    fun onIconChange(value: String?) = editor.update { it?.copy(icon = value) }

    fun onColorChange(value: String?) = editor.update { it?.copy(color = value) }

    fun save() {
        val state = editor.value ?: return
        if (state.name.isBlank()) {
            editor.value = state.copy(nameError = true)
            return
        }
        val balance = state.openingBalanceText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        val account = state.source?.copy(
            name = state.name.trim(),
            type = state.type,
            openingBalance = balance,
            icon = state.icon,
            color = state.color,
        ) ?: Account(
            id = UUID.randomUUID().toString(),
            name = state.name.trim(),
            type = state.type,
            openingBalance = balance,
            icon = state.icon,
            color = state.color,
        )
        viewModelScope.launch {
            upsertAccount(account)
            editor.value = null
        }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { archiveAccount(id, archived) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteAccount(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
