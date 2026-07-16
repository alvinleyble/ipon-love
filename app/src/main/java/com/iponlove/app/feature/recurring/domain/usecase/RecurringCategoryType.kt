package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.transactions.domain.model.TransactionType

/**
 * The generated transaction's type is derived from the template category's [CategoryType]
 * (V1 recurring covers INCOME and EXPENSE only — no transfers). Shared by every recurring
 * consumer that turns a rule into a transaction — auto-post materialization, confirm-on-arrival,
 * and the recurring-list rendering — so the mapping lives in exactly one place.
 */
internal fun CategoryType.toTransactionType(): TransactionType = when (this) {
    CategoryType.INCOME -> TransactionType.INCOME
    CategoryType.EXPENSE -> TransactionType.EXPENSE
}
