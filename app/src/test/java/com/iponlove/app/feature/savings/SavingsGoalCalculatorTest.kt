package com.iponlove.app.feature.savings

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.savings.domain.usecase.SavingsGoalCalculator
import org.junit.Test
import java.math.BigDecimal

class SavingsGoalCalculatorTest {

    @Test
    fun savedAmount_sumsBothAuthors_nonDeletedOnly() {
        // The repository only ever passes non-deleted rows; the sum spans BOTH partners.
        val contributions = listOf(
            goalContribution("c1", goalId = "g-1", amount = BigDecimal("500.00"), byUserId = "me"),
            goalContribution("c2", goalId = "g-1", amount = BigDecimal("300.00"), byUserId = "partner"),
            goalContribution("c3", goalId = "other", amount = BigDecimal("999.00")),
        )
        assertThat(SavingsGoalCalculator.savedAmount("g-1", contributions))
            .isEqualTo(BigDecimal("800.00"))
    }

    @Test
    fun concurrentContributions_distinctIds_neverClobber() {
        // The LWW-safety property: two independent contribution rows (as two offline partners
        // would create) both survive and sum — no shared counter to overwrite.
        val a = goalContribution("id-a", goalId = "g-1", amount = BigDecimal("1000.00"), byUserId = "me")
        val b = goalContribution("id-b", goalId = "g-1", amount = BigDecimal("1000.00"), byUserId = "partner")
        assertThat(SavingsGoalCalculator.savedAmount("g-1", listOf(a, b)))
            .isEqualTo(BigDecimal("2000.00"))
    }

    @Test
    fun withProgress_derivesReachedAndClampedProgress() {
        val goals = listOf(
            savingsGoal("g-1", targetAmount = BigDecimal("1000.00")),
            savingsGoal("g-2", targetAmount = BigDecimal("1000.00")),
        )
        val contributions = listOf(
            goalContribution("c1", goalId = "g-1", amount = BigDecimal("400.00")),
            goalContribution("c2", goalId = "g-2", amount = BigDecimal("1500.00")), // over target
        )
        val result = SavingsGoalCalculator.withProgress(goals, contributions).associateBy { it.goal.id }

        assertThat(result.getValue("g-1").savedAmount).isEqualTo(BigDecimal("400.00"))
        assertThat(result.getValue("g-1").reached).isFalse()
        assertThat(result.getValue("g-1").progress).isWithin(1e-4f).of(0.4f)

        assertThat(result.getValue("g-2").reached).isTrue()          // saved >= target
        assertThat(result.getValue("g-2").progress).isEqualTo(1f)    // clamped to 1
    }

    @Test
    fun withProgress_reachedExactlyAtTarget() {
        val goals = listOf(savingsGoal("g-1", targetAmount = BigDecimal("1000.00")))
        val contributions = listOf(goalContribution("c1", amount = BigDecimal("1000.00")))
        assertThat(SavingsGoalCalculator.withProgress(goals, contributions).single().reached).isTrue()
    }

    @Test
    fun withProgress_ignoresOrphanContributions() {
        // An own-contribution to an ex-partner's goal after unpair (goal absent) must not leak.
        val goals = listOf(savingsGoal("g-1", targetAmount = BigDecimal("1000.00")))
        val contributions = listOf(
            goalContribution("c1", goalId = "g-1", amount = BigDecimal("200.00")),
            goalContribution("orphan", goalId = "gone", amount = BigDecimal("9999.00")),
        )
        val single = SavingsGoalCalculator.withProgress(goals, contributions).single()
        assertThat(single.savedAmount).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun withProgress_goalWithNoContributions_isZero() {
        val goals = listOf(savingsGoal("g-1", targetAmount = BigDecimal("1000.00")))
        val single = SavingsGoalCalculator.withProgress(goals, emptyList()).single()
        assertThat(single.savedAmount).isEqualTo(BigDecimal.ZERO)
        assertThat(single.reached).isFalse()
        assertThat(single.progress).isEqualTo(0f)
    }
}
