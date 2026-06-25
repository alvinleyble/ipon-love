package com.iponlove.app.feature.partnerdebt.domain.usecase

import com.iponlove.app.feature.partnerdebt.domain.model.DebtItem
import com.iponlove.app.feature.partnerdebt.domain.model.DebtNet
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPayment
import com.iponlove.app.feature.partnerdebt.domain.model.DebtPaymentItem
import com.iponlove.app.feature.partnerdebt.domain.model.NetDirection
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebt
import com.iponlove.app.feature.partnerdebt.domain.model.PartnerDebtBoard
import com.iponlove.app.feature.user.domain.model.User
import java.math.BigDecimal

/**
 * Pure derivation of the partner-debt board from the raw debt + payment streams — the
 * tier-1 money math, so it's exhaustively unit-tested and free of Android/IO.
 *
 * Each debt's remaining = `amount − Σ(its active payments)`, floored at zero (overpayment
 * never flips a debt negative). The couple's net is `Σ remaining` signed by direction:
 * debts where I borrowed push the net toward "I owe", debts where I lent pull it the other
 * way. A positive net means I owe my partner; negative means they owe me; zero is settled.
 */
object PartnerDebtCalculator {

    fun summarize(
        me: User,
        partner: User?,
        debts: List<PartnerDebt>,
        payments: List<DebtPayment>,
    ): PartnerDebtBoard {
        val paymentsByDebt = payments.groupBy { it.debtId }
        val counterpartName = partner?.displayName

        val items = debts.map { debt ->
            val debtPayments = paymentsByDebt[debt.id].orEmpty()
            val paid = debtPayments.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
            val remaining = (debt.amount - paid).max(BigDecimal.ZERO)
            val fraction = when {
                debt.amount.signum() <= 0 -> if (remaining.signum() == 0) 1f else 0f
                else -> (paid.toFloat() / debt.amount.toFloat()).coerceIn(0f, 1f)
            }
            DebtItem(
                id = debt.id,
                original = debt.amount,
                paid = paid,
                remaining = remaining,
                description = debt.description,
                iAmBorrower = debt.borrowerId == me.id,
                counterpartName = counterpartName,
                fraction = fraction,
                isSettled = remaining.signum() == 0,
                createdAt = debt.createdAt,
                payments = debtPayments
                    .sortedByDescending { it.date }
                    .map { DebtPaymentItem(it.id, it.amount, it.note, it.date) },
            )
        }

        // Net: debts I borrowed add to what I owe; debts I lent subtract.
        val net = items.fold(BigDecimal.ZERO) { acc, item ->
            if (item.iAmBorrower) acc + item.remaining else acc - item.remaining
        }
        val direction = when (net.signum()) {
            1 -> NetDirection.I_OWE
            -1 -> NetDirection.OWED_TO_ME
            else -> NetDirection.SETTLED
        }

        // Unsettled first so outstanding debts surface above paid-off history; newest first
        // within each group.
        val ordered = items.sortedWith(
            compareBy<DebtItem> { it.isSettled }.thenByDescending { it.createdAt },
        )

        return PartnerDebtBoard(
            net = DebtNet(direction = direction, amount = net.abs(), counterpartName = counterpartName),
            debts = ordered,
        )
    }
}
