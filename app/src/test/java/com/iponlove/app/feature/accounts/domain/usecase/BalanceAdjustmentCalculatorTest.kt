package com.iponlove.app.feature.accounts.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import org.junit.Test
import java.math.BigDecimal

class BalanceAdjustmentCalculatorTest {

    @Test
    fun targetAboveCurrent_isIncomeForTheDifference() {
        val result = BalanceAdjustmentCalculator.delta(BigDecimal("9500.00"), BigDecimal("10000.00"))
        assertThat(result).isEqualTo(
            BalanceAdjustmentCalculator.Result.Adjust(TransactionType.INCOME, BigDecimal("500.00")),
        )
    }

    @Test
    fun targetBelowCurrent_isExpenseForTheDifference() {
        val result = BalanceAdjustmentCalculator.delta(BigDecimal("10000.00"), BigDecimal("9500.00"))
        assertThat(result).isEqualTo(
            BalanceAdjustmentCalculator.Result.Adjust(TransactionType.EXPENSE, BigDecimal("500.00")),
        )
    }

    @Test
    fun targetEqualsCurrent_isNoOp() {
        val result = BalanceAdjustmentCalculator.delta(BigDecimal("500.00"), BigDecimal("500.00"))
        assertThat(result).isEqualTo(BalanceAdjustmentCalculator.Result.NoOp)
    }

    @Test
    fun negativeTarget_onACardOverdraft_isExpenseForTheDifference() {
        // A card that's -₱200 in debt, corrected to -₱1,000 (deeper debt): a ₱800 expense.
        val result = BalanceAdjustmentCalculator.delta(BigDecimal("-200.00"), BigDecimal("-1000.00"))
        assertThat(result).isEqualTo(
            BalanceAdjustmentCalculator.Result.Adjust(TransactionType.EXPENSE, BigDecimal("800.00")),
        )
    }

    @Test
    fun signCrossing_negativeToPositive_isOneIncomeRow() {
        // -₱200 corrected to +₱300 must be one ₱500 income row, not two.
        val result = BalanceAdjustmentCalculator.delta(BigDecimal("-200.00"), BigDecimal("300.00"))
        assertThat(result).isEqualTo(
            BalanceAdjustmentCalculator.Result.Adjust(TransactionType.INCOME, BigDecimal("500.00")),
        )
    }
}
