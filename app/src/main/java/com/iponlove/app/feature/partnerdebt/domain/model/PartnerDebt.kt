package com.iponlove.app.feature.partnerdebt.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * An informal IOU between the two partners: [borrowerId] owes [lenderId] [amount]. Pure
 * domain model — no `couple_id`/sync columns (the repository owns ownership, ADR-0011);
 * debts only ever exist within a couple. [description] is an optional free-text reason
 * ("dinner", "rent share"). Remaining balance is **derived** from [DebtPayment]s, never
 * stored — see [com.iponlove.app.feature.partnerdebt.domain.usecase.PartnerDebtCalculator].
 */
data class PartnerDebt(
    val id: String,
    val borrowerId: String,
    val lenderId: String,
    val amount: BigDecimal,
    val description: String?,
    val createdAt: Instant,
)
