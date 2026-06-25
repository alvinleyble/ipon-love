package com.iponlove.app.feature.partnerdebt.presentation

import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import java.math.BigDecimal

/**
 * Screen state for the partner-debt tracker. [isPaired] guards the edge where the screen is
 * reached without a partner (it is normally only offered while paired with a joined partner).
 */
data class PartnerDebtUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val partnerName: String = "your partner",
    val net: DebtNet? = null,
    val debts: List<DebtItem> = emptyList(),
    /** Non-null while the add-debt dialog is open. */
    val addEditor: AddDebtEditorState? = null,
    /** Non-null while the record-payment dialog is open. */
    val paymentEditor: PaymentEditorState? = null,
)

/** Which way a new debt runs, from my perspective. */
enum class DebtDirection {
    /** I borrowed — I owe my partner. */
    I_OWE,

    /** I lent — my partner owes me. */
    THEY_OWE,
}

/** Form state for creating a debt. */
data class AddDebtEditorState(
    val direction: DebtDirection = DebtDirection.I_OWE,
    val amountText: String = "",
    val description: String = "",
    val amountError: Boolean = false,
)

/** Form state for recording a repayment against [debtId]. */
data class PaymentEditorState(
    val debtId: String,
    /** What the debt is for, shown in the dialog header. */
    val debtLabel: String,
    val remaining: BigDecimal,
    val amountText: String = "",
    val note: String = "",
    val amountError: Boolean = false,
)
