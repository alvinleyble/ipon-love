package com.iponlove.app.feature.recurring.domain.model

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A due-but-not-yet-recorded occurrence of a confirm-on-arrival recurring rule (Item 37).
 *
 * Purely **derived** — nothing is stored until the user confirms. [ObservePendingConfirmationsUseCase]
 * computes these live from each confirm rule's schedule (`nextDate..today`, bounded by a floor)
 * minus the occurrences already materialized, so balance / budget / Analysis exclude pending
 * money "for free" (no `status` column, no money-query change).
 *
 *  - [occurrenceId] the deterministic `ruleId:date` transaction id this occurrence will
 *                   materialize into on confirm — stable across devices, so a confirm is
 *                   idempotent and a stable list key.
 *  - [amount]       the rule's template amount, i.e. the *default* the confirm sheet pre-fills;
 *                   the user may tweak it for this one occurrence before confirming.
 *  - [type]/[categoryName] resolved for display + the signed amount colour.
 */
data class PendingConfirmation(
    val ruleId: String,
    val occurrenceId: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: String,
    val categoryName: String,
    val accountId: String,
    val note: String?,
)
