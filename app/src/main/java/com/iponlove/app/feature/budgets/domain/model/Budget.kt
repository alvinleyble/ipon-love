package com.iponlove.app.feature.budgets.domain.model

import java.math.BigDecimal

/**
 * A monthly spending limit. Pure domain model — no `user_id`/`couple_id` (the repository
 * owns ownership; V1 budgets are all personal) and no sync columns.
 *
 * [categoryId] null means an **overall** monthly budget (all expenses count); otherwise
 * the limit applies to that one category. [yearMonth] is `YearMonth.toString()` form,
 * e.g. `2026-06`.
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val amount: BigDecimal,
    val yearMonth: String,
)
