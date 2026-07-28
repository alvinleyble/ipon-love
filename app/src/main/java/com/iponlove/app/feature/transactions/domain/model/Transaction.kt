package com.iponlove.app.feature.transactions.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * A single ledger entry. Pure domain model — no `user_id`, no sync columns, and no
 * `recurring_rule_id` (that's preserved by the repository across edits, never surfaced
 * to the UI yet).
 *
 * Field validity is type-dependent (see TransactionValidator):
 *  - INCOME / EXPENSE move [amount] on [accountId] and require a [categoryId].
 *  - TRANSFER moves [amount] from [accountId] to [toAccountId] and carries no category.
 *
 * [amount] is always a positive magnitude; direction is conveyed by [type] (ADR-0007
 * derives balances from this, never from a stored signed balance).
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val date: Instant,
    val isPrivate: Boolean = false,
    // Receipts are a separate entity now (TransactionImage, up to 3), managed by the editor's
    // image list + SaveTransactionImagesUseCase — no longer a field on the transaction.
    /**
     * True for the ledger legs of a partner-debt settlement (ADR-0019 #14). Settlement legs
     * move real money — so [com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator]
     * counts them — but they are not spending/income, so Analysis (donut / expense-flow /
     * calendar) excludes them. They also carry no category.
     */
    val isSettlement: Boolean = false,
    /**
     * True for a manual balance correction row (ADR-0057): the user typed a target balance and
     * this row carries the signed delta. It moves real money — so
     * [com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator] counts it —
     * but like [isSettlement] it is not spending/income, so Analysis and Budgets exclude it, and
     * it carries no category. Unlike [isSettlement], it stays out of Combined *spend totals* while
     * still appearing in the Combined *feed* (a shared-account correction moves the partner's
     * balance too, so hiding it entirely would leave them with an unexplained balance jump).
     */
    val isAdjustment: Boolean = false,
    /**
     * Set only on a TRANSFER row with a non-zero fee (ADR-0031): the id of the linked EXPENSE
     * row that carries the fee (auto-categorized under "Transfer fees" so it's real, groupable
     * spend in Analysis — unlike [isSettlement], which is deliberately excluded there). Null
     * means no fee. Maintained by [com.iponlove.app.feature.transactions.domain.usecase.SaveTransferUseCase],
     * which cascades: editing the fee replaces the linked row, deleting the transfer
     * ([com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase]) retires it too.
     */
    val transferFeeTransactionId: String? = null,
)
