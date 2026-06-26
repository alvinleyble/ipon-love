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
    val attachmentUrl: String? = null,
    /** Local path of a receipt image pending upload; null once the uploader has stamped [attachmentUrl]. */
    val attachmentLocalPath: String? = null,
    /**
     * True for the ledger legs of a partner-debt settlement (ADR-0019 #14). Settlement legs
     * move real money — so [com.iponlove.app.feature.transactions.domain.usecase.AccountBalanceCalculator]
     * counts them — but they are not spending/income, so Analysis (donut / expense-flow /
     * calendar) excludes them. They also carry no category.
     */
    val isSettlement: Boolean = false,
)
