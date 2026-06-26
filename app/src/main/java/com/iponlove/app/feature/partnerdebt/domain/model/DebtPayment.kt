package com.iponlove.app.feature.partnerdebt.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * One (partial or full) repayment against a [PartnerDebt]. [debtId] ties it to its debt;
 * the sum of a debt's active payments is subtracted from the debt amount to derive the
 * remaining balance. [date] is the user-meaningful payment date (defaults to now in V1).
 *
 * When [isNetting] is true this is an *auto-generated offset* (ADR-0019 #9): recording a
 * debt opposite to an existing open one settles them against each other via a linked pair
 * of netting payments, and [counterDebtId] points at the opposing debt this one offsets.
 * Manual repayments have [isNetting] = false and [counterDebtId] = null.
 *
 * A *settlement* payment (ADR-0019 #14) records real money movement. The payor (borrower)
 * settles from [payorAccountId], with [payorTxnId] linking the EXPENSE leg they recorded.
 * The receiver (lender) optionally adds the matching INCOME to their own account later via
 * an inline affordance, stamping [receiverTxnId]; until then the payment is payor-only and
 * still valid (graceful degradation). All three are null for manual and netting payments.
 */
data class DebtPayment(
    val id: String,
    val debtId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    val isNetting: Boolean = false,
    val counterDebtId: String? = null,
    val payorAccountId: String? = null,
    val payorTxnId: String? = null,
    val receiverTxnId: String? = null,
)
