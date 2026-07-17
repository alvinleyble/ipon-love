package com.iponlove.app.feature.budgets.domain.model

import java.math.BigDecimal

/**
 * A monthly spending limit. Pure domain model — the repository owns the exact ownership columns
 * (`user_id`/`couple_id`) and all sync columns; [isShared] is the one ownership fact the UI needs,
 * projected from `couple_id != null` (Item 35 / ADR-0047).
 *
 * [categoryId] null means an **overall** monthly budget (all expenses count); otherwise
 * the limit applies to that one category. [yearMonth] is `YearMonth.toString()` form,
 * e.g. `2026-06`.
 *
 * [isShared] true ⇒ a couple-owned budget: both partners read/write it, and its spend counts
 * **both** members' non-private transactions (ADR-0047). Scope is set at creation and immutable
 * after (a shared budget is created shared, never toggled from a personal row — deliberately
 * unlike the Accounts/Categories share toggle, since budgets are per-month; ADR-0047).
 *
 * [rolloverEnabled] opts this budget into carrying last month's leftover (or deficit)
 * forward into [amount] — symmetric, no floor, computed at read time by
 * [com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator] (ADR-0036);
 * a shared budget rolls over with the same per-row toggle, chained within its own scope.
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val amount: BigDecimal,
    val yearMonth: String,
    val rolloverEnabled: Boolean = false,
    val isShared: Boolean = false,
)
