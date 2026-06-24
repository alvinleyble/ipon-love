package com.iponlove.app.feature.recurring.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A rule that materializes transactions on a schedule (e.g. "₱20,000 salary, monthly").
 * Pure domain model — no `user_id` and no sync columns (the repository owns those).
 *
 * V1 recurring covers INCOME and EXPENSE only (no transfers): the generated transaction's
 * type is derived from the template category's [com.iponlove.app.feature.categories.domain.model.CategoryType]
 * at materialization, so the rule itself carries no type field — matching the schema's
 * `template` jsonb (amount, category_id, account_id, note).
 *
 *  - [nextDate]  the next occurrence date still to materialize; advances as occurrences
 *                are generated (ADR-style derived cursor). A local calendar date — time of
 *                day is irrelevant to a recurrence.
 *  - [endDate]   inclusive last possible occurrence; null = repeats forever.
 *  - [interval]  every N units of [frequency] (>= 1), e.g. WEEKLY interval 2 = fortnightly.
 */
data class RecurringRule(
    val id: String,
    val frequency: RecurringFrequency,
    val interval: Int,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val template: RecurringTemplate,
)

/** The fixed details every generated occurrence copies. [categoryId] is required (V1). */
data class RecurringTemplate(
    val amount: BigDecimal,
    val accountId: String,
    val categoryId: String,
    val note: String?,
)
