package com.iponlove.app.feature.transactions.domain.usecase

import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal

/**
 * Derives current account balances from `opening_balance` + the ledger (ADR-0007) — the
 * balance is never stored or synced. Each transaction posts to its account(s):
 *  - INCOME   → +amount on its account
 *  - EXPENSE  → −amount on its account
 *  - TRANSFER → −amount on the source, +amount on the destination
 *
 * Pass only non-deleted transactions (the repository already filters tombstones).
 */
object AccountBalanceCalculator {

    /** Current balance of one account. */
    fun balanceOf(
        accountId: String,
        openingBalance: BigDecimal,
        transactions: List<Transaction>,
    ): BigDecimal = balances(mapOf(accountId to openingBalance), transactions).getValue(accountId)

    /**
     * Current balances for many accounts in a single pass. Postings to accounts not in
     * [openingBalances] are ignored, so a partner's (unreplicated) account can't skew us.
     */
    fun balances(
        openingBalances: Map<String, BigDecimal>,
        transactions: List<Transaction>,
    ): Map<String, BigDecimal> {
        val result = HashMap(openingBalances)

        fun post(accountId: String?, delta: BigDecimal) {
            if (accountId == null) return
            val current = result[accountId] ?: return
            result[accountId] = current + delta
        }

        for (txn in transactions) {
            when (txn.type) {
                TransactionType.INCOME -> post(txn.accountId, txn.amount)
                TransactionType.EXPENSE -> post(txn.accountId, txn.amount.negate())
                TransactionType.TRANSFER -> {
                    post(txn.accountId, txn.amount.negate())
                    post(txn.toAccountId, txn.amount)
                }
            }
        }
        return result
    }
}
