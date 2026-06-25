package com.iponlove.app.feature.partnerdebt.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * One (partial or full) repayment against a [PartnerDebt]. [debtId] ties it to its debt;
 * the sum of a debt's active payments is subtracted from the debt amount to derive the
 * remaining balance. [date] is the user-meaningful payment date (defaults to now in V1).
 */
data class DebtPayment(
    val id: String,
    val debtId: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
)
