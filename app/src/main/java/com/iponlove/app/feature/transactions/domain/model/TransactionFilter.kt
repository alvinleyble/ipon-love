package com.iponlove.app.feature.transactions.domain.model

import java.math.BigDecimal

/**
 * A client-side lens over the Records ledger (v1.7.0 Item 7). Four independent dimensions —
 * categories, accounts, transaction types, and an absolute amount range — combined **AND across
 * dimensions, OR within one**; an empty selection on a dimension leaves it unconstrained.
 *
 * Applied *upstream* of [com.iponlove.app.feature.transactions.presentation.TransactionsViewModel]'s
 * main combine (which is already at its 5-flow ceiling), so [apply] is a pure list→list function
 * over the raw domain models already resident in memory — no query, no join against name maps.
 *
 * Semantics that aren't obvious (all deliberate, see the Item 7 grill):
 *  - Transfers and settlements carry `categoryId = null`, so they can never match a **category**
 *    filter — correct, they have no category.
 *  - An **account** filter matches a transfer on **either** [Transaction.accountId] or
 *    [Transaction.toAccountId] (filtering to "Credit Card" must surface a Bank→Card paydown), and
 *    matches a settlement on its [Transaction.accountId].
 *  - The amount range is absolute (the stored-positive [Transaction.amount], sign is derived at
 *    display) and inclusive at both bounds; each bound is independently optional (`null` = unbounded).
 */
data class TransactionFilter(
    val categoryIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val types: Set<TransactionType> = emptySet(),
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
) {
    /** True when at least one dimension constrains the result — drives the title-row active dot. */
    val isActive: Boolean
        get() = categoryIds.isNotEmpty() || accountIds.isNotEmpty() || types.isNotEmpty() ||
            minAmount != null || maxAmount != null

    /**
     * False only when both bounds are set and inverted (min > max) — the one invalid state, which
     * disables the sheet's Apply button. A single bound (or none) is always valid.
     */
    val isValid: Boolean
        get() = minAmount == null || maxAmount == null || minAmount <= maxAmount

    /** Pure list→list filter. An inactive filter returns the input unchanged (identity fast-path). */
    fun apply(transactions: List<Transaction>): List<Transaction> =
        if (!isActive) transactions else transactions.filter { matches(it) }

    private fun matches(t: Transaction): Boolean {
        if (categoryIds.isNotEmpty() && (t.categoryId == null || t.categoryId !in categoryIds)) {
            return false
        }
        if (accountIds.isNotEmpty()) {
            val touchesAccount = t.accountId in accountIds ||
                (t.toAccountId != null && t.toAccountId in accountIds)
            if (!touchesAccount) return false
        }
        if (types.isNotEmpty() && t.type !in types) return false
        minAmount?.let { if (t.amount < it) return false }
        maxAmount?.let { if (t.amount > it) return false }
        return true
    }

    companion object {
        val NONE = TransactionFilter()

        /**
         * Parses a user-typed amount bound to [BigDecimal] per the money convention (never
         * [Double]). Blank or unparseable input → `null` = unbounded on that side, never zero,
         * never a throw.
         */
        fun parseBound(raw: String): BigDecimal? =
            raw.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
    }
}
