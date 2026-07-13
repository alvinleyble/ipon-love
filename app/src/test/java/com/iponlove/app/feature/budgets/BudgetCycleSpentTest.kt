package com.iponlove.app.feature.budgets

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetAlertsUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.txn
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * Budget "spent"/rollover/alerts under a non-calendar start day (ADR-0046). The `yearMonth` label
 * is unchanged; only which transactions bucket into a cycle moves.
 */
class BudgetCycleSpentTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String) = Instant.parse("${date}T12:00:00Z")

    @Test
    fun spent_bucketsExpensesByCycle_day15() {
        // Cycle "2026-07" @ day 15 = Jul 15 – Aug 14. Jul 14 belongs to the *previous* cycle.
        val transactions = listOf(
            txn("before", TransactionType.EXPENSE, "100.00", categoryId = "cat-1", date = at("2026-07-14")),
            txn("start", TransactionType.EXPENSE, "200.00", categoryId = "cat-1", date = at("2026-07-15")),
            txn("mid", TransactionType.EXPENSE, "300.00", categoryId = "cat-1", date = at("2026-08-01")),
            txn("after", TransactionType.EXPENSE, "400.00", categoryId = "cat-1", date = at("2026-08-15")),
        )

        val spent = BudgetProgressCalculator.spent(
            budget = budget("b", categoryId = "cat-1", yearMonth = "2026-07"),
            transactions = transactions,
            zone = zone,
            startDay = 15,
        )

        // Only the two inside Jul 15 – Aug 14 count.
        assertThat(spent).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun effectiveLimit_rolloverChainUnderNon1StartDay() {
        val juneCycle = budget("jun", amount = "5000.00", yearMonth = "2026-06", rolloverEnabled = true)
        val julyCycle = budget("jul", amount = "4000.00", yearMonth = "2026-07", rolloverEnabled = true)
        val all = listOf(juneCycle, julyCycle)
        val transactions = listOf(
            // Inside the June cycle (Jun 15 – Jul 14) — spends 3000, leaving 2000 to carry.
            txn("j1", TransactionType.EXPENSE, "3000.00", categoryId = "cat-1", date = at("2026-07-10")),
        )

        val limit = BudgetProgressCalculator.effectiveLimit(julyCycle, all, transactions, zone, startDay = 15)

        // 4000 + (5000 - 3000 carried) = 6000. The Jul 10 expense must bucket into June, not July.
        assertThat(limit).isEqualTo(BigDecimal("6000.00"))
    }

    @Test
    fun sharedPathStaysCalendar_whilePersonalPathUsesCycle() {
        // The shared couple budget always passes startDay = 1 (calendar); personal passes the cycle.
        // A Jul 14 expense buckets into the *calendar* July but the *day-15* June cycle — proving the
        // two paths are genuinely independent (ADR-0046 §2 shared-stays-calendar pin).
        val transactions = listOf(
            txn("boundary", TransactionType.EXPENSE, "999.00", categoryId = "cat-1", date = at("2026-07-14")),
        )
        val julyBudget = budget("b", categoryId = "cat-1", yearMonth = "2026-07")

        val calendarSpent = BudgetProgressCalculator.spent(julyBudget, transactions, zone, startDay = 1)
        val cycleSpent = BudgetProgressCalculator.spent(julyBudget, transactions, zone, startDay = 15)

        assertThat(calendarSpent).isEqualTo(BigDecimal("999.00")) // shared/calendar counts it
        assertThat(cycleSpent).isEqualTo(BigDecimal.ZERO)          // personal/day-15 does not
    }

    @Test
    fun alerts_fireForTheCurrentCycle_day15() {
        // On e.g. Jul 13 @ day 15 the current cycle key is "2026-06" (Jun 15 – Jul 14).
        val current = budget("cur", categoryId = "cat-1", amount = "1000.00", yearMonth = "2026-06")
        val transactions = listOf(
            // Inside the June cycle -> 90% of 1000 -> crosses the 80% threshold.
            txn("in", TransactionType.EXPENSE, "900.00", categoryId = "cat-1", date = at("2026-07-10")),
            // In the next cycle (Jul 15+) -> must NOT count toward the "2026-06" budget.
            txn("next", TransactionType.EXPENSE, "5000.00", categoryId = "cat-1", date = at("2026-07-20")),
        )

        val alerts = CheckBudgetAlertsUseCase()(
            budgets = listOf(current),
            transactions = transactions,
            alreadyFiredKeys = emptySet(),
            currentMonth = "2026-06",
            zone = zone,
            startDay = 15,
        )

        assertThat(alerts.map { it.threshold }).containsExactly(80)
    }
}
