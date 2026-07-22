package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Derives how much a budget has been spent, from the transaction ledger — only EXPENSE
 * transactions count (income and transfers never consume a budget), within the budget's
 * cycle, and (for a category budget) within its category. An overall budget
 * (`categoryId == null`) counts every expense in the cycle.
 *
 * Partner-debt **settlement** legs (`isSettlement`, ADR-0019 #14) are excluded — a debt
 * repayment isn't spending, matching every Analysis calculator. Without this, a settlement
 * leg (which carries `categoryId = null`) would count against an overall budget.
 *
 * The cycle a transaction falls in is timezone-dependent, so [zone] is explicit for
 * determinism in tests (V1 is PH-only, so the app passes the system zone).
 *
 * [startDay] is the personal "budget month starts on day N" setting (ADR-0046). The default `1`
 * is calendar months, byte-identical to the pre-setting behaviour; a non-1 value re-windows the
 * cycle to a payday-aligned range via [BudgetCycle]. The `yearMonth` label is unchanged — only
 * which transactions bucket into it.
 */
object BudgetProgressCalculator {

    fun spent(
        budget: Budget,
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
        startDay: Int = 1,
    ): BigDecimal =
        transactions.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .filter { !it.isSettlement }
            .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
            .filter { BudgetCycle.cycleKey(it.date, startDay, zone) == budget.yearMonth }
            .fold(BigDecimal.ZERO) { running, txn -> running + txn.amount }

    /**
     * The rollover-adjusted limit for [budget]: its own [Budget.amount] plus, when
     * [Budget.rolloverEnabled], the previous cycle's leftover (positive) or deficit
     * (negative — never floored at ₱0, ADR-0036) carried in.
     *
     * Chains backward through consecutive `yearMonth` rows in [sameCategoryBudgets] (which
     * must already be filtered to [budget]'s category — including the `null` "overall"
     * category). A cycle with no row at all breaks the chain: the carry resets to ₱0
     * rather than skipping through the gap to reach further back. The label chain is
     * independent of [startDay] — consecutive cycles are always consecutive `YearMonth`s;
     * [startDay] only affects the per-cycle [spent].
     */
    fun effectiveLimit(
        budget: Budget,
        sameCategoryBudgets: List<Budget>,
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
        startDay: Int = 1,
    ): BigDecimal {
        if (!budget.rolloverEnabled) return budget.amount
        val previousMonth = YearMonth.parse(budget.yearMonth).minusMonths(1).toString()
        val previous = sameCategoryBudgets.firstOrNull { it.yearMonth == previousMonth }
            ?: return budget.amount
        val previousLimit = effectiveLimit(previous, sameCategoryBudgets, transactions, zone, startDay)
        val previousSpent = spent(previous, transactions, zone, startDay)
        return budget.amount + (previousLimit - previousSpent)
    }
}

/** `YearMonth` key for an instant in [zone], matching [Budget.yearMonth] form ("2026-06"). */
fun yearMonthKey(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    YearMonth.from(instant.atZone(zone)).toString()
