package com.iponlove.app.feature.partnerdebt.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import java.math.BigDecimal

/**
 * Screen state for the partner-debt tracker. [isPaired] guards the edge where the screen is
 * reached without a partner (it is normally only offered while paired with a joined partner).
 * [accounts] feeds the account pickers in the settle / receive sheets.
 */
data class PartnerDebtUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val partnerName: String = "your partner",
    val net: DebtNet? = null,
    val debts: List<DebtItem> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
    /** My couple accent color (ADR-0014), for the owner-tint on debts I owe. Null → Playful fallback. */
    val myAccentColor: String? = null,
    /** My partner's couple accent color (ADR-0014), for the owner-tint on debts they owe. */
    val partnerAccentColor: String? = null,
    /** The single open dialog, or null when none is shown. */
    val dialog: DebtDialog? = null,
    /** Non-null while the count-cap upsell sheet is showing (S7; only ever set under enforcement). */
    val upsell: UpsellPrompt? = null,
    /** Global amount-masking flag (Item 7 / Item 15). Drives the hero's privacy eye icon. */
    val privacyModeEnabled: Boolean = false,
)

/** A pickable account for the settle / receive sheets. */
data class AccountOption(val id: String, val name: String)

/** Which way a new debt runs, from my perspective. */
enum class DebtDirection {
    /** I borrowed — I owe my partner. */
    I_OWE,

    /** I lent — my partner owes me. */
    THEY_OWE,
}

/** The partner-debt screen shows at most one dialog at a time. */
sealed interface DebtDialog {

    /** Form state for creating a debt. */
    data class AddDebt(
        val direction: DebtDirection = DebtDirection.I_OWE,
        val amountText: String = "",
        val description: String = "",
        val amountError: Boolean = false,
    ) : DebtDialog

    /**
     * Payor settle sheet (ADR-0019 #14): I owe [debtId] and am paying it down from one of my
     * accounts, recording a real expense leg.
     */
    data class Settle(
        val debtId: String,
        /** What the debt is for, shown in the sheet header. */
        val debtLabel: String,
        val remaining: BigDecimal,
        val amountText: String = "",
        val accountId: String? = null,
        val note: String = "",
        val amountError: Boolean = false,
        val accountError: Boolean = false,
    ) : DebtDialog

    /**
     * Receiver "add to my account" sheet (ADR-0019 #14): my partner settled and I'm adding
     * the matching income to one of my accounts. The [amount] is fixed to the payor's leg.
     */
    data class Receive(
        val paymentId: String,
        val amount: BigDecimal,
        /** What the debt is for, shown in the sheet header. */
        val debtLabel: String,
        val accountId: String? = null,
        val accountError: Boolean = false,
    ) : DebtDialog
}
