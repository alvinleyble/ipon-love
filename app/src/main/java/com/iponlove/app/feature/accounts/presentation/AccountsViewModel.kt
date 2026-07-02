package com.iponlove.app.feature.accounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import com.iponlove.app.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.DeleteAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ReorderAccountsUseCase
import com.iponlove.app.feature.accounts.domain.usecase.ShareAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.UnshareAccountUseCase
import com.iponlove.app.feature.accounts.domain.usecase.UpsertAccountUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator
import com.iponlove.app.feature.transactions.domain.usecase.ObserveBalanceLedgerUseCase
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
    observeBalanceLedger: ObserveBalanceLedgerUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    private val upsertAccount: UpsertAccountUseCase,
    private val archiveAccount: ArchiveAccountUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val shareAccount: ShareAccountUseCase,
    private val unshareAccount: UnshareAccountUseCase,
    private val reorderAccounts: ReorderAccountsUseCase,
) : ViewModel() {

    private val editor = MutableStateFlow<AccountEditorState?>(null)

    // Couple id captured for the share action; null when not paired.
    private var coupleId: String? = null

    val uiState: StateFlow<AccountsUiState> =
        combine(
            observeAccounts(),
            observeBalanceLedger(),
            observeCoupleMembers(),
            editor,
        ) { accounts, ledger, members, editorState ->
            coupleId = members?.me?.coupleId
            // Current balance = opening_balance + ledger, derived locally (ADR-0007). For a
            // shared account the ledger carries both partners' postings (ADR-0018).
            val openingBalances = accounts.associate { it.id to it.openingBalance }
            val balances = AccountBalanceCalculator.balances(openingBalances, ledger)
            AccountsUiState(
                isLoading = false,
                accounts = accounts,
                balances = balances,
                isPaired = members != null,
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

    /** Make a personal account couple-owned (shared). No-op if not paired. */
    fun share(id: String) {
        val couple = coupleId ?: return
        viewModelScope.launch { shareAccount(id, couple) }
    }

    /** Revert a shared account to its creator's personal account (ADR-0018). */
    fun unshare(id: String) {
        viewModelScope.launch { unshareAccount(id) }
    }

    fun archive(id: String, archived: Boolean) {
        viewModelScope.launch { archiveAccount(id, archived) }
    }

    /** Persist a drag-handle reorder from the Manage tab (item 9b) — [orderedIds] top-to-bottom. */
    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { reorderAccounts(orderedIds) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteAccount(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
