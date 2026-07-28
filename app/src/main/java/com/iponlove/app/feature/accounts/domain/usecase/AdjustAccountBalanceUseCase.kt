package com.iponlove.app.feature.accounts.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Corrects an account's balance to a user-typed target (ADR-0057) by creating one marked,
 * dated ledger row for the signed delta — never touching `opening_balance` (LWW-unsafe once an
 * account has real activity, ADR-0007's own safety argument). A no-op delta writes nothing.
 *
 * [isShared] decides [Transaction.isPrivate]: `false` on a shared account (ADR-0018 requires it
 * — the partner needs to see why the joint balance moved), `true` on a personal one.
 *
 * Formats the auto-note with a plain local [DecimalFormat] rather than
 * [com.iponlove.app.core.ui.formatPhp] — that helper lives in `core/ui` alongside Compose, and
 * the domain layer stays Android-import-free (CLAUDE.md).
 */
class AdjustAccountBalanceUseCase @Inject constructor(
    private val upsertTransaction: UpsertTransactionUseCase,
) {
    suspend operator fun invoke(
        accountId: String,
        isShared: Boolean,
        current: BigDecimal,
        target: BigDecimal,
        date: Instant = Instant.now(),
    ) {
        val result = BalanceAdjustmentCalculator.delta(current, target)
        if (result !is BalanceAdjustmentCalculator.Result.Adjust) return

        upsertTransaction(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = result.type,
                amount = result.amount,
                accountId = accountId,
                categoryId = null,
                note = "${format(current)} → ${format(target)}",
                date = date,
                isPrivate = !isShared,
                isAdjustment = true,
            ),
        )
    }

    private companion object {
        val amountFormat = DecimalFormat("#,##0.00")
        fun format(amount: BigDecimal) = "₱${amountFormat.format(amount)}"
    }
}
