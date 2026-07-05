package com.iponlove.app.feature.budgets.domain.model

import java.math.BigDecimal

/**
 * A monthly spending limit. Pure domain model — no `user_id`/`couple_id` (the repository
 * owns ownership; V1 budgets are all personal) and no sync columns.
 *
 * [categoryId] null means an **overall** monthly budget (all expenses count); otherwise
 * the limit applies to that one category. [yearMonth] is `YearMonth.toString()` form,
 * e.g. `2026-06`.
 *
 * [rolloverEnabled] opts this budget into carrying last month's leftover (or deficit)
 * forward into [amount] — symmetric, no floor, computed at read time by
 * [com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator] (ADR-0036).
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val amount: BigDecimal,
    val yearMonth: String,
    val rolloverEnabled: Boolean = false,
)
