package com.iponlove.app.feature.partnerdebt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPaymentItem
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.usecase.AddSettlementIncomeUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.DeletePartnerDebtUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.ObservePartnerDebtBoardUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.UpsertPartnerDebtUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the partner-debt tracker. The derived board is recomputed from the live debt +
 * payment streams ([ObservePartnerDebtBoardUseCase]); the member stream supplies the ids the
 * write side needs (who is borrower/lender, which couple to stamp). At most one editor dialog
 * is open at a time ([DebtDialog]); the account stream feeds its pickers.
 */
@HiltViewModel
class PartnerDebtViewModel @Inject constructor(
    observeBoard: ObservePartnerDebtBoardUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    observeAccounts: ObserveAccountsUseCase,
    private val upsertDebt: UpsertPartnerDebtUseCase,
    private val settleDebt: SettleDebtUseCase,
    private val addSettlementIncome: AddSettlementIncomeUseCase,
    private val deleteDebt: DeletePartnerDebtUseCase,
) : ViewModel() {

    private val dialog = MutableStateFlow<DebtDialog?>(null)

    // Captured from the latest member emission so the editor save paths can act without
    // re-deriving: the couple to stamp, and the two member ids to assign borrower/lender.
    private var coupleId: String? = null
    private var myId: String? = null
    private var partnerId: String? = null

    val uiState: StateFlow<PartnerDebtUiState> =
        combine(
            observeBoard(),
            observeCoupleMembers(),
            observeAccounts(),
            dialog,
        ) { board, members, accounts, openDialog ->
            if (board == null || members == null) {
                coupleId = null
                myId = null
                partnerId = null
                return@combine PartnerDebtUiState(isLoading = false, isPaired = false)
            }

            coupleId = members.me.coupleId
            myId = members.me.id
            partnerId = members.partner?.id

            PartnerDebtUiState(
                isLoading = false,
                isPaired = true,
                partnerName = members.partner?.displayName ?: "your partner",
                net = board.net,
                debts = board.debts,
                accounts = accounts.map { AccountOption(it.id, it.name) },
                dialog = openDialog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PartnerDebtUiState(),
        )

    fun cancelDialog() {
        dialog.value = null
    }

    // ---- add debt ----

    fun startAddDebt() {
        dialog.value = DebtDialog.AddDebt()
    }

    fun onDirectionChange(direction: DebtDirection) = updateAddDebt { it.copy(direction = direction) }

    fun onDebtAmountChange(value: String) = updateAddDebt { it.copy(amountText = value, amountError = false) }

    fun onDebtDescriptionChange(value: String) = updateAddDebt { it.copy(description = value) }

    fun saveDebt() {
        val editor = dialog.value as? DebtDialog.AddDebt ?: return
        val couple = coupleId ?: return
        val me = myId ?: return
        val partner = partnerId ?: return
        val amount = editor.amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            dialog.value = editor.copy(amountError = true)
            return
        }
        // I_OWE → I'm the borrower; THEY_OWE → my partner is.
        val (borrower, lender) = when (editor.direction) {
            DebtDirection.I_OWE -> me to partner
            DebtDirection.THEY_OWE -> partner to me
        }
        val debt = PartnerDebt(
            id = UUID.randomUUID().toString(),
            borrowerId = borrower,
            lenderId = lender,
            amount = amount,
            description = editor.description.trim().ifBlank { null },
            createdAt = Instant.now(),
        )
        viewModelScope.launch {
            upsertDebt(debt, couple)
            dialog.value = null
        }
    }

    // ---- settle (payor leg) ----

    fun startSettle(debt: DebtItem) {
        dialog.value = DebtDialog.Settle(
            debtId = debt.id,
            debtLabel = debt.description ?: "this debt",
            remaining = debt.remaining,
            accountId = uiState.value.accounts.firstOrNull()?.id,
        )
    }

    fun onSettleAmountChange(value: String) = updateSettle { it.copy(amountText = value, amountError = false) }

    fun onSettleAccountChange(accountId: String) = updateSettle { it.copy(accountId = accountId, accountError = false) }

    fun onSettleNoteChange(value: String) = updateSettle { it.copy(note = value) }

    fun saveSettle() {
        val editor = dialog.value as? DebtDialog.Settle ?: return
        val amount = editor.amountText.trim().toBigDecimalOrNull()
        // Guard against a non-positive amount or paying more than what's outstanding.
        if (amount == null || amount.signum() <= 0 || amount > editor.remaining) {
            dialog.value = editor.copy(amountError = true)
            return
        }
        val account = editor.accountId
        if (account == null) {
            dialog.value = editor.copy(accountError = true)
            return
        }
        viewModelScope.launch {
            settleDebt(
                debtId = editor.debtId,
                amount = amount,
                payorAccountId = account,
                note = editor.note.trim().ifBlank { null },
            )
            dialog.value = null
        }
    }

    // ---- add to my account (receiver leg) ----

    fun startReceive(debt: DebtItem, payment: DebtPaymentItem) {
        dialog.value = DebtDialog.Receive(
            paymentId = payment.id,
            amount = payment.amount,
            debtLabel = debt.description ?: "this debt",
            accountId = uiState.value.accounts.firstOrNull()?.id,
        )
    }

    fun onReceiveAccountChange(accountId: String) = updateReceive { it.copy(accountId = accountId, accountError = false) }

    fun saveReceive() {
        val editor = dialog.value as? DebtDialog.Receive ?: return
        val account = editor.accountId
        if (account == null) {
            dialog.value = editor.copy(accountError = true)
            return
        }
        viewModelScope.launch {
            addSettlementIncome(
                paymentId = editor.paymentId,
                amount = editor.amount,
                receiverAccountId = account,
                note = editor.debtLabel,
            )
            dialog.value = null
        }
    }

    fun removeDebt(id: String) {
        viewModelScope.launch { deleteDebt(id) }
    }

    private inline fun updateAddDebt(transform: (DebtDialog.AddDebt) -> DebtDialog.AddDebt) =
        dialog.update { (it as? DebtDialog.AddDebt)?.let(transform) ?: it }

    private inline fun updateSettle(transform: (DebtDialog.Settle) -> DebtDialog.Settle) =
        dialog.update { (it as? DebtDialog.Settle)?.let(transform) ?: it }

    private inline fun updateReceive(transform: (DebtDialog.Receive) -> DebtDialog.Receive) =
        dialog.update { (it as? DebtDialog.Receive)?.let(transform) ?: it }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
