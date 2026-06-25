package com.iponlove.app.feature.partnerdebt.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * The whole partner-debt picture for the signed-in user, derived by
 * [com.iponlove.app.feature.partnerdebt.domain.usecase.PartnerDebtCalculator]: the netted
 * "who owes whom" summary plus every active debt with its remaining balance and payments.
 * Everything is framed from *my* perspective so the UI never re-resolves borrower/lender.
 */
data class PartnerDebtBoard(
    val net: DebtNet,
    /** Active (non-deleted) debts, unsettled first, each newest-first within its group. */
    val debts: List<DebtItem>,
)

/** Which way the couple nets out, after summing every debt's remaining balance. */
enum class NetDirection { I_OWE, OWED_TO_ME, SETTLED }

/**
 * The single netted balance between the two partners. [amount] is always non-negative
 * (the absolute net); [direction] says who is ahead. [counterpartName] is the partner's
 * display name, or null until their row has synced in.
 */
data class DebtNet(
    val direction: NetDirection,
    val amount: BigDecimal,
    val counterpartName: String?,
)

/** A single debt, framed from my perspective with its derived remaining balance. */
data class DebtItem(
    val id: String,
    /** The original borrowed amount. */
    val original: BigDecimal,
    /** Sum of recorded payments (may exceed [original] if overpaid). */
    val paid: BigDecimal,
    /** [original] − [paid], floored at zero. */
    val remaining: BigDecimal,
    val description: String?,
    /** True when I am the borrower (I owe my partner); false when my partner owes me. */
    val iAmBorrower: Boolean,
    val counterpartName: String?,
    /** Paid ÷ original, clamped to 0f..1f, for the progress bar. */
    val fraction: Float,
    /** [remaining] is zero — the debt is fully repaid. */
    val isSettled: Boolean,
    val createdAt: Instant,
    /** This debt's payments, newest first. */
    val payments: List<DebtPaymentItem>,
)

/** A payment row for the debt-detail view. */
data class DebtPaymentItem(
    val id: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
)
