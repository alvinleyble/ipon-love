package com.iponlove.app.feature.export.domain

import com.iponlove.app.feature.transactions.domain.model.TransactionFilter

/**
 * The export sheet's "What to include" row label (v1.7.0 Item 6, re-grilled 2026-07-24, decision
 * 4): *"All transactions"* unfiltered; the single category's name when that's the *only*
 * constraint; *"Filtered"* for anything more complex. Pure — no dependency on the filename (which
 * no longer carries a scope word, a separate decision).
 */
object ExportScopeLabel {
    fun of(filter: TransactionFilter, categoryNames: Map<String, String>): String = when {
        !filter.isActive -> "All transactions"
        filter.categoryIds.size == 1 &&
            filter.accountIds.isEmpty() &&
            filter.types.isEmpty() &&
            filter.minAmount == null &&
            filter.maxAmount == null ->
            categoryNames[filter.categoryIds.single()] ?: "Filtered"
        else -> "Filtered"
    }
}
