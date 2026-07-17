package com.iponlove.app.feature.analysis

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.analysis.domain.usecase.ProjectedNetCalculator
import com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ProjectedNetCalculatorTest {

    private val monthEnd = LocalDate.of(2026, 7, 31)

    @Test
    fun projectsBothSides_actualNetPlusUpcomingIncomeMinusUpcomingBills() {
        // Actual net -5,000 so far; salary +20,000 (Jul 20) and rent -8,000 (Jul 25) still to come.
        val projection = ProjectedNetCalculator.project(
            actualNet = BigDecimal("-5000"),
            upcoming = listOf(
                occ(TransactionType.INCOME, "20000", LocalDate.of(2026, 7, 20)),
                occ(TransactionType.EXPENSE, "8000", LocalDate.of(2026, 7, 25)),
            ),
            monthEndInclusive = monthEnd,
        )

        assertThat(projection.upcomingIncome.compareTo(BigDecimal("20000"))).isEqualTo(0)
        assertThat(projection.upcomingExpense.compareTo(BigDecimal("8000"))).isEqualTo(0)
        // -5000 + 20000 - 8000 = 7000
        assertThat(projection.projectedNet.compareTo(BigDecimal("7000"))).isEqualTo(0)
        assertThat(projection.hasSchedule).isTrue()
    }

    @Test
    fun occurrencesAfterMonthEnd_areExcluded() {
        val projection = ProjectedNetCalculator.project(
            actualNet = BigDecimal.ZERO,
            upcoming = listOf(
                occ(TransactionType.INCOME, "1000", LocalDate.of(2026, 7, 31)), // on the boundary → counted
                occ(TransactionType.INCOME, "9999", LocalDate.of(2026, 8, 1)),  // past month → dropped
            ),
            monthEndInclusive = monthEnd,
        )

        assertThat(projection.upcomingIncome.compareTo(BigDecimal("1000"))).isEqualTo(0)
        assertThat(projection.projectedNet.compareTo(BigDecimal("1000"))).isEqualTo(0)
    }

    @Test
    fun noSchedule_hasScheduleFalse_projectedEqualsActual() {
        val projection = ProjectedNetCalculator.project(
            actualNet = BigDecimal("1234.56"),
            upcoming = emptyList(),
            monthEndInclusive = monthEnd,
        )

        assertThat(projection.hasSchedule).isFalse()
        assertThat(projection.projectedNet.compareTo(BigDecimal("1234.56"))).isEqualTo(0)
    }

    @Test
    fun incomeOnly_hasScheduleTrue() {
        val projection = ProjectedNetCalculator.project(
            actualNet = BigDecimal.ZERO,
            upcoming = listOf(occ(TransactionType.INCOME, "500", LocalDate.of(2026, 7, 20))),
            monthEndInclusive = monthEnd,
        )
        assertThat(projection.hasSchedule).isTrue()
        assertThat(projection.upcomingExpense.compareTo(BigDecimal.ZERO)).isEqualTo(0)
    }

    private fun occ(type: TransactionType, amount: String, date: LocalDate) = UpcomingOccurrence(
        ruleId = "r",
        date = date,
        amount = BigDecimal(amount),
        type = type,
        categoryId = "cat",
        categoryName = "Cat",
        note = null,
    )
}
