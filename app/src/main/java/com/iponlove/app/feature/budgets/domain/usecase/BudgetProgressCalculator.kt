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
 * month, and (for a category budget) within its category. An overall budget
 * (`categoryId == null`) counts every expense that month.
 *
 * The month a transaction falls in is timezone-dependent, so [zone] is explicit for
 * determinism in tests (V1 is PH-only, so the app passes the system zone).
 */
object BudgetProgressCalculator {

    fun spent(
        budget: Budget,
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BigDecimal =
        transactions.asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .filter { budget.categoryId == null || it.categoryId == budget.categoryId }
            .filter { yearMonthKey(it.date, zone) == budget.yearMonth }
            .fold(BigDecimal.ZERO) { running, txn -> running + txn.amount }
}

/** `YearMonth` key for an instant in [zone], matching [Budget.yearMonth] form ("2026-06"). */
fun yearMonthKey(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    YearMonth.from(instant.atZone(zone)).toString()
