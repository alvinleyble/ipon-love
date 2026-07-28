package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Pure delta math behind "adjust balance" (ADR-0057): the user types a target balance, this
 * turns `target − current` into the signed ledger row that lands the derived balance exactly
 * on that figure. [amount] is always a positive magnitude — direction is conveyed by [type],
 * matching every other [com.iponlove.app.feature.transactions.domain.model.Transaction].
 */
object BalanceAdjustmentCalculator {

    /** The result of comparing a target balance against the current one. */
    sealed interface Result {
        /** Target equals current — nothing to write. */
        data object NoOp : Result

        /** The correction row to create: [type] and its positive [amount]. */
        data class Adjust(val type: TransactionType, val amount: BigDecimal) : Result
    }

    fun delta(current: BigDecimal, target: BigDecimal): Result {
        val diff = target - current
        return when {
            diff.signum() == 0 -> Result.NoOp
            diff.signum() > 0 -> Result.Adjust(TransactionType.INCOME, diff)
            else -> Result.Adjust(TransactionType.EXPENSE, diff.negate())
        }
    }
}
