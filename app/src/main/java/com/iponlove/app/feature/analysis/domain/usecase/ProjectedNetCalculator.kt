package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The schedule-based month-end forecast for the Analysis Summary (Item 37, Slice 2 — premium).
 *
 * Pure add-on to the actual, already-derived Net: it never touches the ledger or any stored
 * figure (ADR-0007 stays intact). Given this in-progress month's **actual** Net and the derived
 * [UpcomingOccurrence]s remaining in the month, it projects both sides:
 *
 *   `projectedNet = actualNet + (scheduled income left this month) − (scheduled bills left this month)`
 *
 * It cannot see unplanned ad-hoc spending — the forecast assumes none, which is why the UI labels
 * it a forecast rather than a promise (Q9/Q10). [Projection.hasSchedule] is false when nothing is
 * scheduled for the rest of the month; the caller then shows no forecast (and falls back to the
 * "Last month income" context stat) rather than a projection identical to actual Net.
 */
object ProjectedNetCalculator {

    data class Projection(
        val projectedNet: BigDecimal,
        val upcomingIncome: BigDecimal,
        val upcomingExpense: BigDecimal,
    ) {
        /** Whether anything at all is scheduled for the rest of the month — the signal that a
         *  forecast is worth showing (otherwise it just equals actual Net). */
        val hasSchedule: Boolean
            get() = upcomingIncome.signum() > 0 || upcomingExpense.signum() > 0
    }

    /**
     * @param actualNet this month's actual Net so far (income − expense from the ledger).
     * @param upcoming derived future occurrences (already forward-only); only those on/before
     *   [monthEndInclusive] are counted, so a window that overruns the month is trimmed here.
     */
    fun project(
        actualNet: BigDecimal,
        upcoming: List<UpcomingOccurrence>,
        monthEndInclusive: LocalDate,
    ): Projection {
        var income = BigDecimal.ZERO
        var expense = BigDecimal.ZERO
        for (occ in upcoming) {
            if (occ.date.isAfter(monthEndInclusive)) continue
            when (occ.type) {
                TransactionType.INCOME -> income += occ.amount
                TransactionType.EXPENSE -> expense += occ.amount
                TransactionType.TRANSFER -> Unit // recurring is income/expense only (V1)
            }
        }
        return Projection(
            projectedNet = actualNet + income - expense,
            upcomingIncome = income,
            upcomingExpense = expense,
        )
    }
}
