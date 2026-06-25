package com.iponlove.app.feature.partnerdebt.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.usecase.DeletePartnerDebtUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.ObservePartnerDebtBoardUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.RecordDebtPaymentUseCase
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
 * write side needs (who is borrower/lender, which couple to stamp). Only the two editor
 * dialogs hold transient UI state.
 */
@HiltViewModel
class PartnerDebtViewModel @Inject constructor(
    observeBoard: ObservePartnerDebtBoardUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    private val upsertDebt: UpsertPartnerDebtUseCase,
    private val recordPayment: RecordDebtPaymentUseCase,
    private val deleteDebt: DeletePartnerDebtUseCase,
) : ViewModel() {

    private val addEditor = MutableStateFlow<AddDebtEditorState?>(null)
    private val paymentEditor = MutableStateFlow<PaymentEditorState?>(null)

    // Captured from the latest member emission so the editor save paths can act without
    // re-deriving: the couple to stamp, and the two member ids to assign borrower/lender.
    private var coupleId: String? = null
    private var myId: String? = null
    private var partnerId: String? = null

    val uiState: StateFlow<PartnerDebtUiState> =
        combine(
            observeBoard(),
            observeCoupleMembers(),
            addEditor,
            paymentEditor,
        ) { board, members, add, payment ->
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
                addEditor = add,
                paymentEditor = payment,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PartnerDebtUiState(),
        )

    // ---- add debt ----

    fun startAddDebt() {
        addEditor.value = AddDebtEditorState()
    }

    fun onDirectionChange(direction: DebtDirection) =
        addEditor.update { it?.copy(direction = direction) }

    fun onDebtAmountChange(value: String) =
        addEditor.update { it?.copy(amountText = value, amountError = false) }

    fun onDebtDescriptionChange(value: String) =
        addEditor.update { it?.copy(description = value) }

    fun cancelAddDebt() {
        addEditor.value = null
    }

    fun saveDebt() {
        val editor = addEditor.value ?: return
        val couple = coupleId ?: return
        val me = myId ?: return
        val partner = partnerId ?: return
        val amount = editor.amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            addEditor.value = editor.copy(amountError = true)
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
            addEditor.value = null
        }
    }

    // ---- record payment ----

    fun startPayment(debt: DebtItem) {
        paymentEditor.value = PaymentEditorState(
            debtId = debt.id,
            debtLabel = debt.description ?: "this debt",
            remaining = debt.remaining,
        )
    }

    fun onPaymentAmountChange(value: String) =
        paymentEditor.update { it?.copy(amountText = value, amountError = false) }

    fun onPaymentNoteChange(value: String) =
        paymentEditor.update { it?.copy(note = value) }

    fun cancelPayment() {
        paymentEditor.value = null
    }

    fun savePayment() {
        val editor = paymentEditor.value ?: return
        val amount = editor.amountText.trim().toBigDecimalOrNull()
        // Guard against a non-positive amount or paying more than what's outstanding.
        if (amount == null || amount.signum() <= 0 || amount > editor.remaining) {
            paymentEditor.value = editor.copy(amountError = true)
            return
        }
        val payment = DebtPayment(
            id = UUID.randomUUID().toString(),
            debtId = editor.debtId,
            amount = amount,
            note = editor.note.trim().ifBlank { null },
            date = Instant.now(),
        )
        viewModelScope.launch {
            recordPayment(payment)
            paymentEditor.value = null
        }
    }

    fun removeDebt(id: String) {
        viewModelScope.launch { deleteDebt(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
