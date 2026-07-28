package com.iponlove.app.feature.partnerdebt.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import com.iponlove.app.feature.partnerdebt.domain.usecase.AllocationTarget
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocation
import com.iponlove.app.feature.partnerdebt.domain.usecase.DebtAllocationCalculator
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
     * Payor settle sheet (ADR-0019 #14, ADR-0055): I owe [debtId] and am paying it down from
     * one of my accounts, recording a real expense leg. Typing more than [remaining] reveals
     * [others] — my other "I owe" debts — so the overflow can spill into the ones I tick.
     * Everything below the plain single-debt case is derived, so the sheet and the save path
     * agree on one ceiling.
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
        /** My other unsettled "I owe" debts, in board order — candidates for the overflow. */
        val others: List<SettleCandidate> = emptyList(),
        /** Ticked candidate ids **in tick order** — that order is the fill order (ADR-0055 #3). */
        val tickedIds: List<String> = emptyList(),
    ) : DebtDialog {

        /** The typed amount, or null while it isn't a positive number yet. */
        val amount: BigDecimal?
            get() = amountText.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

        /** The tapped debt fills first, then the ticked others in the order they were ticked. */
        val targets: List<AllocationTarget>
            get() = listOf(AllocationTarget(debtId, remaining)) +
                tickedIds.mapNotNull { id -> others.firstOrNull { it.debtId == id } }
                    .map { AllocationTarget(it.debtId, it.remaining) }

        /** The most this settlement can pay right now — rises as more debts are ticked. */
        val ceiling: BigDecimal
            get() = DebtAllocationCalculator.ceiling(targets)

        /** True once the typed amount spills past the tapped debt, revealing [others]. */
        val isOverflowing: Boolean
            get() = amount?.let { it > remaining } == true

        /** True when the typed amount can't be covered — blocked, never silently capped. */
        val exceedsCeiling: Boolean
            get() = amount?.let { it > ceiling } == true

        /** How the lump would land, for the per-debt preview. Empty while it can't be paid. */
        val allocations: List<DebtAllocation>
            get() = amount
                ?.takeIf { !exceedsCeiling }
                ?.let { DebtAllocationCalculator.allocate(targets, it) }
                ?: emptyList()
    }

    /**
     * Receiver "add to my account" sheet (ADR-0019 #14): my partner settled and I'm adding
     * the matching income to one of my accounts. The unit is the payor's *transaction*, not
     * one payment — a lump split across debts is added once and stamps the whole group
     * (ADR-0055 #6) — so [amount] is the lump total, not any single payment's share.
     */
    data class Receive(
        val payorTxnId: String,
        val amount: BigDecimal,
        /** What the payment was for, shown in the sheet header. */
        val debtLabel: String,
        val accountId: String? = null,
        val accountError: Boolean = false,
    ) : DebtDialog
}

/** One of my other "I owe" debts, offered to absorb an overflowing settlement. */
data class SettleCandidate(
    val debtId: String,
    val label: String,
    val remaining: BigDecimal,
)
