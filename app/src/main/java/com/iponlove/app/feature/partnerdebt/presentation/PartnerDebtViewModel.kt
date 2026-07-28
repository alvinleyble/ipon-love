package com.iponlove.app.feature.partnerdebt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPaymentItem
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.partnerdebt.domain.usecase.AddSettlementIncomeUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.CheckPartnerDebtCapUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocationCalculator
import com.iponlove.app.feature.partnerdebt.domain.usecase.DeletePartnerDebtUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.ObservePartnerDebtBoardUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.SettleDebtsUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.UpsertPartnerDebtUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
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
    private val settleDebts: SettleDebtsUseCase,
    private val addSettlementIncome: AddSettlementIncomeUseCase,
    private val deleteDebt: DeletePartnerDebtUseCase,
    private val checkDebtCap: CheckPartnerDebtCapUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val dialog = MutableStateFlow<DebtDialog?>(null)
    private val upsell = MutableStateFlow<UpsellPrompt?>(null)

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
            upsell,
        ) { board, members, accounts, openDialog, upsellState ->
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
                myAccentColor = members.me.accentColor,
                partnerAccentColor = members.partner?.accentColor,
                dialog = openDialog,
                upsell = upsellState,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PartnerDebtUiState(),
            )

    fun cancelDialog() {
        dialog.value = null
    }

    // ---- add debt ----

    fun startAddDebt() {
        // Gate the couple-debt cap at create-intent (S7). Only un-settled debts count (G1); the
        // board already flags each item's settled state, so count from the live board.
        viewModelScope.launch {
            val unsettled = uiState.value.debts.count { !it.isSettled }
            when (val check = checkDebtCap(unsettled)) {
                CapCheck.Allowed -> dialog.value = DebtDialog.AddDebt()
                is CapCheck.Blocked -> {
                    upsell.value = UpsellPrompt("debts", check.freeLimit, check.premiumMax)
                }
            }
        }
    }

    fun dismissUpsell() {
        upsell.value = null
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before routing to paywall. */
    fun onUpsellUpgrade(): String {
        val source = "couple_debts"
        analytics.log("upsell_tap", source = source)
        upsell.value = null
        return source
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
        // Overflow may only spill into debts running the same way — never into one my partner
        // owes me, which would flip who owes whom (ADR-0055 #1).
        val others = uiState.value.debts
            .filter { it.id != debt.id && it.iAmBorrower && !it.isSettled }
            .map { SettleCandidate(it.id, it.description ?: "Untitled debt", it.remaining) }
        dialog.value = DebtDialog.Settle(
            debtId = debt.id,
            debtLabel = debt.description ?: "this debt",
            remaining = debt.remaining,
            accountId = uiState.value.accounts.firstOrNull()?.id,
            others = others,
        )
    }

    fun onSettleAmountChange(value: String) = updateSettle { it.copy(amountText = value, amountError = false) }

    fun onSettleAccountChange(accountId: String) = updateSettle { it.copy(accountId = accountId, accountError = false) }

    fun onSettleNoteChange(value: String) = updateSettle { it.copy(note = value) }

    /** Tick/untick a debt to include it in the overflow; ticks append, so the list is fill order. */
    fun onToggleSettleTarget(debtId: String) = updateSettle { editor ->
        val ticked = if (debtId in editor.tickedIds) {
            editor.tickedIds - debtId
        } else {
            editor.tickedIds + debtId
        }
        editor.copy(tickedIds = ticked, amountError = false)
    }

    /** The "Pay everything" shortcut — ticks every candidate and fills the amount to the ceiling. */
    fun onPayFullOutstanding() = updateSettle { editor ->
        val allTicked = editor.copy(tickedIds = editor.others.map { it.debtId })
        allTicked.copy(amountText = allTicked.ceiling.toPlainString(), amountError = false)
    }

    fun saveSettle() {
        val editor = dialog.value as? DebtDialog.Settle ?: return
        // Blocked, not silently capped: a non-positive amount, or more than the ticked debts
        // can absorb, keeps the sheet open with the ceiling spelled out (ADR-0055 #4).
        val amount = editor.amount
        if (amount == null || editor.exceedsCeiling) {
            dialog.value = editor.copy(amountError = true)
            return
        }
        val account = editor.accountId
        if (account == null) {
            dialog.value = editor.copy(accountError = true)
            return
        }
        val allocations = DebtAllocationCalculator.allocate(editor.targets, amount)
        viewModelScope.launch {
            settleDebts(
                allocations = allocations,
                payorAccountId = account,
                note = editor.note.trim().ifBlank { null },
            )
            dialog.value = null
        }
    }

    // ---- add to my account (receiver leg) ----

    fun startReceive(debt: DebtItem, payment: DebtPaymentItem) {
        val payorTxnId = payment.payorTxnId ?: return
        // The payor may have split one lump across several debts, so the income leg covers the
        // whole group at once — the amount is the lump, not this row's share (ADR-0055 #6).
        // Rows that already carry a receiver leg are left out of both the total and the count,
        // matching what the repository will actually stamp (first writer wins per row).
        val group = uiState.value.debts
            .flatMap { it.payments }
            .filter { it.payorTxnId == payorTxnId && it.receiverTxnId == null }
            .distinctBy { it.id }
        if (group.isEmpty()) return
        val total = group.fold(BigDecimal.ZERO) { sum, it -> sum + it.amount }
        dialog.value = DebtDialog.Receive(
            payorTxnId = payorTxnId,
            amount = total,
            debtLabel = if (group.size > 1) "${group.size} debts" else debt.description ?: "this debt",
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
                payorTxnId = editor.payorTxnId,
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
